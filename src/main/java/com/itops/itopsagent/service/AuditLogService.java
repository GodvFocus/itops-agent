package com.itops.itopsagent.service;

import java.util.Map;

public interface AuditLogService {

    void record(
            String ticketId,
            String actorType,
            String actorId,
            String action,
            String targetType,
            String targetId,
            Map<String, Object> detail);
}
