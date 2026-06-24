package com.itops.itopsagent.dto;

import java.util.List;
import java.util.Map;

public record TicketTimelineResponse(
        Map<String, Object> currentPlan,
        List<String> matchedSopIds,
        List<ApprovalTaskResponse> approvalTasks,
        List<ToolCallLogResponse> toolCalls,
        List<TimelineEventResponse> timelineEvents,
        String resolutionSummary) {
}
