package com.itops.itopsagent.mapper;

import com.itops.itopsagent.entity.TicketStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketStatusHistoryMapper extends JpaRepository<TicketStatusHistory, Long> {
    List<TicketStatusHistory> findByTicketIdOrderByCreatedAtAsc(String ticketId);
}
