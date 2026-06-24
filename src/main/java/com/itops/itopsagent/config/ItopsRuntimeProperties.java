package com.itops.itopsagent.config;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 统一沉淀项目级运行时配置命名空间。
 * 这样后续接入真实 Redis、RabbitMQ、Qdrant 以及模型配置时，不需要把配置散落到业务类里。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "itops")
public class ItopsRuntimeProperties {

    private final Harness harness = new Harness();
    private final Integrations integrations = new Integrations();
    private final Ai ai = new Ai();
    private final AgentRuntime agentRuntime = new AgentRuntime();

    @Getter
    @Setter
    public static class Harness {
        private final Queue queue = new Queue();
        private final Lock lock = new Lock();
        private final Idempotency idempotency = new Idempotency();
    }

    @Getter
    @Setter
    public static class Queue {
        /** 先保留 mode，后续可以从 memory 平滑切到 rabbitmq。 */
        private String mode = "memory";
        private String toolExchange = "itops.tool.exchange";
        private String toolRoutingKey = "itops.tool.execute";
        private String toolQueue = "itops.tool.queue";
        private String deadLetterExchange = "itops.tool.dlx";
        private String deadLetterRoutingKey = "itops.tool.dead";
        private String deadLetterQueue = "itops.tool.dead.queue";
    }

    @Getter
    @Setter
    public static class Lock {
        /** 当前仍是内存锁，但保留 redis 模式开关便于后续替换实现。 */
        private String mode = "memory";
        private String keyPrefix = "itops:harness:ticket-lock:";
        private Duration ttl = Duration.ofSeconds(30);
    }

    @Getter
    @Setter
    public static class Idempotency {
        /** fastStore 对应“Redis 快速判重层”，最终事实仍在 MySQL。 */
        private String fastStore = "memory";
        private String keyPrefix = "itops:harness:idem:";
    }

    @Getter
    @Setter
    public static class Integrations {
        private final Qdrant qdrant = new Qdrant();
    }

    @Getter
    @Setter
    public static class Qdrant {
        private boolean enabled;
        private String url = "";
        private String collectionName = "sop_catalog";
    }

    @Getter
    @Setter
    public static class Ai {
        private final Model embedding = new Model();
        private final Model chat = new Model();
    }

    @Getter
    @Setter
    public static class Model {
        private String provider = "mock";
        private String model = "";
        private String endpoint = "";
        private String apiKey = "";
    }

    @Getter
    @Setter
    public static class AgentRuntime {
        /** 默认直接使用项目约定的 Anaconda 虚拟环境解释器。 */
        private String pythonExecutable = "D:/anaconda3/envs/lc/python.exe";
        /** 给 Python Runtime 一个明确超时，避免请求线程无限挂起。 */
        private Duration timeout = Duration.ofSeconds(15);
    }
}
