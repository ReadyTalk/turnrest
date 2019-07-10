package com.ecovate.rtc.turn;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.util.StringUtils;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.prometheus.client.CollectorRegistry;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  
  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  public static final String HTTP_ACAO_HEADER = "Access-Control-Allow-Origin";
  public static final String HTTP_ACAM_HEADER = "Access-Control-Allow-Methods";
  public static final String HTTP_ACAH_HEADER = "Access-Control-Allow-Headers";
  public static final String HTTP_CACHE_HEADER = "Cache-Control";
  private static final PriorityScheduler PS = new PriorityScheduler(20);
  
  private static final ThreadedSocketExecuter TSE = new ThreadedSocketExecuter(Utils.getScheduler());
  

  private static volatile CollectorRegistry mr;
  private static volatile HealthCheckRegistry hcr;

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
      .setHeader(Utils.HTTP_ACAO_HEADER, "*")
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
  
  static {
    TSE.start();
  }

  public static PriorityScheduler getScheduler() {
    return PS;
  }
  
  public static SocketExecuter getSocketExecuter() {
    return TSE;
  }
  
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

  public static CollectorRegistry getMetricsRegistry() {
    if(mr == null) {
      synchronized(Utils.class) {
        if(mr == null) {
          mr = CollectorRegistry.defaultRegistry;
        }
      }
    }
    return mr;
  }

  public static HealthCheckRegistry getHealthCheckRegistry() {
    if(hcr == null) {
      synchronized(Utils.class) {
        if(hcr == null) {
          hcr = new HealthCheckRegistry();
        }
      }
    }
    return hcr;
  }
  
  public static void reset() {
    hcr = null;
    CollectorRegistry.defaultRegistry.clear();
  }

  public static String makeUserName() {
    return "user-"+StringUtils.makeRandomString(16);
  }

  public static String SHABytes(byte[] ... b)  {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      for(byte[] ba: b) {
        digest.update(ba);
      }
      return bytesToHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
  
  public static String SHAString(String ... s)  {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      for(String ss: s) {
        digest.update(ss.getBytes());
      }
      return bytesToHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String bytesToHex(byte[] hash) {
    StringBuffer hs = new StringBuffer();
    for(byte b: hash) {
      hs.append(String.format("%02x", b));
    }
    return hs.toString();
  }
}
