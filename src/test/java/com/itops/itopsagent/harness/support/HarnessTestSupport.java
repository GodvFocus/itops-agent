package com.itops.itopsagent.harness.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itops.itopsagent.entity.IdempotencyRecord;
import com.itops.itopsagent.entity.ToolCallLog;
import com.itops.itopsagent.entity.enums.TicketStatus;
import com.itops.itopsagent.service.harness.HarnessIdempotencyService;
import com.itops.itopsagent.service.harness.HarnessPlanValidationService;
import com.itops.itopsagent.service.harness.HarnessPolicyEngine;
import com.itops.itopsagent.service.harness.HarnessRiskEvaluator;
import com.itops.itopsagent.service.harness.HarnessTicketStatePort;
import com.itops.itopsagent.service.harness.HarnessToolCallLogService;
import com.itops.itopsagent.service.harness.IdempotencyRecordStore;
import com.itops.itopsagent.service.harness.InMemoryTicketExecutionLockService;
import com.itops.itopsagent.service.harness.MockEnterpriseToolGateway;
import com.itops.itopsagent.service.harness.MyBatisIdempotencyRecordStore;
import com.itops.itopsagent.service.harness.PlanExecutionTracker;
import com.itops.itopsagent.service.harness.TicketExecutionLockService;
import com.itops.itopsagent.service.harness.ToolCallLogStore;
import com.itops.itopsagent.service.harness.ToolGateway;
import com.itops.itopsagent.service.harness.ToolRegistryService;
import com.itops.itopsagent.service.harness.ToolTaskProcessor;
import com.itops.itopsagent.service.harness.ToolTaskQueue;
import com.itops.itopsagent.service.harness.ToolExecutionWorker;
import com.itops.itopsagent.service.harness.InMemoryRabbitMqToolTaskQueue;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class HarnessTestSupport implements AutoCloseable {

    public final FakeHarnessTicketStatePort ticketPort = new FakeHarnessTicketStatePort();
    public final InMemoryIdempotencyRecordStore idempotencyStore = new InMemoryIdempotencyRecordStore();
    public final InMemoryToolCallLogStore logStore = new InMemoryToolCallLogStore();
    public final ToolRegistryService toolRegistryService = new ToolRegistryService();
    public final HarnessRiskEvaluator riskEvaluator = new HarnessRiskEvaluator(toolRegistryService);
    public final HarnessPolicyEngine policyEngine = new HarnessPolicyEngine();
    public final ToolTaskQueue queue = new InMemoryRabbitMqToolTaskQueue();
    public final TicketExecutionLockService lockService = new InMemoryTicketExecutionLockService();
    public final PlanExecutionTracker planExecutionTracker = new PlanExecutionTracker();
    public final Clock clock = Clock.fixed(Instant.parse("2026-06-23T08:00:00Z"), ZoneOffset.UTC);
    public final ObjectMapper objectMapper = new ObjectMapper();
    public final HarnessToolCallLogService logService = new HarnessToolCallLogService(logStore, objectMapper, clock);
    public final HarnessIdempotencyService idempotencyService = new HarnessIdempotencyService(idempotencyStore, objectMapper, clock);
    public final ToolGateway toolGateway;
    public final ToolExecutionWorker worker;
    public final ToolTaskProcessor processor;
    public final HarnessPlanValidationService harnessService;

    public HarnessTestSupport() {
        this(new MockEnterpriseToolGateway());
    }

    public HarnessTestSupport(ToolGateway toolGateway) {
        this.toolGateway = toolGateway;
        this.worker = new ToolExecutionWorker(toolGateway, lockService, idempotencyService, logService, planExecutionTracker, ticketPort);
        this.processor = new ToolTaskProcessor(queue, worker);
        this.harnessService = new HarnessPlanValidationService(
                riskEvaluator,
                policyEngine,
                ticketPort,
                logService,
                queue,
                processor,
                planExecutionTracker);
    }

    @Override
    public void close() {
        processor.shutdown();
    }

    public static final class FakeHarnessTicketStatePort implements HarnessTicketStatePort {

        private final Map<String, TicketStatus> statuses = new ConcurrentHashMap<>();
        private final List<String> transitions = new ArrayList<>();

        public void put(String ticketId, TicketStatus status) {
            statuses.put(ticketId, status);
        }

        @Override
        public TicketStatus getCurrentStatus(String ticketId) {
            return statuses.get(ticketId);
        }

        @Override
        public void transition(String ticketId, TicketStatus targetStatus, String comment) {
            statuses.put(ticketId, targetStatus);
            transitions.add(ticketId + ":" + targetStatus.name() + ":" + comment);
        }

        public List<String> transitions() {
            return transitions;
        }
    }

    public static final class InMemoryIdempotencyRecordStore implements IdempotencyRecordStore {

        private final Map<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

        @Override
        public IdempotencyRecord findByIdemKey(String idemKey) {
            return records.get(idemKey);
        }

        @Override
        public void saveOrUpdate(IdempotencyRecord record) {
            records.put(record.getIdemKey(), record);
        }

        public Map<String, IdempotencyRecord> records() {
            return records;
        }
    }

    public static final class InMemoryToolCallLogStore implements ToolCallLogStore {

        private final List<ToolCallLog> logs = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public void save(ToolCallLog log) {
            logs.add(log);
        }

        @Override
        public List<ToolCallLog> findByTicketId(String ticketId) {
            return logs.stream().filter(log -> ticketId.equals(log.getTicketId())).toList();
        }

        public List<ToolCallLog> logs() {
            return logs;
        }
    }
}
