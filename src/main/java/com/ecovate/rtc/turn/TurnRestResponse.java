package com.ecovate.rtc.turn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.threadly.util.Clock;

public class TurnRestResponse {
  private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

  private final String username;
  private final String password;
  private final long ttl;
  private final IceServers[] iceServers;
  private transient volatile String json;

  TurnRestResponse(String un, String pwd, long ttl, IceServers[] iceServers) {
    this.username = un;
    this.password = pwd;
    this.ttl = ttl;
    this.iceServers = iceServers;
  }

  TurnRestResponse(String un, String pwd, long ttl, List<IceServers> iceServers) {
    this.username = un;
    this.password = pwd;
    this.ttl = ttl;
    IceServers[] sa = new IceServers[iceServers.size()];
    this.iceServers = iceServers.toArray(sa);
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public long getTtl() {
    return ttl;
  }

  public List<IceServers> getIceServers() {
    return Collections.unmodifiableList(Arrays.asList(iceServers));
  }
  @Override
  public String toString() {
    if(json == null) {
      json = Utils.GSON.toJson(this);
    }
    return json;
  }
  @Override
  public int hashCode() {
    return toString().hashCode()+5;
  }

  public static TurnRestResponse makeResponse(TurnRestConfig trc) {
    return makeResponse(trc, null, 0);
  }

  public static TurnRestResponse makeResponse(TurnRestConfig trc, String username) {
    return makeResponse(trc, username, 0);
  }

  public static TurnRestResponse makeResponse(TurnRestConfig trc, int ttl) {
    return makeResponse(trc, null, ttl);
  }

  public static TurnRestResponse makeResponse(TurnRestConfig trc, String username, int ttl) {
    try {
      long nttl = ttl;
      if(ttl <=0) {
        nttl = trc.getTTL();
      }
      long endtime = Clock.lastKnownTimeMillis()/1000 + nttl;
      String un = Utils.makeUserName();
      if(username != null && username.length() > 0) {
        un = username;
      }
      if(trc.getForcedUser() != null) {
        un = trc.getForcedUser();
      }
      String user = null;
      String passwd = null;
      if(trc.getForcedPassword() == null) {
        user = endtime+":"+un;
        SecretKeySpec signingKey = new SecretKeySpec(trc.getSecretKey().getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);

        passwd = Base64.getEncoder().encodeToString(mac.doFinal(user.getBytes()));
      } else {
        user  = trc.getForcedUser();
        passwd = trc.getForcedPassword();
      }
      ArrayList<IceServers> servers = new ArrayList<>();
      IceServers turnservers = new IceServers(trc.getTURNURIs(), user, passwd);
      servers.add(turnservers);
      if(trc.getSTUNURIs().size() > 0) {
        IceServers stunservers = new IceServers(trc.getSTUNURIs());
        servers.add(stunservers);
      }

      TurnRestResponse trr = new TurnRestResponse(user, passwd, nttl, servers);
      return trr;
    } catch(Exception e) {
      throw new RuntimeException(e);
    }
  }
  
  public static class IceServers {
    private final String[] urls;
    private final String username;
    private final String credential;
    private transient volatile String json;
    
    public IceServers(List<String> urls, String username, String credential) {
      String[] sa = new String[urls.size()];
      this.urls = urls.toArray(sa);
      this.username = username;
      this.credential = credential;
    }
    
    public IceServers(String[] urls, String username, String credential) {
      this.urls = urls;
      this.username = username;
      this.credential = credential;
    }
    public IceServers(List<String> urls) {
      String[] sa = new String[urls.size()];
      this.urls = urls.toArray(sa);
      this.username = null;
      this.credential = null;
    }
    public IceServers(String[] urls) {
      this.urls = urls;
      this.username = null;
      this.credential = null;
    }

    public String[] getUrls() {
      return urls;
    }

    public String getUsername() {
      return username;
    }

    public String getCredential() {
      return credential;
    }
    @Override
    public String toString() {
      if(json == null) {
        json = Utils.GSON.toJson(this);
      }
      return json;
    }
    @Override
    public int hashCode() {
      return toString().hashCode()+5;
    }
  }
}
