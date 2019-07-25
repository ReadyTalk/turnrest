package com.ecovate.rtc.turn.processors;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPRequestMethod;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.HTTPUtils;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;
import com.google.common.io.ByteStreams;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;
import io.prometheus.client.Histogram.Timer;

public class ClientHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(ClientHTTPHandler.class);

  private static final Histogram clientTimes = new Histogram.Builder()
      .help("Client HTTP requests latency")
      .name(TurnRest.TURN_REST+"client_http_requests_seconds")
      .register();
  
  private static final Counter clientRequests = new Counter.Builder()
      .help("Client HTTP requests count")
      .name(TurnRest.TURN_REST+"client_http_requests")
      .labelNames("method","file")
      .register();


  private static final List<String> filenames = Arrays.asList(new String[] {
      "/html/turnRestClient-1.1.0.js",
      "/html/turnRestClient-1.2.0.js",
      "/html/turnRestClient-latest.js",
  });
  private static final String filesResponseTemplate="<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\""
      + "\"http://www.w3.org/TR/html4/loose.dtd\">" 
      + "<html>"
      + "<head>" 
      + "<title>Client Versions</title>"
      + "</head>" 
      + "<body>" 
      + "  <h1>Version</h1>" 
      + "  <ul>"
      + "--REPLACE--"
      + "  </ul>" 
      + "</body>" 
      + "</html>";

  private static final String filesResponse;

  static {
    StringBuilder sb = new StringBuilder();
    for(String f: filenames) {
      String name = new File(f).getName();
      sb.append("<li><a href=\"./")
      .append(name)
      .append("\">")
      .append(name)
      .append("</a></li>");
    }
    filesResponse = filesResponseTemplate.replaceAll("--REPLACE--", sb.toString());
  }

  private final Map<String, SimpleResponse> files;
  private final SimpleResponse rootResponse;

  public ClientHTTPHandler() {
    log.info("Starting Client handler!");
    HTTPResponseBuilder hrb = new HTTPResponseBuilder()
        .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "close")
        .setHeader(HTTPUtils.HTTP_ACAO_HEADER, "*")
        .setHeader(HTTPConstants.HTTP_KEY_USER_AGENT, "dontlook")
        .setResponseCode(HTTPResponseCode.OK);
    HashMap<String, SimpleResponse> map = new HashMap<>();
    byte[] ba = null;
    for(String f: filenames) {
      log.info("Opening file:{}", f);
      try {
        if(!f.endsWith("latest.js")) {
          ba = ByteStreams.toByteArray(ClientHTTPHandler.class.getResourceAsStream((f)));
        }
        hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Long.toString(ba.length));
        hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "application/javascript");
        hrb.setHeader("Cache-Control", "max-age=2592000");
        map.put(new File(f).getName(), new SimpleResponse(hrb.build(), ByteBuffer.wrap(ba).asReadOnlyBuffer()));
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    files = Collections.unmodifiableMap(map);
    hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Long.toString(filesResponse.length()));
    hrb.setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html");
    rootResponse = new SimpleResponse(hrb.build(), ByteBuffer.wrap(filesResponse.getBytes()).asReadOnlyBuffer());
    hrb.removeHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH)
    .removeHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE)
    .setResponseCode(HTTPResponseCode.NotFound);
  }

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    final Timer timer = clientTimes.startTimer();
    final String hrm = httpRequest.getHTTPRequestHeader().getRequestMethod();
    try {
      if(hrm.equalsIgnoreCase(HTTPRequestMethod.GET.toString())) {
        String path = httpRequest.getHTTPRequestHeader().getRequestPath().replaceFirst("/clients", "");
        if(path.startsWith("/")) {
          path = path.substring(1);
        }
        if(path.length() == 0) {
          log.info("{}:Client Served Index", clientID);
          clientRequests.labels(hrm, "index").inc();
          return rootResponse;
        } else {
          SimpleResponse fileResponse = files.get(path);
          if(fileResponse != null) {
            clientRequests.labels(hrm, path).inc();
            log.info("{}:Client Served File:{}", clientID, path);
            return fileResponse;
          }
        }
      }
      clientRequests.labels(hrm, "error").inc();
      return new SimpleResponse(HTTPUtils.getNotFoundResponse());
    } finally {
      timer.close();
    }
  }

  @Override
  public boolean canHandle(String currentPath) {
    return currentPath.startsWith("/clients");
  }
  
  @Override
  public String getName() {
    return "clientHandler";
  }
}
