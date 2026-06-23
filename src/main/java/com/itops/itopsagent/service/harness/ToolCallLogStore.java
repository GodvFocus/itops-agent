package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.ToolCallLog;
import java.util.List;

public interface ToolCallLogStore {

    void save(ToolCallLog log);

    List<ToolCallLog> findByTicketId(String ticketId);
}
