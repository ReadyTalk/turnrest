package com.ecovate.rtc.turn;

import org.threadly.litesockets.protocols.http.request.HTTPRequest;

import com.ecovate.rtc.turn.TurnRest.ClientID;

public interface HTTPHandler {

  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc);
  public boolean canHandle(String path);
}
