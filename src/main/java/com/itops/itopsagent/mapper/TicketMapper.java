package com.itops.itopsagent.mapper;

import com.itops.itopsagent.entity.Ticket;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TicketMapper extends JpaRepository<Ticket, String> {
    List<Ticket> findAllByOrderByCreatedAtDesc();
}
