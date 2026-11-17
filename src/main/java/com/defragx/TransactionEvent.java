package com.defragx;

import java.time.LocalDateTime;
import java.util.UUID;

public final class TransactionEvent {
    public final String eventId;
    public final LocalDateTime timestamp;
    public final String accountHolder, accountNumber, amount, status, details;

    public TransactionEvent(String holder, String number, String amount, LocalDateTime timestamp, String status, String details) {
        this(UUID.randomUUID().toString(), holder, number, amount, timestamp, status, details);
    }

    public TransactionEvent(String eventId, LocalDateTime timestamp, String holder, String number, String amount, String status, String details) {
        this(eventId, holder, number, amount, timestamp, status, details);
    }

    private TransactionEvent(String eventId, String holder, String number, String amount, LocalDateTime timestamp, String status, String details) {
        this.eventId=eventId; this.timestamp=timestamp; this.accountHolder=holder; this.accountNumber=number;
        this.amount=amount; this.status=status; this.details=details;
    }
}
