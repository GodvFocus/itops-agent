package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.AgentStepLogResponse;
import com.itops.itopsagent.entity.enums.AgentStepStatus;
import java.util.List;
import java.util.Map;

public interface AgentStepLogService {

    void record(String ticketId, String nodeName, String inputContextHash, Map<String, Object> output, AgentStepStatus status, String errorMessage);

    List<AgentStepLogResponse> listLogs(String ticketId);
}
