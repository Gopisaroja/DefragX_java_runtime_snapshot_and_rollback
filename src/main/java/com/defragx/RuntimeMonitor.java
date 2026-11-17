package com.defragx;

import java.lang.management.ManagementFactory;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ThreadMXBean;
import java.time.LocalDateTime;
import com.sun.management.OperatingSystemMXBean;

public class RuntimeMonitor {
    private double memoryUsage;
    private double cpuUsage;
    private int threadCount;
    private long previousProcessCpuNanos = -1;
    private long previousCpuSampleNanos = -1;
    private String jvmInfo = System.getProperty("java.vm.name") + " " + System.getProperty("java.version");

    public RuntimeMetrics collectMetrics() {
        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        memoryUsage = runtime.maxMemory() == 0 ? 0 : used * 100.0 / runtime.maxMemory();
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        threadCount = threads.getThreadCount();
        java.lang.management.OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        // Measure the JVM process itself so the dashboard's CPU value describes this application.
        if (os instanceof OperatingSystemMXBean bean) {
            long nowNanos = System.nanoTime();
            long processCpuNanos = bean.getProcessCpuTime();
            double cpuLoad = bean.getProcessCpuLoad();
            if (processCpuNanos >= 0 && previousProcessCpuNanos >= 0 && previousCpuSampleNanos >= 0) {
                long elapsedNanos = nowNanos - previousCpuSampleNanos;
                long cpuDeltaNanos = processCpuNanos - previousProcessCpuNanos;
                cpuUsage = elapsedNanos > 0 && cpuDeltaNanos >= 0
                        ? Math.min(1.0, cpuDeltaNanos / (double) elapsedNanos / runtime.availableProcessors())
                        : Double.NaN;
            } else {
                cpuUsage = cpuLoad >= 0 ? cpuLoad : Double.NaN;
            }
            previousProcessCpuNanos = processCpuNanos;
            previousCpuSampleNanos = nowNanos;
        } else {
            cpuUsage = Double.NaN;
        }
        String gc = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName).reduce((a, b) -> a + ", " + b).orElse("Unavailable");
        return new RuntimeMetrics(LocalDateTime.now(), used, runtime.maxMemory(), memoryUsage, cpuUsage, threadCount, gc);
    }
    public boolean detectAbnormality() { return getMetrics().isAbnormal(); }
    public RuntimeMetrics getMetrics() { return collectMetrics(); }
    public String getJvmInfo() { return jvmInfo; }
}
