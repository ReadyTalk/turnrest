package com.ecovate.rtc.turn.stats;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;

public class MemoryMetrics {
  private static final String JVM_PREFIX = "jvm_";
  

  private static final Gauge jvmHeapMemoryInit = new Gauge.Builder()
      .help("Current JVM Heap Memory Init").name(JVM_PREFIX+"heap_memory_init").create();
  private static final Gauge jvmHeapMemoryMax = new Gauge.Builder()
      .help("Current JVM Heap Memory Max").name(JVM_PREFIX+"heap_memory_max").create();
  private static final Gauge jvmHeapMemoryUsed = new Gauge.Builder()
      .help("Current JVM Heap Memory Used").name(JVM_PREFIX+"heap_memory_used").create();
  private static final Gauge jvmHeapMemoryCommited = new Gauge.Builder()
      .help("Current JVM Heap Memory Commited").name(JVM_PREFIX+"heap_memory_commited").create();
  
  private static final Gauge jvmNonHeapMemoryInit = new Gauge.Builder()
      .help("Current JVM NonHeap Memory Init").name(JVM_PREFIX+"non_heap_memory_init").create();
  private static final Gauge jvmNonHeapMemoryMax = new Gauge.Builder()
      .help("Current JVM NonHeap Memory Max").name(JVM_PREFIX+"non_heap_memory_max").create();
  private static final Gauge jvmNonHeapMemoryUsed = new Gauge.Builder()
      .help("Current JVM NonHeap Memory Used").name(JVM_PREFIX+"non_heap_memory_used").create();
  private static final Gauge jvmNonHeapMemoryCommited = new Gauge.Builder()
      .help("Current JVM NonHeap Memory Commited").name(JVM_PREFIX+"non_heap_memory_commited").create();
  
  
  private static final MemoryMXBean mb = ManagementFactory.getMemoryMXBean();
  
  public static void registerMemoryMetrics() {
    registerMemoryMetrics(CollectorRegistry.defaultRegistry);
  }
  
  public static void registerMemoryMetrics(CollectorRegistry cr) {
    jvmHeapMemoryMax.register(cr);
    jvmHeapMemoryInit.register(cr);
    jvmHeapMemoryUsed.register(cr);
    jvmHeapMemoryCommited.register(cr);
    
    jvmNonHeapMemoryInit.register(cr);
    jvmNonHeapMemoryMax.register(cr);
    jvmNonHeapMemoryUsed.register(cr);
    jvmNonHeapMemoryCommited.register(cr);
  }
  
  public static void updateMemoryStats() {
    jvmHeapMemoryInit.set(mb.getHeapMemoryUsage().getInit());
    jvmHeapMemoryMax.set(mb.getHeapMemoryUsage().getMax());
    jvmHeapMemoryUsed.set(mb.getHeapMemoryUsage().getUsed());
    jvmHeapMemoryCommited.set(mb.getHeapMemoryUsage().getCommitted());

    jvmNonHeapMemoryInit.set(mb.getNonHeapMemoryUsage().getInit());
    jvmNonHeapMemoryMax.set(mb.getNonHeapMemoryUsage().getMax());
    jvmNonHeapMemoryUsed.set(mb.getNonHeapMemoryUsage().getUsed());
    jvmNonHeapMemoryCommited.set(mb.getNonHeapMemoryUsage().getCommitted());
  }
}
