package com.ecovate.rtc.turn;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class TurnRestConfig {

  public static final long DEFAULT_TTL = 60*60*12; //12 hours

  private final String secretKey;
  private final String allowedOrigin;
  private final Boolean ignoreJWT;
  private final String forcedUser;
  private final String forcedPassword;
  private final String[] turnURIS;
  private final String[] stunURIS;
  private final String[] jwkURLs;
  private final String[] jwtPublicKeys;
  private final String[] requiredJWTScope;
  private final String userClaim;
  private final Long ttl;


  private transient volatile String json;

  public TurnRestConfig(String secretKey, Boolean ignoreJWT, 
      String[] turnURIS, String[] stunURIS, String[] jwkURLs, String[] jwtPublicKeys, String[] requiredJWTScope, 
      String userClaim, Long ttl) {
    this(secretKey, ignoreJWT, turnURIS, stunURIS, jwkURLs, jwtPublicKeys, requiredJWTScope, userClaim, ttl, null, null, null);
  }
  
  public TurnRestConfig(String secretKey, Boolean ignoreJWT, 
      String[] turnURIS, 
      String[] stunURIS,
      String[] jwkURLs,
      String[] jwtPublicKeys,
      String[] requiredJWTScope,
      String userClaim,
      Long ttl, String forcedUser, 
      String forcedPassword,
      String allowedOrigin) {
    this.secretKey = secretKey;
    this.ignoreJWT = ignoreJWT;
    this.jwkURLs = jwkURLs;
    this.jwtPublicKeys = jwtPublicKeys;
    this.userClaim = userClaim;
    this.turnURIS = turnURIS;
    this.stunURIS = stunURIS;
    this.ttl = ttl;
    this.forcedUser = forcedUser;
    this.forcedPassword = forcedPassword;
    this.allowedOrigin = allowedOrigin;
    this.requiredJWTScope = requiredJWTScope;
  }
  
  public String getAllowedOrigin() {
    return allowedOrigin;
  }

  public String getSecretKey() {
    return secretKey;
  }
  
  public String getUserClaim() {
    return userClaim;
  }

  public Boolean getIgnoreJWT() {
    if(ignoreJWT == null) {
      return false;
    }
    return ignoreJWT;
  }

  public List<String> getTURNURIs() {
    if(this.turnURIS == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(turnURIS));
  }
  
  public List<String> getSTUNURIs() {
    if(this.stunURIS == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(stunURIS));
  }
  
  public List<String> getJwkURLs() {
    if(this.jwkURLs == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(this.jwkURLs));
  }

  public List<String> getJwtPublicKeys() {
    if(this.jwtPublicKeys == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(this.jwtPublicKeys));
  }
  
  public List<String> getRequiredScopes() {
    if(this.requiredJWTScope == null) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(Arrays.asList(this.requiredJWTScope));
  }

  public long getTTL() {
    if(ttl == null) {
      return DEFAULT_TTL;
    }
    return ttl;
  }
  
  public String getForcedUser() {
    return forcedUser;
  }

  public String getForcedPassword() {
    return forcedPassword;
  }


  @Override
  public String toString() {
    if(json == null) {
      json = Utils.GSON.toJson(this);
    }
    return json;
  }

  public int hashCode() {
    return toString().hashCode()+5;
  }
  
  public static TurnRestConfig openConfigFile(final File cf) throws IOException {
    RandomAccessFile raf = new RandomAccessFile(cf, "r");
    try {
      byte[] ba = new byte[(int)raf.length()];
      raf.read(ba);
      String s = new String(ba);
      return Utils.GSON.fromJson(s, TurnRestConfig.class);
    } finally {
      raf.close();
    }
  }
}
