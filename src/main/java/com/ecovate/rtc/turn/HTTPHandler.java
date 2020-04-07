package com.ecovate.rtc.turn;

import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;

import com.ecovate.rtc.turn.TurnRest.ClientID;


public interface HTTPHandler {

  public ListenableFuture<SimpleResponse> handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc);
  public boolean canHandle(String path);
  public String getName();
}
