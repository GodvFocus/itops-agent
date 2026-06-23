package com.itops.itopsagent.entity.enums;

public enum ToolCallStatus {
    QUEUED,
    PENDING_APPROVAL,
    REJECTED,
    SUCCESS,
    FAILED,
    RETRYING,
    DEAD_LETTER,
    DUPLICATE,
    SKIPPED
}
