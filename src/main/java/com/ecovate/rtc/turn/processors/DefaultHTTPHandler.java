package com.ecovate.rtc.turn.processors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;

import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.HTTPUtils;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;

public class DefaultHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(DefaultHTTPHandler.class);
  
  public DefaultHTTPHandler() {

  }

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    return new SimpleResponse(HTTPUtils.getNotFoundResponse());
  }

  @Override
  public boolean canHandle(String path) {
    return true;
  }
  
  @Override
  public String getName() {
    return "DefaultHandler";
  }
}
