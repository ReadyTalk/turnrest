package com.ecovate.rtc.turn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.MalformedURLException;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Base64;
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

import com.auth0.jwk.JwkException;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ecovate.rtc.turn.JWTUtils.JWTDecodeException;
import com.ecovate.rtc.turn.TurnRest.ClientID;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import io.prometheus.client.CollectorRegistry;

public class JWTUtilsTests {


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
  PriorityScheduler ps = null;
  SocketExecuter se = null;
  JWTUtils ju = null;

  @Before
  public void start() {
    goodKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

    badKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256);

    ps = new PriorityScheduler(2);
    se = new ThreadedSocketExecuter(ps);
    se.startIfNotStarted();

    ju = new JWTUtils(ps);
    ju.addStaticKey(goodKeyPair.getPublic());
  }
  
  @After
  public void end() {
    ju.reset();
    ju.stopIfRunning();
    Utils.resetRegistries();
  }

  @Test
  public void goodJWT() throws Exception {
    String jws = Jwts.builder().setSubject("Bob").signWith(goodKeyPair.getPrivate()).compact();

    ClientID cid = new ClientID();

    ju.addStaticKey(goodKeyPair.getPublic());
    DecodedJWT jwsD = JWT.decode(jws);
    
    ju.updateStaticPublicKeys(ExtraPublicKeys);
    ju.addStaticKey(goodKeyPair.getPublic());

    assertTrue(ju.validateJWT(cid, jwsD).get());
    ju.clearJWTCache();
    ju.removeStaticKey(goodKeyPair.getPublic());
    ju.addStaticKey(Base64.getEncoder().encodeToString(goodKeyPair.getPublic().getEncoded()));
    assertTrue(ju.validateJWT(cid, jwsD).get());
    assertTrue(ju.validateJWT(cid, jwsD).get());
  }
  
  
  @Test
  public void badJWT() throws Exception {
    String jws = Jwts.builder().setSubject("Bob").signWith(badKeyPair.getPrivate()).compact();

    ClientID cid = new ClientID();

    ju.addStaticKey(goodKeyPair.getPublic());
    DecodedJWT jwsD = JWT.decode(jws);
    
    ju.updateStaticPublicKeys(ExtraPublicKeys);

    assertFalse(ju.validateJWT(cid, jwsD).get());
    ju.clearJWTCache();
    assertFalse(ju.validateJWT(cid, jwsD).get());
    assertFalse(ju.validateJWT(cid, jwsD).get());
  }

  @Test
  public void testGetJWT() throws JWTDecodeException {
    String jws = Jwts.builder().setSubject("Bob").signWith(goodKeyPair.getPrivate()).compact();

    //header with bearer
    HTTPRequest hr = new HTTPRequestBuilder()
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws)
        .buildHTTPRequest();
    DecodedJWT djwt = ju.getJWT(hr);
    assertEquals(jws, djwt.getToken());

    //header w/o bearer
    hr = new HTTPRequestBuilder()
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, jws)
        .buildHTTPRequest();
    djwt = ju.getJWT(hr);
    assertEquals(jws, djwt.getToken());

    //query
    hr = new HTTPRequestBuilder()
        .setQueryString("?jwt="+jws)
        .buildHTTPRequest();
    djwt = ju.getJWT(hr);
    assertEquals(jws, djwt.getToken());
  }

  @Test
  public void failGetJWT() throws JWTDecodeException {
    HTTPRequest hr = new HTTPRequestBuilder()
        .setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer 123412")
        .buildHTTPRequest();
    try {
      ju.getJWT(hr);
      fail();
    } catch(JWTDecodeException e) {

    }

    hr = new HTTPRequestBuilder()
        .setQueryString("?jwt=12423123")
        .buildHTTPRequest();
    try { 
      ju.getJWT(hr);
      fail();
    } catch(JWTDecodeException e) {

    }
    
    
    hr = new HTTPRequestBuilder()
        .buildHTTPRequest();
    try { 
      ju.getJWT(hr);
      fail();
    } catch(JWTDecodeException e) {

    }

  }
}
