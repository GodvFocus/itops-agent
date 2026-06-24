package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.ApprovalDecisionRequest;
import com.itops.itopsagent.dto.ApprovalTaskResponse;
import java.util.List;

public interface ApprovalCommandService {

    List<ApprovalTaskResponse> listApprovals(String ticketId);

    ApprovalTaskResponse approve(String approvalId, ApprovalDecisionRequest request);

    ApprovalTaskResponse reject(String approvalId, ApprovalDecisionRequest request);
}
