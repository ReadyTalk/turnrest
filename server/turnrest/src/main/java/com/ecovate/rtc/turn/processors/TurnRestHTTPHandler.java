package com.ecovate.rtc.turn.processors;

import java.nio.ByteBuffer;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.util.ExceptionUtils;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;
import com.ecovate.rtc.turn.TurnRestResponse;
import com.ecovate.rtc.turn.Utils;

import io.prometheus.client.Counter;
import io.prometheus.client.Histogram;

public class TurnRestHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(TurnRestHTTPHandler.class);
  
  private final Counter turnRequests = new Counter.Builder()
      .help("Total turn HTTP requests").name(TurnRest.TURN_REST+"turn_http_requests").register();
  private final Counter badTurnRequests = new Counter.Builder()
      .help("Bad turn HTTP requests").name(TurnRest.TURN_REST+"turn_http_fail").register();
  private final Histogram turnRequestLatency = Histogram.build()
      .name("turn_requests_latency_seconds").help("HTTP Turn Request latency in seconds.").register();
  
  public TurnRestHTTPHandler() {
  }
  

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    Histogram.Timer httpTimer = turnRequestLatency.startTimer();
    log.info("{}: processing turn user request", clientID);
    turnRequests.inc();
    boolean authed = false;
    DecodedJWT djwt = null;
    String jwtUser = null;
    if(trc.getForcedUser() != null) {
      jwtUser = trc.getForcedUser();
    } else {
      jwtUser = "AutoUser-"+clientID;
    }
    try {
      if(trc.getIgnoreJWT()) {
        log.info("{}: Config marked to skip JWT Auth, skipping!", clientID);
        authed = true;
      } else {
        log.info("{}: Processing JWT", clientID);
        djwt = Utils.validateHTTPJWT(clientID, trc, httpRequest);
        if(djwt != null) {
          log.info("{}: Got valid JWT", clientID);
          List<String> reqScopes = trc.getRequiredScopes();
          List<String> scopes = djwt.getClaim("scp").asList(String.class);
          if(reqScopes != null && reqScopes.size() > 0) {
            if(scopes != null && scopes.size() > 0) {
              for(String rs: reqScopes) {
                if(scopes.contains(rs)) {
                  authed = true;
                  log.info("{}: Authed with JWT, matched a scope", clientID);
                  break;
                }
              }
            }
            if (!authed) {
              log.error("{}: could not find a required scope in the JWT", clientID);
            }
          } else if(reqScopes == null || reqScopes.size() == 0) {
            log.info("{}: Authed with JWT, no required scopes.", clientID);
            authed = true;  
          }
          if(authed && trc.getForcedUser() == null) {
            if(trc.getUserClaim() != null) {
              Claim tmp = djwt.getClaim(trc.getUserClaim());
              if(!tmp.isNull() && !tmp.asString().equals("")) {
                jwtUser = tmp.asString();
              }
            }
          }
        } else {
          log.info("{}: could not validate JWT", clientID);
        }
      }
    } catch(Exception e) {
      log.info("{}: Exception processing auth\n{}", clientID, ExceptionUtils.stackToString(e));
    }
    SimpleResponse sr = null;
    if(authed) {
      log.info("{}: Set User to:{}", clientID, jwtUser);
      ByteBuffer bb = ByteBuffer.wrap(TurnRestResponse.makeResponse(trc, jwtUser).toString().getBytes());
      httpTimer.observeDuration();
      sr = new SimpleResponse(Utils.getOKResponse().makeBuilder()
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bb.remaining()))
          .build()
          , bb);
    } else {
      badTurnRequests.inc();
      httpTimer.observeDuration();
      sr = new SimpleResponse(Utils.getUnauthorizedResponse());
    }
    log.info("{}: sending back code:{}", clientID, sr.getHr().getResponseCode());
    return sr;
  }

  @Override
  public boolean canHandle(String path) {
    if(path.startsWith("/turn")) return true;
    return false;
  }
}
