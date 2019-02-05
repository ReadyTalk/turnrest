package com.ecovate.rtc.turn;

import java.nio.ByteBuffer;

import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.litesockets.utils.IOUtils;

public class SimpleResponse {

  private final HTTPResponse hr;
  private final ByteBuffer body;
  public SimpleResponse(HTTPResponse hr) {
    this.hr = hr;
    this.body = IOUtils.EMPTY_BYTEBUFFER;
  }
  public SimpleResponse(HTTPResponse hr, ByteBuffer body) {
    this.hr = hr;
    this.body = body;
  }
  public HTTPResponse getHr() {
    return hr;
  }
  public ByteBuffer getBody() {
    return body.duplicate();
  }
  public boolean isGood() {
    return hr.getResponseCode() == HTTPResponseCode.OK;
  }
}
