package com.defragx;

import java.time.LocalDateTime;

public record RuntimeMetrics(LocalDateTime timestamp, long heapUsedBytes, long heapMaxBytes,
                             double memoryUsage, double cpuUsage, int threadCount, String gcInfo) {
    public String getMetrics() {
        return String.format("Memory %.1f%% | CPU load %.3f (0–1) | Threads %d | GC %s",
                memoryUsage, cpuUsage, threadCount, gcInfo);
    }
    public boolean isAbnormal() { return memoryUsage >= 90 || cpuUsage >= 0.95 || threadCount >= 500; }
}
