package com.ecovate.rtc.turn;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.response.HTTPResponse;
import org.threadly.litesockets.protocols.http.response.HTTPResponseBuilder;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.litesockets.protocols.http.shared.HTTPResponseCode;
import org.threadly.util.StringUtils;

import com.auth0.jwk.GuavaCachedJwkProvider;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.ecovate.rtc.turn.TurnRest.ClientID;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  public static final String HTTP_ACAO_HEADER = "Access-Control-Allow-Origin";
  public static final String HTTP_ACAM_HEADER = "Access-Control-Allow-Methods";
  public static final String HTTP_ACAH_HEADER = "Access-Control-Allow-Headers";
  public static final String HTTP_CACHE_HEADER = "Cache-Control";
  private static final ConcurrentHashMap<String, GuavaCachedJwkProvider> jwkProviders = new ConcurrentHashMap<>();

  private static volatile MetricRegistry mr;
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

  public static void setJWKProviders(HashSet<String> newProviders) {
    Set<String> oldProviders = jwkProviders.keySet();
    Set<String> missingProviders = new HashSet<>(newProviders);
    Set<String> removedProviders = new HashSet<>(oldProviders);
    missingProviders.removeAll(oldProviders);
    removedProviders.removeAll(newProviders);
    for(String x: missingProviders) {
      try {
        jwkProviders.putIfAbsent(x, new GuavaCachedJwkProvider(new UrlJwkProvider(new URL(x))));
      } catch (MalformedURLException e) {
        log.error("Could not make a URL from :"+x, e);
      }
    }
    for(String x: removedProviders) {
      jwkProviders.remove(x);
    }
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

  public static MetricRegistry getMetricsRegistry() {
    if(mr == null) {
      synchronized(Utils.class) {
        if(mr == null) {
          mr = new MetricRegistry();
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

  public static String makeUserName() {
    return "user-"+StringUtils.makeRandomString(16);
  }

  public static boolean checkJWT(ClientID cid, byte[] key, String claim) throws JwtException {
    try {
      PublicKey pk = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(key));
      Jwts.parser().setSigningKey(pk).parseClaimsJws(claim);
      return true;
    } catch (Exception e) {
      log.info("{}:Exception validating JWT:{}", cid, e.getMessage());
      log.debug("Exception validating JWT:", e);
      return false;
    }
  }

  public static DecodedJWT validateHTTPJWT(ClientID clientID, TurnRestConfig trc, HTTPRequest httpRequest) {
    String jwtQ = httpRequest.getHTTPRequestHeader().getRequestQueryValue("jwt");
    String jwtAH = httpRequest.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION);
    String jwtClaim = null;
    DecodedJWT djwt = null;
    String kid = null;
    if(jwtAH != null) {
      if(jwtAH.toLowerCase().startsWith("bearer ")) {
        jwtClaim = jwtAH.split(" ")[1];
      } else {
        jwtClaim = jwtAH;
      }
    } else if(jwtQ != null) {
      jwtClaim = jwtQ;
    }
    if(jwtClaim != null) {
      try {
        djwt = JWT.decode(jwtClaim);
        kid = djwt.getKeyId();
      } catch (Exception e) {
        log.info("Exception decoding JWT:{}", e.getMessage());
        log.debug("Exception validating JWT:", e);
      }
    }
    if (djwt != null) {
      for(Map.Entry<String, GuavaCachedJwkProvider> me: jwkProviders.entrySet()) {
        try {
          me.getValue().get(kid);
          log.info("{}: Found provider:{} for JWT", clientID, me.getKey());
          return djwt;
        } catch (JwkException e) {
        }
      }
      if(trc.getJwtPublicKeys().size() > 0) {
        log.info("{}: Could not find JWK for JWT, trying static public keys");
      }

      for(String key: trc.getJwtPublicKeys()) {
        if(Utils.checkJWT(clientID, Base64.getDecoder().decode(key), jwtClaim)) {
          log.info("{}: Found static public key for JWT:\"{}\" for JWT", clientID, key);
          return djwt;
        } else {
          log.error("{}: Not valid:\nkey:\"{}\"\nJWT:\"{}\"", clientID, key, jwtClaim);
        }
      }
      log.info("{}: Not a valid JWT:\"{}\"", clientID, jwtClaim);
      return null;
    } else {
      log.error("{}: No JWT found!", clientID);
      return null;
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
