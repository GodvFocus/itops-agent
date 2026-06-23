package com.itops.itopsagent.service.harness;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class InMemoryTicketExecutionLockService implements TicketExecutionLockService {

    private final ConcurrentHashMap<String, String> locks = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String ticketId, String owner) {
        return locks.putIfAbsent(ticketId, owner) == null;
    }

    @Override
    public void release(String ticketId, String owner) {
        locks.remove(ticketId, owner);
    }
}
