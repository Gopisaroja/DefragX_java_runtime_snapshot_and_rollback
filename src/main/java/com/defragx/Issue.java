package com.defragx;

import java.time.LocalDateTime;

public record Issue(String issueType, String description, String severity, LocalDateTime detectedAt) { }
