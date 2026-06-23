package com.itops.itopsagent.service.harness;

public interface TicketExecutionLockService {

    boolean tryAcquire(String ticketId, String owner);

    void release(String ticketId, String owner);
}
