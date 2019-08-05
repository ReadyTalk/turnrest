package com.ecovate.rtc.turn.processors;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.util.Clock;

import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.HTTPUtils;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;

public class PingHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(PingHTTPHandler.class);
  
  private static final long CACHE_TIME = 5000;
  private static final String PONG = "pong\n";
  
  private volatile SimpleResponse cachedPingResponse = new SimpleResponse(HTTPUtils.getOKResponse().makeBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(PONG.length()))
      .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
      .build(), ByteBuffer.wrap(PONG.getBytes()));
  private volatile long lastPingUpdate = Clock.lastKnownForwardProgressingMillis();
  
  public PingHTTPHandler() {

  }

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    log.info("{}: processing ping request", clientID);
    updatePing();
    return cachedPingResponse;
  }

  @Override
  public boolean canHandle(String path) {
    if(path.equals("/ping") || path.equals("/monitor/ping")) return true;
    return false;
  }
  
  @Override
  public String getName() {
    return "PingHandler";
  }
  
  private void updatePing() {
    if(Clock.lastKnownForwardProgressingMillis() - lastPingUpdate > CACHE_TIME) {
      cachedPingResponse = new SimpleResponse(HTTPUtils.getOKResponse().makeBuilder()
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(PONG.length()))
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_TYPE, "text/html")
          .build(), ByteBuffer.wrap(PONG.getBytes()));
      lastPingUpdate = Clock.lastKnownForwardProgressingMillis();
    }
  }
}
