package com.ecovate.rtc.turn;

import static org.junit.Assert.assertEquals;

import java.net.MalformedURLException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.util.StringUtils;

import com.auth0.jwk.JwkException;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.ecovate.rtc.turn.processors.TurnRestHTTPHandler;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class TurnRestResponseTests {
  


  static Set<KeyPair> ExtraKeyPairs = new HashSet<>();
  static Set<PublicKey> ExtraPublicKeys = new HashSet<>();
  static {
    for(int i=0; i<10; i++) {
      KeyPair tmpkp = Keys.keyPairFor(SignatureAlgorithm.RS256);
      ExtraKeyPairs.add(tmpkp);
      ExtraPublicKeys.add(tmpkp.getPublic());
    }    
  }

  KeyPair goodKeyPair = null;
  KeyPair badKeyPair = null;  
  String userName = null;
  String password = null;
  PriorityScheduler ps = null;
  SocketExecuter se = null;
  JWTUtils ju = null;


  @Before
  public void start() {
    goodKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    ps = new PriorityScheduler(2);
    se = new ThreadedSocketExecuter(ps);
    se.startIfNotStarted();
    badKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);
    userName = StringUtils.makeRandomString(25);
    password = StringUtils.makeRandomString(25);
    ju = new JWTUtils(ps);
    ju.addStaticKey(goodKeyPair.getPublic());
  }
  
  @After
  public void end() {
    ju.reset();
    Utils.resetRegistries();
  }

  @Test
  public void simpleJWTTest() throws Exception {
    TurnRestHTTPHandler handler = new TurnRestHTTPHandler(ju);
    
    String jws = Jwts.builder().setSubject("Bob").signWith(goodKeyPair.getPrivate()).compact();
    HTTPRequest hr = new HTTPRequestBuilder().setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws).buildHTTPRequest();
    TurnRestConfig trc = new TurnRestConfig("12312", false, null, null, null, null, null, null, 15000L, userName, password, "*");
    
    SimpleResponse sr = handler.handleRequest(new ClientID(), hr, trc).get();
    assertEquals(HTTPResponseCode.OK, sr.getHr().getResponseCode());
    byte[] ba = new byte[sr.getBody().remaining()];
    sr.getBody().duplicate().get(ba);
    String sb = new String(ba);
    TurnRestResponse trr = Utils.GSON.fromJson(sb, TurnRestResponse.class);
    assertEquals(userName, trr.getUsername());
    assertEquals(password, trr.getPassword());
  }

}

