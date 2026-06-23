package com.itops.itopsagent.service.harness;

import com.itops.itopsagent.entity.ToolCallLog;
import com.itops.itopsagent.mapper.ToolCallLogMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MyBatisToolCallLogStore implements ToolCallLogStore {

    private final ToolCallLogMapper toolCallLogMapper;

    @Override
    public void save(ToolCallLog log) {
        toolCallLogMapper.insert(log);
    }

    @Override
    public List<ToolCallLog> findByTicketId(String ticketId) {
        return toolCallLogMapper.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }
}
