package com.ecovate.rtc.turn;

import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;

public class HTTPUtils {
  public static final String HTTP_ACAO_HEADER = "Access-Control-Allow-Origin";
  public static final String HTTP_ACAM_HEADER = "Access-Control-Allow-Methods";
  public static final String HTTP_ACAH_HEADER = "Access-Control-Allow-Headers";
  public static final String HTTP_CACHE_HEADER = "Cache-Control";
  
  private static volatile HTTPResponse BAD_REQUEST_RESPONSE = new HTTPResponseBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_USER_AGENT, "dontlook")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "close")
      .setResponseCode(HTTPResponseCode.BadRequest)
      .build();
  private static volatile HTTPResponse NOT_FOUND_RESPONSE = new HTTPResponseBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_USER_AGENT, "dontlook")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "close")
      .setResponseCode(HTTPResponseCode.NotFound)
      .build();
  private static volatile HTTPResponse OK_RESPONSE = new HTTPResponseBuilder()
      .setHeader(HTTP_ACAO_HEADER, "*")
      .setHeader(HTTP_ACAM_HEADER, "GET, POST")
      .setHeader(HTTP_ACAH_HEADER, "authorization")
      .setHeader(HTTP_CACHE_HEADER, "no-store")
      .setHeader(HTTPConstants.HTTP_KEY_USER_AGENT, "dontlook")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "close")
      .setResponseCode(HTTPResponseCode.OK)
      .build();

  private static volatile HTTPResponse UNAUTHROIZED_RESPONSE = new HTTPResponseBuilder()
      .setHeader(HTTPConstants.HTTP_KEY_USER_AGENT, "dontlook")
      .setHeader(HTTPConstants.HTTP_KEY_CONNECTION, "close")
      .setResponseCode(HTTPResponseCode.Unauthorized)
      .build();
  
  public static HTTPResponse getOKResponse() {
    return OK_RESPONSE;
  }

  public static HTTPResponse getUnauthorizedResponse() {
    return UNAUTHROIZED_RESPONSE;
  }

  public static HTTPResponse getNotFoundResponse() {
    return NOT_FOUND_RESPONSE;
  }

  public static HTTPResponse getBadRequestResponse() {
    return BAD_REQUEST_RESPONSE;
  }

  public static void setOKResponse(HTTPResponse hr) {
    OK_RESPONSE = hr;
  }

  public static void setUnauthorizedResponse(HTTPResponse hr) {
    UNAUTHROIZED_RESPONSE = hr;
  }

  public static void setNotFoundResponse(HTTPResponse hr) {
    NOT_FOUND_RESPONSE= hr;
  }

  public static void setBadRequestResponse(HTTPResponse hr) {
    BAD_REQUEST_RESPONSE= hr;
  }
  
  public static void processHTTPDefaults(final TurnRestConfig trc) {
    String origin = trc.getAllowedOrigin();
    if(origin == null || origin.equals("")) {
      origin = "*";
    }
    if(OK_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) == null || 
        (OK_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) != null && 
        !OK_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER).equals(origin))) {
      OK_RESPONSE = OK_RESPONSE.makeBuilder().setHeader(HTTP_ACAO_HEADER, origin).build();
    }
    if(UNAUTHROIZED_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER)  == null || 
        (UNAUTHROIZED_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) != null && 
        !UNAUTHROIZED_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER).equals(origin))) {
      UNAUTHROIZED_RESPONSE = UNAUTHROIZED_RESPONSE.makeBuilder().setHeader(HTTP_ACAO_HEADER, origin).build();
    }
    if(NOT_FOUND_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER)  == null || 
        (NOT_FOUND_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) != null && 
        !NOT_FOUND_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER).equals(origin))) {
      NOT_FOUND_RESPONSE = NOT_FOUND_RESPONSE.makeBuilder().setHeader(HTTP_ACAO_HEADER, origin).build();
    }
    if(BAD_REQUEST_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) == null || 
        (BAD_REQUEST_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER) != null && 
        !BAD_REQUEST_RESPONSE.getHeaders().getHeader(HTTP_ACAO_HEADER).equals(origin))) {
      BAD_REQUEST_RESPONSE = BAD_REQUEST_RESPONSE.makeBuilder().setHeader(HTTP_ACAO_HEADER, origin).build();
    }
  }
}
