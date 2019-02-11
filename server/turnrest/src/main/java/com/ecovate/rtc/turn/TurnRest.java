package com.ecovate.rtc.turn;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.Key;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestMethod;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.server.http.HTTPServer;
import org.threadly.litesockets.server.http.HTTPServer.BodyFuture;
import org.threadly.litesockets.server.http.HTTPServer.ResponseWriter;
import org.threadly.util.Clock;
import org.threadly.util.StringUtils;

import com.codahale.metrics.health.HealthCheck;
import com.ecovate.rtc.turn.processors.DefaultHTTPHandler;
import com.ecovate.rtc.turn.processors.MonitorHTTPHandler;
import com.ecovate.rtc.turn.processors.PingHTTPHandler;
import com.ecovate.rtc.turn.processors.TurnRestHTTPHandler;
import com.ecovate.rtc.turn.stats.MemoryMetrics;
import com.ecovate.rtc.turn.stats.NetworkMetrics;

import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class TurnRest {
  private static final int CONFIG_FILE_SCAN_TIMER_MS = 1000*30; //30 seconds 
  public static final String TURN_REST = "turn_rest_";

  private final Logger log = LoggerFactory.getLogger(TurnRest.class);
  private final PriorityScheduler PS = new PriorityScheduler(10);
  private final ThreadedSocketExecuter tse = new ThreadedSocketExecuter(PS, 100, 1);
  private final List<HTTPHandler> handlers;
  private final Runnable cfr = ()->loadConfigFile();
  private final MonitorHTTPHandler monitorHandler;
  private final PingHTTPHandler pingHandler = new PingHTTPHandler();
  private final DefaultHTTPHandler defaultHandler = new DefaultHTTPHandler(); 
  private final InetSocketAddress publicAddress;
  private final InetSocketAddress adminAddress;
  private final HTTPServer httpServer;
  private final HTTPServer adminHttpServer;
  private final Gauge uptimeCounter = new Gauge.Builder()
      .help("Uptime in ms").name(TURN_REST+"uptime_ms").register();
  private final Counter totalHTTPCounter = new Counter.Builder()
      .help("Total HTTP Requests").name(TURN_REST+"http_requests").register();
  private final Counter unhandledHTTPCounter = new Counter.Builder()
      .help("Unhandled HTTP requests").name(TURN_REST+"http_unhandled").register();
  private final Histogram httpRequestLatency = Histogram.build()
      .name(TURN_REST+"http_requests_latency_seconds").help("HTTP Request latency in seconds.").register();
  private final File cf;
  private final SimpleResponse optionsResponse;
  private volatile long cf_modTime = -1;
  private volatile TurnRestConfig config = null;

  public TurnRest(InetSocketAddress publicAddress, InetSocketAddress adminAddress, File cf) throws IOException {
    this.tse.start();
    this.cf = cf;
    this.publicAddress = publicAddress;
    this.adminAddress = adminAddress;
    
    optionsResponse = new SimpleResponse(Utils.getOKResponse());
    
    PS.scheduleAtFixedRate(()->uptimeCounter.set(Clock.lastKnownForwardProgressingMillis()), 500, 500);
    MemoryMetrics.registerMemoryMetrics();
    PS.scheduleAtFixedRate(()->MemoryMetrics.updateMemoryStats(), 500, 500);
    NetworkMetrics.registerNetworkMetrics(tse);
    PS.scheduleAtFixedRate(()->NetworkMetrics.updateNetworkMetrics(), 500, 500);

    this.httpServer = new HTTPServer(tse, this.publicAddress.getAddress().getHostAddress(), this.publicAddress.getPort());
    this.httpServer.setHandler((x,y,z)->handler(x,y,z));
    loadConfigFile();
    configureHealthChecks();
    monitorHandler = new MonitorHTTPHandler();
    ArrayList<HTTPHandler> hl = new ArrayList<>();
    hl.add(pingHandler);
    if(publicAddress.equals(adminAddress)) {
      hl.add(monitorHandler);
      adminHttpServer = httpServer;
    } else {
      this.adminHttpServer = new HTTPServer(tse, this.adminAddress.getAddress().getHostAddress(), this.adminAddress.getPort());
      this.adminHttpServer.setHandler((x,y,z)->adminHandler(x,y,z));  
    }
    hl.add(new TurnRestHTTPHandler());
    hl.add(defaultHandler);
    handlers = Collections.unmodifiableList(hl);
    this.httpServer.start();
    adminHttpServer.startIfNotStarted();

    PS.scheduleAtFixedRate(cfr, CONFIG_FILE_SCAN_TIMER_MS, CONFIG_FILE_SCAN_TIMER_MS);
    log.info("Server Started.");
  }

  private void configureHealthChecks() {
    Utils.getHealthCheckRegistry().register(ClientConnectionsCheck.class.getSimpleName(), new ClientConnectionsCheck());
  }

  private void loadConfigFile() {
    if(cf_modTime <0 || cf.lastModified() != cf_modTime) {
      log.info("loading new Config file!");
      try {
        TurnRestConfig lc  = TurnRestConfig.openConfigFile(cf);
        Utils.processHTTPDefaults(lc);
        Utils.setJWKProviders(new HashSet<String>(lc.getJwkURLs()));
        config = lc;
        log.info("loadded new config:\n{}", config.toString());
      } catch (IOException e) {
        log.error("Error parsing configfile", e);
      } finally {
        cf_modTime = cf.lastModified();
      }
    }
  }
  
  private void adminHandler(HTTPRequest httpRequest, ResponseWriter rw, BodyFuture bodyListener) {
    Histogram.Timer httpTimer = httpRequestLatency.startTimer();
    totalHTTPCounter.inc();
    final ClientID clientID = new ClientID();
    log.info("{}: Got Admin HTTPRequest:\n{}", clientID, httpRequest.toString());
    final String hrm = httpRequest.getHTTPRequestHeader().getRequestMethod();
    final String path = httpRequest.getHTTPRequestHeader().getRequestPath();
    final TurnRestConfig localConfig = config;
    SimpleResponse sr = null;
    if(hrm.equalsIgnoreCase(HTTPRequestMethod.OPTIONS.toString())) {
      sr = optionsResponse;
    } else {
      if(this.pingHandler.canHandle(path) ) {
        sr = pingHandler.handleRequest(clientID, httpRequest, localConfig);
        log.info("{}: ping processed", clientID);
      } else if(this.monitorHandler.canHandle(path)) {
        sr = monitorHandler.handleRequest(clientID, httpRequest, localConfig);
      } else {
        sr = this.defaultHandler.handleRequest(clientID, httpRequest, localConfig);
      }
    }
    if(sr != null) {
      log.info("{}:Sending Response:\n{}", clientID, sr.getHr());
      rw.sendHTTPResponse(sr.getHr());
      rw.writeBody(sr.getBody());
      rw.closeOnDone();
      rw.done();
    } else {
      log.error("{}: Got unhandled Message, path:{}", clientID, path);
      unhandledHTTPCounter.inc();
      rw.closeOnDone();
      rw.sendHTTPResponse(Utils.getNotFoundResponse());
      rw.done();
    }
    httpTimer.observeDuration();
  }

  private void handler(HTTPRequest httpRequest, ResponseWriter rw, BodyFuture bodyListener) {
    Histogram.Timer httpTimer = httpRequestLatency.startTimer();
    totalHTTPCounter.inc();
    final ClientID clientID = new ClientID();
    log.info("{}: Got HTTPRequest:\n{}", clientID, httpRequest.toString());
    final String hrm = httpRequest.getHTTPRequestHeader().getRequestMethod();
    final String path = httpRequest.getHTTPRequestHeader().getRequestPath();
    final TurnRestConfig localConfig = config;
    SimpleResponse sr = null;

    if(localConfig == null) {
      log.error("Config not loaded yet!");
      sr = new SimpleResponse(Utils.getBadRequestResponse());
    } else if(hrm.equalsIgnoreCase(HTTPRequestMethod.OPTIONS.toString())) {
      sr = optionsResponse;
    } else {
      for(HTTPHandler hh: handlers) {
        if(hh.canHandle(path)) {
          sr = hh.handleRequest(clientID, httpRequest, localConfig);
          if(sr != null) {
            break;
          }
        }
      }
    } 
    if(sr != null) {
      log.info("{}:Sending Response:\n{}", clientID, sr.getHr());
      rw.sendHTTPResponse(sr.getHr());
      rw.writeBody(sr.getBody());
      rw.closeOnDone();
      rw.done();
    } else {
      log.error("{}: Got unhandled Message, path:{}", clientID, path);
      unhandledHTTPCounter.inc();
      rw.closeOnDone();
      rw.sendHTTPResponse(Utils.getNotFoundResponse());
      rw.done();
    }
    httpTimer.observeDuration();
  }

  private class ClientConnectionsCheck extends HealthCheck {
    private final int max_clients = 4000;

    public ClientConnectionsCheck() {
    }

    @Override
    protected Result check() throws Exception {
      int clients = tse.getClientCount();
      if(clients > max_clients) {
        return Result.unhealthy("To many clients connected!  current:"+clients+" Max:"+max_clients);
      } else {
        return Result.healthy(clients+" clients connected");
      }
    }
  }
  
  public static class ClientID {
    
    public final String clientID;
    
    public ClientID() {
      this.clientID = StringUtils.makeRandomString(15).toUpperCase();
    }
    
    public ClientID(String clientID) {
      this.clientID = clientID;
    }
    
    @Override
    public int hashCode() {
      return clientID.hashCode();
    }
    
    @Override
    public String toString() {
      return clientID;
    }
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    LoggingConfig.configureLogging();
    String env_cf= System.getenv("TURNREST_CONFIG_FILE");
    String env_pa = System.getenv("TURNREST_PUBLIC_ADDRESS");
    String env_aa = System.getenv("TURNREST_ADMIN_ADDRESS");

    ArgumentParser parser = ArgumentParsers.newFor("TURNREST").build()
        .defaultHelp(true)
        .description("Turn Rest API for generating usernames and passwords");
    Argument arg_cf = parser.addArgument("--config_file")
        .required(true)
        .help("The confAdminRequestsig file to use for configuing the server");
    Argument arg_pa = parser.addArgument("--public_address")
        .required(true)
        .help("The listen address for the server (ip:port, 0.0.0.0:8080)");
    Argument arg_aa = parser.addArgument("--admin_address")
        .required(true)
        .help("The admin address for the server to use (ip:port, 0.0.0.0:8081) (can be the same as the public port)");

    if(env_cf != null) {
      arg_cf.required(false);
      arg_cf.setDefault(env_cf);
    }
    if(env_pa != null) {
      arg_pa.required(false);
      arg_pa.setDefault(env_pa);
    }
    if(env_aa != null) {
      arg_aa.required(false);
      arg_aa.setDefault(env_aa);
    }

    Namespace res = null;
    try {
      res = parser.parseArgs(args);
    } catch (ArgumentParserException e) {
      parser.handleError(e);
      System.exit(1);
    }

    final String cf = res.getString("config_file");
    final String pa = res.getString("public_address");
    final String aa = res.getString("admin_address");


//    log.info("Starting Service with the following arguments:\nconfigFile:{}\npublicAddress:{}\nadminAddress:{}", cf, pa, aa);
    final File cff = new File(cf);
    if(!cff.exists() || !cff.canRead()) {
      throw new RuntimeException("Can not find or read file:"+cf);
    }
    final InetSocketAddress public_addr = new InetSocketAddress(pa.split(":")[0],Integer.parseInt(pa.split(":")[1]));
    final InetSocketAddress admin_addr = new InetSocketAddress(aa.split(":")[0],Integer.parseInt(aa.split(":")[1]));

    TurnRest H = new TurnRest(public_addr, admin_addr, cff);
    synchronized(H) {
      while(true) {
        H.wait();
      }
    }
  }
}
