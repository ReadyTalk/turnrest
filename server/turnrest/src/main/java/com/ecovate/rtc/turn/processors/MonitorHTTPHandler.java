package com.ecovate.rtc.turn.processors;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.utils.IOUtils;
import org.threadly.util.Clock;

import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.json.HealthCheckModule;
import com.codahale.metrics.json.MetricsModule;
import com.codahale.metrics.jvm.ThreadDump;
import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;
import com.ecovate.rtc.turn.Utils;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.exporter.common.TextFormat;

public class MonitorHTTPHandler implements HTTPHandler {
  private final static Logger log = LoggerFactory.getLogger(MonitorHTTPHandler.class);
  private static final int CACHE_TIME = 1000;

  private static final String MONITOR_PAGE = "<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\""
      + "        \"http://www.w3.org/TR/html4/loose.dtd\">" + "<html>" + "<head>" + "  <title>Metrics</title>"
      + "</head>" + "<body>" + "  <h1>Operational Menu</h1>" + "  <ul>"
      + "    <li><a href=\"/monitor/metrics\">Metrics</a></li>"
      + "    <li><a href=\"/monitor/ping\">Ping</a></li>"
      + "    <li><a href=\"/monitor/threads\">Threads</a></li>"
      + "    <li><a href=\"/monitor/healthcheck\">Healthcheck</a></li>" + "  </ul>" + "</body>" + "</html>";
  private static final String PONG = "pong\n";

  private final ObjectMapper HC_MAPPER = new ObjectMapper().registerModule(new HealthCheckModule());
  private final ObjectMapper MR_MAPPER = new ObjectMapper().registerModule(new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false));
  private final ThreadDump THREAD_DUMP = new ThreadDump(ManagementFactory.getThreadMXBean());

  private final Counter monitorRequests = new Counter.Builder()
      .help("Monitor HTTP requests").name(TurnRest.TURN_REST+"monitor_http_requests").register();
  private final Counter badMonitorRequests = new Counter.Builder()
      .help("Bad Monitor HTTP requests").name(TurnRest.TURN_REST+"monitor_http_unhandled").register();

  private volatile long lastHealthUpdate = Clock.lastKnownForwardProgressingMillis() - (CACHE_TIME*2);
  private volatile long lastMetricUpdate = lastHealthUpdate;
  private volatile long lastPingUpdate = Clock.lastKnownForwardProgressingMillis();
  private volatile long lastMonitorPageUpdate = Clock.lastKnownForwardProgressingMillis();
  private volatile String cachedHealth = null;
  private volatile String cachedMetrics = null;
  private volatile SimpleResponse cachedHealthResponse = null;
  private volatile SimpleResponse cachedMetricsResponse = null;
  private volatile SimpleResponse cachedPingResponse = new SimpleResponse(Utils.getOKResponse().makeBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(PONG.length()))
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
      .build(), ByteBuffer.wrap(PONG.getBytes()));
  
  private volatile SimpleResponse cachedMonitorPage = new SimpleResponse(Utils.getOKResponse().makeBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(MONITOR_PAGE.length()))
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
      .build(), ByteBuffer.wrap(MONITOR_PAGE.getBytes()));
  
  public MonitorHTTPHandler() {
    updateHealthCheck();
    updateMetrics();
  }


  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    log.info("{}: processing metrics/health", clientID);
    monitorRequests.inc();
    return processHealthCheck(httpRequest);
  }


  @Override
  public boolean canHandle(String path) {
    if(path.startsWith("/monitor")) return true;
    return false;
  }

  private void updateHealthCheck() {
    if(Clock.lastKnownForwardProgressingMillis() - lastHealthUpdate > CACHE_TIME) {
      synchronized(HC_MAPPER) {
        if(Clock.lastKnownForwardProgressingMillis() - lastHealthUpdate > CACHE_TIME) {
          try {
            ByteArrayOutputStream dump = new ByteArrayOutputStream();
            final SortedMap<String, HealthCheck.Result> hc_results = Utils.getHealthCheckRegistry().runHealthChecks();
            HC_MAPPER.writerWithDefaultPrettyPrinter().writeValue(dump, hc_results);
            cachedHealth = dump.toString();
          } catch (Exception e) {
            // ignored
          } finally {
            long t = Clock.lastKnownForwardProgressingMillis();
            log.info("Updated HealthChecks:{}", t);
            lastHealthUpdate = t;
          }
        }
      }
      if(cachedHealth.contains("false")) {
        cachedHealthResponse = new SimpleResponse(Utils.getOKResponse().makeBuilder().setResponseCode(HTTPResponseCode.InternalServerError)
            .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(cachedHealth.length()))
            .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "application/json")
            .build(),
            ByteBuffer.wrap(cachedHealth.getBytes()));
      } else {
        cachedHealthResponse = new SimpleResponse(Utils.getOKResponse().makeBuilder().setResponseCode(HTTPResponseCode.OK)
            .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(cachedHealth.length()))
            .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "application/json")
            .build(),
            ByteBuffer.wrap(cachedHealth.getBytes()));  
      }
    }
  }
  
  private void updatePing() {
    if(Clock.lastKnownForwardProgressingMillis() - lastPingUpdate > CACHE_TIME) {
      cachedPingResponse = new SimpleResponse(Utils.getOKResponse().makeBuilder()
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(PONG.length()))
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
          .build(), ByteBuffer.wrap(PONG.getBytes()));
      lastPingUpdate = Clock.lastKnownForwardProgressingMillis();
    }
  }
  private void updateMonitorPage() {
    if(Clock.lastKnownForwardProgressingMillis() - lastMonitorPageUpdate > CACHE_TIME) {
      cachedMonitorPage = new SimpleResponse(Utils.getOKResponse().makeBuilder()
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(MONITOR_PAGE.length()))
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
          .build(), ByteBuffer.wrap(MONITOR_PAGE.getBytes()));
      lastMonitorPageUpdate = Clock.lastKnownForwardProgressingMillis();
    }
  }

  private void updateMetrics() {



    if(Clock.lastKnownForwardProgressingMillis() - lastMetricUpdate > CACHE_TIME) {
      synchronized(MR_MAPPER) {
        if(Clock.lastKnownForwardProgressingMillis() - lastMetricUpdate > CACHE_TIME) {
          StringWriter writer = new StringWriter();
          try {
            TextFormat.write004(writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(Collections.emptySet()));
            cachedMetrics = writer.toString();
          } catch (Exception e) {
            log.error("Error parsing metrics!", e);
          } finally {
            long t = Clock.lastKnownForwardProgressingMillis();
            log.info("Updated Metrics:{}", t);
            lastMetricUpdate = t;
            IOUtils.closeQuietly(writer);
          }
        }
      }
      cachedMetricsResponse = new SimpleResponse(Utils.getOKResponse().makeBuilder().setResponseCode(HTTPResponseCode.OK)
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(cachedMetrics.length()))
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/plain")
          .build(),
          ByteBuffer.wrap(cachedMetrics.getBytes()));  
    }
  }

  private SimpleResponse processHealthCheck(HTTPRequest hr) {
    final String path = hr.getHTTPRequestHeader().getRequestPath();
    if(path.equals("/monitor") || path.equals("/monitor/") ) {
      updateMonitorPage();
      return cachedMonitorPage;
    } else if(path.equals("/monitor/healthcheck") || path.equals("/monitor/healthcheck/")) {
      updateHealthCheck();
      return cachedHealthResponse;
    } else if(path.equals("/monitor/metrics") || path.equals("/monitor/metrics/")) {
      updateMetrics();
      return cachedMetricsResponse;
    } else if(path.equals("/monitor/ping") || path.equals("/monitor/ping/")) {
      updatePing();
      return cachedPingResponse;
    } else if(path.equals("/monitor/threads") || path.equals("/monitor/threads/")) {
      ByteArrayOutputStream dump = new ByteArrayOutputStream();
      THREAD_DUMP.dump(dump);
      return new SimpleResponse(Utils.getOKResponse().makeBuilder().setResponseCode(HTTPResponseCode.InternalServerError)
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(dump.size()))
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
          .build(), ByteBuffer.wrap(dump.toByteArray()));
    } else {
      this.badMonitorRequests.inc();
      return new SimpleResponse(Utils.getNotFoundResponse(), IOUtils.EMPTY_BYTEBUFFER);
    }
  }
}
