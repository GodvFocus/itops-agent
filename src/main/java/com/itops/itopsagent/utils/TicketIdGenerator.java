package com.itops.itopsagent.utils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TicketIdGenerator {

    /** 工单号中的时间格式，保证生成结果具备较好的可读性和有序性。 */
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 统一从 Spring 注入时钟，方便测试稳定控制时间。 */
    private final Clock clock;

    /**
     * 生成工单 ID。
     * 由固定前缀、毫秒时间戳和三位随机尾缀组成，兼顾可读性和简单去碰撞。
     */
    public String nextId() {
        String timestamp = LocalDateTime.now(clock).format(FORMATTER);
        int suffix = ThreadLocalRandom.current().nextInt(100, 1000);
        return "T" + timestamp + suffix;
    }
}
