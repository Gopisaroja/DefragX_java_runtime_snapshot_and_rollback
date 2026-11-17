package com.defragx;

import java.time.LocalDateTime;

public class IssueDetector {
    private String issueType = "None";
    private String description = "No issue detected";
    private String severity = "INFO";
    private Issue lastIssue;

    public Issue detectIssue(RuntimeMetrics metrics) {
        if (metrics == null || !metrics.isAbnormal()) return null;
        issueType = metrics.memoryUsage() >= 90 ? "HIGH_MEMORY" : metrics.cpuUsage() >= 0.95 ? "HIGH_CPU" : "THREAD_SPIKE";
        description = metrics.getMetrics(); severity = "HIGH";
        lastIssue = new Issue(issueType, description, severity, LocalDateTime.now());
        return lastIssue;
    }
    public void logIssue(Issue issue) { if (issue != null) lastIssue = issue; }
    public String getIssueDetails() { return lastIssue == null ? description : lastIssue.toString(); }
}
