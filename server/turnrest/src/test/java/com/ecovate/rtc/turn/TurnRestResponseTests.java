package com.ecovate.rtc.turn;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.junit.Test;
import org.threadly.litesockets.protocols.http.request.HTTPRequestBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;

import com.auth0.jwk.GuavaCachedJwkProvider;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ecovate.rtc.turn.TurnRest.ClientID;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.CompressionCodecs;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwt;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;

public class TurnRestResponseTests {


  @Test
  public void simpleJWTTest() throws JwkException, MalformedURLException {
    KeyPair k = Keys.keyPairFor(SignatureAlgorithm.RS256);
    PublicKey pubK = k.getPublic();
    PrivateKey priKey = k.getPrivate();
    
    String jws = Jwts.builder().setSubject("Bob").signWith(priKey).compact();
    KeyPair kb = Keys.keyPairFor(SignatureAlgorithm.RS256);
    PublicKey pubKeyBad = kb.getPublic();
    PrivateKey priKeyBad = kb.getPrivate();
    
    
    ClientID cid = new ClientID();
    String[] keys = new String[] {Base64.getEncoder().encodeToString(pubKeyBad.getEncoded()), Base64.getEncoder().encodeToString(pubK.getEncoded())};
    String[] bkeys = new String[] {Base64.getEncoder().encodeToString(pubKeyBad.getEncoded())};

    TurnRestConfig trc = new TurnRestConfig(null, false, null, null, null, keys, null, null, 15000L);
    TurnRestConfig btrc = new TurnRestConfig(null, false, null, null, null, bkeys, null, null, 15000L);
    assertTrue(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws).buildHTTPRequest()) != null);
    assertTrue(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, jws).buildHTTPRequest()) != null);
    assertTrue(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().appendQuery("jwt", jws).buildHTTPRequest()) != null);
    assertFalse(Utils.validateHTTPJWT(cid, btrc, new HTTPRequestBuilder().setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "Bearer "+jws).buildHTTPRequest()) != null);
    assertFalse(Utils.validateHTTPJWT(cid, btrc, new HTTPRequestBuilder().appendQuery("jwt", jws).buildHTTPRequest()) != null);
    assertFalse(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().appendQuery("jwt2", jws).buildHTTPRequest()) != null);
    assertFalse(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().setHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION, "problem: "+jws).buildHTTPRequest()) != null);
    assertFalse(Utils.validateHTTPJWT(cid, trc, new HTTPRequestBuilder().buildHTTPRequest()) != null);
  }
}

