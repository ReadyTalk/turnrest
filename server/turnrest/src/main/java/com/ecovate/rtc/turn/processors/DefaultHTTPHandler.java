package com.ecovate.rtc.turn.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;

import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;
import com.ecovate.rtc.turn.Utils;

import io.prometheus.client.Counter;

public class DefaultHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(DefaultHTTPHandler.class);
  
  private final Counter badHttpRequests = new Counter.Builder()
      .help("Default HTTP requests handled")
      .name(TurnRest.TURN_REST+"default_http_requests")
      .register();
  
  public DefaultHTTPHandler() {

  }

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    badHttpRequests.inc();
    return new SimpleResponse(Utils.getNotFoundResponse());
  }

  @Override
  public boolean canHandle(String path) {
    return true;
  }
}
