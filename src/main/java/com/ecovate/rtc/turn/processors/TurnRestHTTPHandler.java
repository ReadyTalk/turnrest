package com.ecovate.rtc.turn.processors;

import java.nio.ByteBuffer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.future.FutureCallback;
import org.threadly.concurrent.future.ImmediateResultListenableFuture;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
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
  public ListenableFuture<SimpleResponse> handleRequest(final ClientID clientID, final HTTPRequest httpRequest, final TurnRestConfig trc) {
    log.info("{}: processing turn user request", clientID);
    SettableListenableFuture<SimpleResponse> slf = new SettableListenableFuture<SimpleResponse>(); 

    ListenableFuture<Boolean> authLF = ImmediateResultListenableFuture.BOOLEAN_FALSE_RESULT;
    boolean hasScopes = false;
    String jwtUser = null;
    if(trc.getForcedUser() != null) {
      jwtUser = trc.getForcedUser();
    } else {
      jwtUser = "AutoUser-"+clientID;
    }
    
    if(trc.getIgnoreJWT()) {
      log.info("{}: Config marked to skip JWT Auth, skipping!", clientID);
      authLF = ImmediateResultListenableFuture.BOOLEAN_TRUE_RESULT;
    } else {
      log.info("{}: Processing JWT", clientID);
      try {
        DecodedJWT djwt = ju.getJWT(httpRequest);
        hasScopes = ju.checkScopes(trc.getRequiredScopes(), false, djwt);
        if(hasScopes) {
          authLF = ju.validateJWT(clientID, djwt);
          if(trc.getForcedUser() == null) {
            if(trc.getUserClaim() != null) {
              Claim tmp = djwt.getClaim(trc.getUserClaim());
              if(!tmp.isNull() && !tmp.asString().equals("")) {
                jwtUser = tmp.asString();
              }
            }
          }
        } else {
          log.info("{}: JWT missing required scopes:{}", clientID, trc.getRequiredScopes());
          authLF = ImmediateResultListenableFuture.BOOLEAN_FALSE_RESULT;
        }
      } catch(Exception e) {
        log.info("{}: Exception processing auth\n{}", clientID, ExceptionUtils.stackToString(e));
        slf.setResult(new SimpleResponse(HTTPUtils.getUnauthorizedResponse()));
        return slf;
      }
    }

    final String JWTUser = jwtUser;
    authLF.callback(new FutureCallback<Boolean>() {
      @Override
      public void handleResult(Boolean result) {
        SimpleResponse sr = null;
        if(result) {
          log.info("{}: Set User to:{}", clientID, JWTUser);
          ByteBuffer bb = ByteBuffer.wrap(TurnRestResponse.makeResponse(trc, JWTUser).toString().getBytes());
          sr = new SimpleResponse(HTTPUtils.getOKResponse().makeBuilder()
              .setHeader(HTTPConstants.HTTP_KEY_CONTENT_LENGTH, Integer.toString(bb.remaining()))
              .build()
              , bb);
        } else {
          sr = new SimpleResponse(HTTPUtils.getUnauthorizedResponse());
        }
        log.info("{}: sending back code:{}", clientID, sr.getHr().getResponseCode());
        slf.setResult(sr);
      }

      @Override
      public void handleFailure(Throwable t) {
        slf.setFailure(t);
      }
    });
 
    return slf;
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
