package com.ecovate.rtc.turn;

import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.concurrent.future.FutureUtils;
import org.threadly.concurrent.future.ListenableFuture;
import org.threadly.concurrent.future.SettableListenableFuture;
import org.threadly.litesockets.protocols.http.request.HTTPRequest;
import org.threadly.litesockets.protocols.http.shared.HTTPConstants;
import org.threadly.util.AbstractService;
import org.threadly.util.Clock;

import com.auth0.jwk.GuavaCachedJwkProvider;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.ecovate.rtc.turn.TurnRest.ClientID;

import io.jsonwebtoken.Jwts;

public class JWTUtils extends AbstractService {
  private static final Logger log = LoggerFactory.getLogger(JWTUtils.class);

  private final ConcurrentHashMap<String, Long> cachedJWTs = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, GuavaCachedJwkProvider> jwkProviders = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, PublicKey> staticJWTKeys = new ConcurrentHashMap<>();
  private final Runnable watchCache = ()->{
    List<String> expiredCache = new ArrayList<>();
    for(String s: cachedJWTs.keySet()) {
      if(Clock.lastKnownForwardProgressingMillis() - cachedJWTs.get(s) >= 300000) {
        expiredCache.add(s);
      }
    }
    for(String s: cachedJWTs.keySet()) {
      cachedJWTs.remove(s);
    }
  };
  private final PriorityScheduler ps; 
  
  
  public JWTUtils(PriorityScheduler ps) {
    this.ps = ps;
  }

  public void reset() {
    clearJWTCache();
    clearJWKProviders();
    clearStaticKey();
  }

  public void clearJWTCache() {
    cachedJWTs.clear();
  }

  public void addJWKProvider(String jwkProvider) {
    try {
      GuavaCachedJwkProvider gcjwkp = new GuavaCachedJwkProvider(new UrlJwkProvider(new URL(jwkProvider)));
      
      if(jwkProviders.putIfAbsent(jwkProvider, gcjwkp) == null) {
        log.info("Added new JWK endpoint:{}",jwkProvider);
      }
    } catch (MalformedURLException e) {
      log.error("Could not make a URL from:{}", jwkProvider, e);
    }
  }
  
  public void updateJWKProvider(Set<String> jwkUrls) {
    for(String jwkUrl: jwkUrls) {
      addJWKProvider(jwkUrl);
    }
    Set<String> currentJWKs = getJWKProviders();
    currentJWKs.removeAll(jwkUrls);
    for(String jwkUrl: currentJWKs) {
      removeJWKProvider(jwkUrl);
    }
  }

  public void removeJWKProvider(String jwkProvider) {
    if(jwkProviders.remove(jwkProvider) != null) {
      log.info("Removed JWK endpoint:{}", jwkProvider);
    }
  }

  public void clearJWKProviders() {
    for(String jwk: getJWKProviders()) {
      removeJWKProvider(jwk);
    }
  }

  public Set<String> getJWKProviders() {
    return new HashSet<>(jwkProviders.keySet());
  }

  public String hashPublicKey(String key) {
    return Utils.SHABytes(Base64.getDecoder().decode(key));
  }
  
  public String hashPublicKey(PublicKey key) {
    return Utils.SHABytes(key.getEncoded());
  }

  public void updateStaticB64Keys(Set<String> keys) {
    HashMap<String, String> hashKeys = new HashMap<>();
    for(String key: keys) {
      hashKeys.put(this.hashPublicKey(key), key);
      addStaticKey(key);
    }
    
    Set<String> currentKeys = getStaticKeys();
    
    currentKeys.removeAll(hashKeys.keySet());
    for(String leftKeys: currentKeys) {
      removeStaticKey(leftKeys);
    }
  }
  
  public void updateStaticPublicKeys(Set<PublicKey> keys) {
    HashMap<String, PublicKey> hashKeys = new HashMap<>();
    for(PublicKey key: keys) {
      hashKeys.put(hashPublicKey(key), key);
      addStaticKey(key);
    }
    Set<String> currentKeys = getStaticKeys();
    currentKeys.removeAll(hashKeys.keySet());
    for(String leftKeys: currentKeys) {
      removeStaticKey(leftKeys);
    }
  }
  
  public void addStaticKey(String key) {
    try {
      byte[] ba = Base64.getDecoder().decode(key);
      String sha = Utils.SHABytes(ba);
      PublicKey x = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(ba));
      if(staticJWTKeys.putIfAbsent(sha, x) == null) {
        log.info("Added static key:{}", sha);
      }
    } catch (Exception e) {
      log.error("Could not make a X509EncodedKey from :\n\n{}\n", key);
      log.error("", e);
    }
  }
  
  public void addStaticKey(PublicKey key) {
    try {
      String sha = Utils.SHABytes(key.getEncoded());
      if(staticJWTKeys.putIfAbsent(sha, key)==null) {
        log.info("Added static key:{}", sha);
      }
    } catch (Exception e) {
      log.error("Could not make a X509EncodedKey from :\n\n{}\n", key);
      log.error("", e);
    }
  }

  public void removeStaticKey(String key) {
    if(staticJWTKeys.remove(key) != null) {
      log.info("Removed StaticKey:"+key);
    } else {
      String sha = Utils.SHABytes(Base64.getDecoder().decode(key));
      if(staticJWTKeys.remove(sha) != null) {
        log.info("Removed StaticKey:{}",sha);
      }
    }
  }
  
  public void removeStaticKey(PublicKey key) {
    String sha = Utils.SHABytes(key.getEncoded());
    if(staticJWTKeys.remove(sha) != null) {
      log.info("Removed StaticKey:{}",sha);
    }
  }

  public void clearStaticKey() {
    staticJWTKeys.clear();
  }

  public Set<String> getStaticKeys() {
    return new HashSet<>(staticJWTKeys.keySet());
  }

  public DecodedJWT getJWT(HTTPRequest httpRequest) throws JWTDecodeException {
    String jwtQ = httpRequest.getHTTPRequestHeader().getRequestQueryValue("jwt");
    String jwtAH = httpRequest.getHTTPHeaders().getHeader(HTTPConstants.HTTP_KEY_AUTHORIZATION);
    String jwtClaim = null;
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
        return JWT.decode(jwtClaim);
      } catch(Exception e) {
        throw new JWTDecodeException(jwtClaim, e);
      }
    } else {
      throw new JWTDecodeException("No JWT found");
    }
  }
  
  public boolean checkScopes(final Collection<String> requiredScopes, boolean requireAll, final DecodedJWT djwt) {
    if(requiredScopes == null || requiredScopes.size() == 0) {
      return true;
    }
    List<String> scopes = djwt.getClaim("scp").asList(String.class);
    List<String> scopes2 = djwt.getClaim("scopes").asList(String.class);
    List<String> allScopes = new ArrayList<>();
    if(scopes != null) {
      allScopes.addAll(scopes);
    }
    if(scopes2 != null) {
      allScopes.addAll(scopes2);
    }
    int ts = 0;
    for(String s: allScopes) {
      if(requiredScopes.contains(s)) {
        ts++;
        if(!requireAll) {
          return true;
        }
      }
    }
    return requiredScopes.size() == ts;
  }

  public boolean validateJWT(final ClientID clientID, final DecodedJWT djwtFinal) {
    final String jwtString = djwtFinal.getToken();
    final String keyID = djwtFinal.getKeyId();
    final String tokenSha = Utils.SHAString(jwtString);

    Long time = cachedJWTs.get(tokenSha);
    if(time != null && Clock.lastKnownForwardProgressingMillis() < time+300000) {
      log.info("{}: JWT in valid JWT cache:{}", clientID, jwtString);
      return true;
    }
    
    List<ListenableFuture<DecodedJWT>> llf = new ArrayList<>();
    for(Map.Entry<String, GuavaCachedJwkProvider> me: jwkProviders.entrySet()) {
      final GuavaCachedJwkProvider jwkCache = me.getValue();
      final String url = me.getKey();
      final SettableListenableFuture<DecodedJWT> slf = new SettableListenableFuture<>(false);
      llf.add(slf);
      Utils.getScheduler().execute(()->{
        try {
          Jwk jwk = jwkCache.get(keyID);
          checkJWT(clientID, url, jwk.getPublicKey(), djwtFinal);
          slf.setResult(djwtFinal);
        } catch (Exception e) {
          slf.setFailure(e);
        }
      });
    }
    for(Map.Entry<String, PublicKey>me: staticJWTKeys.entrySet()) {
      final SettableListenableFuture<DecodedJWT> slf = new SettableListenableFuture<>(false);
      llf.add(slf);
      Utils.getScheduler().execute(()->{
        try {
          checkJWT(clientID, me.getKey(), me.getValue(), djwtFinal);
          log.info("{}: Found static public key:\"{}\" for JWT:\n{}", clientID, me.getKey(), jwtString);
          slf.setResult(djwtFinal);
        } catch(Exception e) {
          slf.setFailure(e);
        }
      });
    }
    ListenableFuture<DecodedJWT> lf = FutureUtils.makeFirstResultFuture(llf,true, false);
    Utils.getSocketExecuter().watchFuture(lf, 10000);
    try {
      lf.get();
      cachedJWTs.put(tokenSha, Clock.lastKnownForwardProgressingMillis());
      return true;
    } catch(Exception e) {
      StringBuilder sb = new StringBuilder();
      for(ListenableFuture<DecodedJWT> nlf: llf) {
        try {
          nlf.get();
        } catch(Exception e2) {        
          if(e2.getCause() != null) {
            sb.append(e2.getCause());
            if(e2.getCause().getCause() != null) {
            sb.append("\n\tCaused By: ");
            sb.append(e2.getCause().getCause());
            }
          }
          sb.append("\n");
        }
      }
      log.error("{}: Could not find valid JWT:\nJWT:{}\nErrors:\n{}", clientID, jwtString, sb.toString());
      return false;
    }
  }

  private boolean checkJWT(final ClientID cid, final String kid, final PublicKey pk, final DecodedJWT claim) throws JWTValidateException {
    try {
      Jwts.parser().setSigningKey(pk).parseClaimsJws(claim.getToken());
      return true;
    } catch (Exception e) {
      throw new JWTValidateException("KID:"+kid, e);
    }
  }

  public static class JWTValidateException extends Exception {

    private static final long serialVersionUID = -4667802009570530L;

    public JWTValidateException() {
      super();
    }
    public JWTValidateException(String s) {
      super(s);
    }
    public JWTValidateException(String s, Exception e) {
      super(s, e);
    }
    public JWTValidateException(Exception e) {
      super(e);
    }
  }

  public static class JWTDecodeException extends Exception {

    private static final long serialVersionUID = -4667802002719570530L;

    public JWTDecodeException() {
      super();
    }
    public JWTDecodeException(String s) {
      super(s);
    }
    public JWTDecodeException(String s, Exception e) {
      super(s, e);
    }
    public JWTDecodeException(Exception e) {
      super(e);
    }
  }

  @Override
  protected void startupService() {
    ps.scheduleAtFixedRate(this.watchCache, 30000, 30000);
  }

  @Override
  protected void shutdownService() {
    ps.remove(this.watchCache);
  }
}
