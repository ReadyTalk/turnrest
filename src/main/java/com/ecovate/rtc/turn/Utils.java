package com.ecovate.rtc.turn;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.threadly.concurrent.PriorityScheduler;
import org.threadly.litesockets.SocketExecuter;
import org.threadly.litesockets.ThreadedSocketExecuter;
import org.threadly.util.StringUtils;

import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.prometheus.client.CollectorRegistry;

public class Utils {
  private static final Logger log = LoggerFactory.getLogger(Utils.class);
  
  public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

  private static volatile PriorityScheduler PS = null;
  
  private static volatile SocketExecuter SE = null;
  
  private static volatile CollectorRegistry mr;
  private static volatile HealthCheckRegistry hcr;
  
  static {

  }

  public static boolean setScheduler(PriorityScheduler ps) {
    if(PS == null) {
      synchronized(Utils.class) {
        if(PS == null) {
          log.info("Setting custom threadpool, threads:{}", ps.getMaxPoolSize());
          PS = ps;
          return true;
        }
      }
    }
    return false;
  }
  
  public static PriorityScheduler getScheduler() {
    if(PS == null) {
      synchronized(Utils.class) {
        if(PS == null) {
          int threads = Math.max(10, Runtime.getRuntime().availableProcessors()*3);
          log.info("Creating default threadpool with {} threads", threads);
          PS = new PriorityScheduler(threads);
        }
      }
    }
    return PS;
  }
  
  public static boolean setSocketExecuter(SocketExecuter se) {
    if(SE == null) {
      synchronized(Utils.class) {
        if(SE == null) {
          log.info("Setting custom SocketExecuter:{}", se);
          SE = se;
          SE.startIfNotStarted();
          return true;
        }
      }
    }
    return false;
  }
  
  public static SocketExecuter getSocketExecuter() {
    if(SE == null) {
      synchronized(Utils.class) {
        if(SE == null) {
          log.info("Creating default SocketExecuter");
          SE = new ThreadedSocketExecuter(getScheduler());
          SE.start();
        }
      }
    }
    return SE;
  }

  public static boolean setMetricsRegistry(CollectorRegistry cr, boolean force) {
    if(mr == null || force) {
      synchronized(Utils.class) {
        if(mr == null || force) {
          mr = cr;
          log.info("Setting custom metrics registry:{}", cr);
          return true;
        }
      }
    }
    return false;
  }
  
  public static CollectorRegistry getMetricsRegistry() {
    if(mr == null) {
      synchronized(Utils.class) {
        if(mr == null) {
          log.info("Creating Default MetricsRegistry");
          mr = new CollectorRegistry();
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
  
  public static void resetRegistries() {
    hcr = null;
    mr.clear();
    mr = null;
  }

  public static String makeUserName() {
    return "user-"+StringUtils.makeRandomString(16);
  }

  public static String SHABytes(byte[] ... b)  {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      for(byte[] ba: b) {
        digest.update(ba);
      }
      return bytesToHex(digest.digest());
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
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
