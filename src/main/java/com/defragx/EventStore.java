package com.defragx;

import java.util.List;

public interface EventStore {
    void saveEvent(TransactionEvent event) throws Exception;
    List<TransactionEvent> listEvents() throws Exception;
}
