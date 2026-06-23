package com.itops.itopsagent.service.harness;

import java.util.List;

public interface ToolTaskQueue {

    void publish(ToolExecutionTask task);

    ToolExecutionTask poll();

    void moveToDeadLetter(ToolExecutionTask task);

    int pendingCount();

    List<ToolExecutionTask> deadLetters();
}
