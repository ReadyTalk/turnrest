package com.ecovate.rtc.turn.stats;

import org.threadly.litesockets.SocketExecuter;

import com.ecovate.rtc.turn.TurnRest;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;

public class NetworkMetrics {
  
  private static final Counter bytesRead = new Counter.Builder()
      .help("Total number of Bytes Read").name(TurnRest.TURN_REST+"total_bytes_read").create();
  private static final Counter bytesWrite = new Counter.Builder()
      .help("Total number of Bytes Written").name(TurnRest.TURN_REST+"total_bytes_write").create();
  private static volatile SocketExecuter SE = null;
  private static volatile long lastRead = 0;
  private static volatile long lastWrite = 0;
  
  public static void registerNetworkMetrics(SocketExecuter se) {
    registerNetworkMetrics(se, CollectorRegistry.defaultRegistry);
  }
  
  public static void registerNetworkMetrics(SocketExecuter se, CollectorRegistry cr) {
    SE = se;
    bytesRead.register(cr);
    bytesWrite.register(cr);
  }
  
  public static void updateNetworkMetrics() {
    if(SE != null) {
      long newRead = SE.getStats().getTotalRead();
      long newWrite = SE.getStats().getTotalWrite();
      bytesRead.inc(newRead-lastRead);
      bytesWrite.inc(newWrite-lastWrite);
      lastRead = newRead;
      lastWrite = newWrite;
    }
  }
}
