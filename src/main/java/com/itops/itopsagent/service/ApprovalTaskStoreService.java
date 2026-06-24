package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.ApprovalTaskResponse;
import com.itops.itopsagent.dto.CandidatePlanRequest;
import java.util.List;
import java.util.Map;

public interface ApprovalTaskStoreService {

    ApprovalTaskResponse createPendingTask(String ticketId, CandidatePlanRequest plan, String requestedReason, List<Map<String, Object>> approvalSteps);

    List<ApprovalTaskResponse> listAll();

    List<ApprovalTaskResponse> listByTicketId(String ticketId);

    ApprovalTaskResponse getByApprovalId(String approvalId);

    ApprovalTaskResponse markApproved(String approvalId, String approverId, String comment);

    ApprovalTaskResponse markRejected(String approvalId, String approverId, String comment);

    CandidatePlanRequest getPlan(String approvalId);
}
