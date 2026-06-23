package com.itops.itopsagent.service.harness;

import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.springframework.stereotype.Service;

@Service
public class InMemoryRabbitMqToolTaskQueue implements ToolTaskQueue {

    private final Queue<ToolExecutionTask> pendingQueue = new ConcurrentLinkedQueue<>();
    private final Queue<ToolExecutionTask> deadLetterQueue = new ConcurrentLinkedQueue<>();

    @Override
    public void publish(ToolExecutionTask task) {
        pendingQueue.offer(task);
    }

    @Override
    public ToolExecutionTask poll() {
        return pendingQueue.poll();
    }

    @Override
    public void moveToDeadLetter(ToolExecutionTask task) {
        deadLetterQueue.offer(task);
    }

    @Override
    public int pendingCount() {
        return pendingQueue.size();
    }

    @Override
    public List<ToolExecutionTask> deadLetters() {
        return deadLetterQueue.stream().toList();
    }
}
