package com.defragx;

public class DefragXAgent {
    private final String appName;
    private final RuntimeMonitor monitor;
    private String agentStatus="STOPPED";
    public DefragXAgent(String appName) { this.appName=appName; this.monitor=new RuntimeMonitor(); }
    public void startMonitoring() { agentStatus="MONITORING"; monitor.collectMetrics(); }
    public void stopMonitoring() { agentStatus="STOPPED"; }
    public String getStatus() { return agentStatus; }
    public String getAppName() { return appName; }
    public RuntimeMonitor getMonitor() { return monitor; }
}
