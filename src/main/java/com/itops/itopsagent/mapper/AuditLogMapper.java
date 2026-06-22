package com.itops.itopsagent.mapper;

import com.itops.itopsagent.entity.AuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogMapper extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
