package com.ecovate.rtc.turn;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestMethod;
import org.threadly.litesockets.server.http.HTTPServer;
import org.threadly.litesockets.server.http.HTTPServer.BodyFuture;
import org.threadly.litesockets.server.http.HTTPServer.ResponseWriter;
import org.threadly.util.AbstractService;
import org.threadly.util.StringUtils;

import com.codahale.metrics.health.HealthCheck;
import com.ecovate.rtc.turn.processors.ClientHTTPHandler;
import com.ecovate.rtc.turn.processors.DefaultHTTPHandler;
import com.ecovate.rtc.turn.processors.MonitorHTTPHandler;
import com.ecovate.rtc.turn.processors.PingHTTPHandler;
import com.ecovate.rtc.turn.processors.TurnRestHTTPHandler;
import com.ecovate.rtc.turn.stats.NetworkMetrics;

import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.Histogram;
import io.prometheus.client.hotspot.BufferPoolsExports;
import io.prometheus.client.hotspot.ClassLoadingExports;
import io.prometheus.client.hotspot.DefaultExports;
import io.prometheus.client.hotspot.GarbageCollectorExports;
import io.prometheus.client.hotspot.MemoryAllocationExports;
import io.prometheus.client.hotspot.MemoryPoolsExports;
import io.prometheus.client.hotspot.StandardExports;
import io.prometheus.client.hotspot.ThreadExports;
import io.prometheus.client.hotspot.VersionInfoExports;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class TurnRest extends AbstractService {
  private static final int CONFIG_FILE_SCAN_TIMER_MS = 1000*30; //30 seconds 
  public static final String TURN_REST = "turn_rest_";

  private static final Logger log = LoggerFactory.getLogger(TurnRest.class);

  private final Gauge startTime = new Gauge.Builder()
      .help("Process Starttime since epoch")
      .name(TURN_REST+"start_time_seconds")
      .register(Utils.getMetricsRegistry());
  private final Counter httpResponses = new Counter.Builder()
      .help("HTTP Response codes")
      .labelNames("response")
      .name(TURN_REST+"http_response")
      .register(Utils.getMetricsRegistry());
  
  private final Histogram httpRequestLatency = Histogram.build()
      .name(TURN_REST+"http_requests_latency_seconds")
      .help("HTTP Request latency in seconds.")
      .labelNames("method","handler")
      .register(Utils.getMetricsRegistry());
  

  private final List<HTTPHandler> handlers;
  private final Runnable cfr = ()->loadConfigFile();
  private final Runnable networkMetricsRunner = ()->NetworkMetrics.updateNetworkMetrics();
  private final MonitorHTTPHandler monitorHandler;
  private final PingHTTPHandler pingHandler = new PingHTTPHandler();
  private final DefaultHTTPHandler defaultHandler = new DefaultHTTPHandler(); 
  private final InetSocketAddress publicAddress;
  private final InetSocketAddress adminAddress;
  private final HTTPServer httpServer;
  private final HTTPServer adminHttpServer;

  
  private final PriorityScheduler ps;
  private final SocketExecuter se;
  private final File cf;
  private final JWTUtils ju;
  private final long fileScanTime; 

  private final SimpleResponse optionsResponse;
  private volatile long cf_modTime = -1;
  private volatile TurnRestConfig config = null;

  public TurnRest(InetSocketAddress publicAddress, InetSocketAddress adminAddress, File cf) throws IOException {
    this(Utils.getScheduler(), Utils.getSocketExecuter(), publicAddress, adminAddress, cf, CONFIG_FILE_SCAN_TIMER_MS);
  }

  public TurnRest(PriorityScheduler ps, 
      SocketExecuter se, 
      InetSocketAddress publicAddress, 
      InetSocketAddress adminAddress, 
      File cf, 
      long fileScanTime) throws IOException {
    startTime.setToCurrentTime();
    this.ps = ps;
    this.se = se;
    this.cf = cf;
    this.ju = new JWTUtils(ps);
    this.publicAddress = publicAddress;
    this.adminAddress = adminAddress;
    this.fileScanTime = fileScanTime;

    optionsResponse = new SimpleResponse(HTTPUtils.getOKResponse());

    this.httpServer = new HTTPServer(this.se, this.publicAddress.getAddress().getHostAddress(), this.publicAddress.getPort());
    this.httpServer.setHandler((x,y,z)->handler(x,y,z));
    loadConfigFile();
    configureHealthChecks();
    monitorHandler = new MonitorHTTPHandler();
    ArrayList<HTTPHandler> hl = new ArrayList<>();
    hl.add(new ClientHTTPHandler());
    hl.add(pingHandler);
    if(publicAddress.equals(adminAddress)) {
      hl.add(monitorHandler);
      adminHttpServer = httpServer;
    } else {
      this.adminHttpServer = new HTTPServer(this.se, this.adminAddress.getAddress().getHostAddress(), this.adminAddress.getPort());
      this.adminHttpServer.setHandler((x,y,z)->adminHandler(x,y,z));  
    }
    hl.add(new TurnRestHTTPHandler(ju));  
    hl.add(defaultHandler);
    handlers = Collections.unmodifiableList(hl);
  }


  @Override
  protected void startupService() {
    NetworkMetrics.registerNetworkMetrics(this.se);
    this.ps.scheduleAtFixedRate(networkMetricsRunner, 500, 500);

    ps.scheduleAtFixedRate(cfr, fileScanTime, fileScanTime);
    httpServer.start();
    adminHttpServer.startIfNotStarted();
    log.info("Server Started.");
  }

  @Override
  protected void shutdownService() {
    httpServer.stopIfRunning();
    adminHttpServer.stopIfRunning();
    ps.remove(cfr);
    ps.remove(networkMetricsRunner);
  }

  public JWTUtils getJWTUtils() {
    return ju;
  }

  private void configureHealthChecks() {
    Utils.getHealthCheckRegistry().register(ClientConnectionsCheck.class.getSimpleName(), new ClientConnectionsCheck());
  }

  private void loadConfigFile() {
    if(cf_modTime <0 || cf.lastModified() != cf_modTime) {
      log.info("loading new Config file!");
      try {
        TurnRestConfig lc  = TurnRestConfig.openConfigFile(cf);
        HTTPUtils.processHTTPDefaults(lc);
        ju.updateJWKProvider(new HashSet<String>(lc.getJwkURLs()));
        ju.updateStaticB64Keys(new HashSet<>(lc.getJwtPublicKeys()));
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

    final ClientID clientID = new ClientID();
    log.info("{}: Got Admin HTTPRequest:\n{}", clientID, httpRequest.toString());
    Histogram.Timer httpTimer = null;
    final String hrm = httpRequest.getHTTPRequestHeader().getRequestMethod();
    final String path = httpRequest.getHTTPRequestHeader().getRequestPath();
    final TurnRestConfig localConfig = config;
    SimpleResponse sr = null;
    if(hrm.equalsIgnoreCase(HTTPRequestMethod.OPTIONS.toString())) {
      httpTimer = httpRequestLatency.labels(hrm, "/").startTimer();
      sr = optionsResponse;
    } else {
      if(this.pingHandler.canHandle(path) ) {
        httpTimer = httpRequestLatency.labels(hrm, this.pingHandler.getName()).startTimer();
        sr = pingHandler.handleRequest(clientID, httpRequest, localConfig);
        log.info("{}: ping processed", clientID);
      } else if(this.monitorHandler.canHandle(path)) {
        httpTimer = httpRequestLatency.labels(hrm, this.monitorHandler.getName()).startTimer();
        sr = monitorHandler.handleRequest(clientID, httpRequest, localConfig);
      } else {
        httpTimer = httpRequestLatency.labels(hrm, this.defaultHandler.getName()).startTimer();
        sr = this.defaultHandler.handleRequest(clientID, httpRequest, localConfig);
      }
    }
    if(sr != null) {
      log.info("{}:Sending Response:\n{}", clientID, sr.getHr());
      rw.sendHTTPResponse(sr.getHr());
      rw.writeBody(sr.getBody());
      rw.closeOnDone();
      rw.done();
      httpResponses.labels(Integer.toString(sr.getHr().getResponseCode().getId())).inc();
    } else {
      log.error("{}: Got unhandled Message, path:{}", clientID, path);
      rw.closeOnDone();
      rw.sendHTTPResponse(HTTPUtils.getNotFoundResponse());
      rw.done();
      httpResponses.labels(Integer.toString(HTTPUtils.getNotFoundResponse().getResponseCode().getId())).inc();
    }
    if(httpTimer != null) {
      httpTimer.observeDuration();
    }
  }

  private void handler(HTTPRequest httpRequest, ResponseWriter rw, BodyFuture bodyListener) {
    Histogram.Timer httpTimer = null;
    final ClientID clientID = new ClientID();
    log.info("{}: Got HTTPRequest:\n{}", clientID, httpRequest.toString());
    final String hrm = httpRequest.getHTTPRequestHeader().getRequestMethod();
    final String path = httpRequest.getHTTPRequestHeader().getRequestPath();
    final TurnRestConfig localConfig = config;
    SimpleResponse sr = null;

    if(localConfig == null) {
      log.error("Config not loaded yet!");
      sr = new SimpleResponse(HTTPUtils.getBadRequestResponse());
    } else if(hrm.equalsIgnoreCase(HTTPRequestMethod.OPTIONS.toString())) {
      sr = optionsResponse;
    } else {
      for(HTTPHandler hh: handlers) {
        if(hh.canHandle(path)) {
          sr = hh.handleRequest(clientID, httpRequest, localConfig);
          httpTimer = httpRequestLatency.labels(hrm, hh.getName()).startTimer();
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
      httpResponses.labels(Integer.toString(sr.getHr().getResponseCode().getId())).inc();
    } else {
      log.error("{}: Got unhandled Message, path:{}", clientID, path);
      rw.closeOnDone();
      rw.sendHTTPResponse(HTTPUtils.getNotFoundResponse());
      rw.done();
      httpResponses.labels(Integer.toString(HTTPUtils.getNotFoundResponse().getResponseCode().getId())).inc();
    }
    if(httpTimer != null) {
      httpTimer.observeDuration();
    }
  }

  private class ClientConnectionsCheck extends HealthCheck {
    private final int max_clients = 4000;

    public ClientConnectionsCheck() {
    }

    @Override
    protected Result check() throws Exception {
      int clients = se.getClientCount();
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
    
    new StandardExports().register(Utils.getMetricsRegistry());
    new MemoryPoolsExports().register(Utils.getMetricsRegistry());
    new MemoryAllocationExports().register(Utils.getMetricsRegistry());
    new BufferPoolsExports().register(Utils.getMetricsRegistry());
    new GarbageCollectorExports().register(Utils.getMetricsRegistry());
    new ThreadExports().register(Utils.getMetricsRegistry());
    new ClassLoadingExports().register(Utils.getMetricsRegistry());
    new VersionInfoExports().register(Utils.getMetricsRegistry());
    
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
    H.startIfNotStarted();
    synchronized(H) {
      while(true) {
        H.wait();
      }
    }
  }

}
