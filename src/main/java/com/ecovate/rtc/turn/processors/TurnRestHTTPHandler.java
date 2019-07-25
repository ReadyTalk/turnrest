package com.ecovate.rtc.turn.processors;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.util.ExceptionUtils;

import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ecovate.rtc.turn.HTTPHandler;
import com.ecovate.rtc.turn.HTTPUtils;
import com.ecovate.rtc.turn.JWTUtils;
import com.ecovate.rtc.turn.SimpleResponse;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.TurnRestConfig;
import com.ecovate.rtc.turn.TurnRestResponse;

public class TurnRestHTTPHandler implements HTTPHandler {
  private static final Logger log = LoggerFactory.getLogger(TurnRestHTTPHandler.class);

  private final JWTUtils ju;

  public TurnRestHTTPHandler(JWTUtils ju) {
    this.ju = ju;
  }

  @Override
  public SimpleResponse handleRequest(ClientID clientID, HTTPRequest httpRequest, TurnRestConfig trc) {
    log.info("{}: processing turn user request", clientID);
    boolean authed = false;
    boolean hasScopes = false;
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
        final DecodedJWT djwt = ju.getJWT(httpRequest);
        hasScopes = ju.checkScopes(trc.getRequiredScopes(), false, djwt);
        boolean validJWT = ju.validateJWT(clientID, djwt);
        if(hasScopes && validJWT) {
          authed = true;
          log.info("{}: Got valid JWT", clientID);
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
      sr = new SimpleResponse(HTTPUtils.getOKResponse().makeBuilder()
          .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bb.remaining()))
          .build()
          , bb);
    } else {
      sr = new SimpleResponse(HTTPUtils.getUnauthorizedResponse());
    }
    log.info("{}: sending back code:{}", clientID, sr.getHr().getResponseCode());
    return sr;
  }

  @Override
  public boolean canHandle(String path) {
    if(path.startsWith("/turn")) return true;
    return false;
  }
  
  @Override
  public String getName() {
    return "TurnHandler";
  }
}
