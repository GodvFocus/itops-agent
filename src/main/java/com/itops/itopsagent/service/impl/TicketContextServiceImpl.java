package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.TicketContextResponse;
import com.itops.itopsagent.dto.UpdateAgentContextRequest;
import com.itops.itopsagent.entity.TicketContext;
import com.itops.itopsagent.entity.enums.RiskLevel;
import com.itops.itopsagent.entity.enums.TicketIntent;
import com.itops.itopsagent.mapper.TicketContextMapper;
import com.itops.itopsagent.mapper.TicketMapper;
import com.itops.itopsagent.service.TicketContextService;
import com.itops.itopsagent.utils.exception.TicketNotFoundException;
import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TicketContextServiceImpl implements TicketContextService {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final TicketContextMapper ticketContextMapper;
    private final TicketMapper ticketMapper;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public TicketContextResponse getContext(String ticketId) {
        if (ticketMapper.selectById(ticketId) == null) {
            throw new TicketNotFoundException(ticketId);
        }
        TicketContext context = ticketContextMapper.findByTicketId(ticketId);
        return context == null
                ? new TicketContextResponse(
                        TicketIntent.UNKNOWN,
                        Map.of(),
                        List.of(),
                        List.of(),
                        Map.of(),
                        RiskLevel.LOW,
                        "INIT",
                        null)
                : toResponse(context);
    }

    @Override
    @Transactional
    public TicketContextResponse saveContext(String ticketId, UpdateAgentContextRequest request) {
        if (ticketMapper.selectById(ticketId) == null) {
            throw new TicketNotFoundException(ticketId);
        }
        Instant now = Instant.now(clock);
        TicketContext context = ticketContextMapper.findByTicketId(ticketId);
        if (context == null) {
            context = TicketContext.builder().build();
        }
        context.setTicketId(ticketId);
        context.setIntent(request.intent() == null ? TicketIntent.UNKNOWN : request.intent());
        context.setSlotsJson(serialize(request.slots() == null ? Map.of() : request.slots()));
        context.setMissingSlotsJson(serialize(request.missingSlots() == null ? List.of() : request.missingSlots()));
        context.setMatchedSopIdsJson(serialize(request.matchedSopIds() == null ? List.of() : request.matchedSopIds()));
        context.setCurrentPlanJson(serialize(request.currentPlan() == null ? Map.of() : request.currentPlan()));
        context.setRiskLevel(request.riskLevel() == null ? RiskLevel.LOW : request.riskLevel());
        context.setLastAgentStep(isBlank(request.lastAgentStep()) ? "INIT" : request.lastAgentStep().trim());
        context.setUpdatedAt(now);
        if (context.getId() == null) {
            ticketContextMapper.insert(context);
        } else {
            ticketContextMapper.updateById(context);
        }
        return toResponse(context);
    }

    private TicketContextResponse toResponse(TicketContext context) {
        return new TicketContextResponse(
                context.getIntent(),
                deserializeMap(context.getSlotsJson()),
                deserializeStringList(context.getMissingSlotsJson()),
                deserializeStringList(context.getMatchedSopIdsJson()),
                deserializeMap(context.getCurrentPlanJson()),
                context.getRiskLevel(),
                context.getLastAgentStep(),
                context.getUpdatedAt());
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to serialize ticket context", exception);
        }
    }

    private Map<String, Object> deserializeMap(String json) {
        try {
            return json == null ? new LinkedHashMap<>() : objectMapper.readValue(json, MAP_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize ticket context map", exception);
        }
    }

    private List<String> deserializeStringList(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("Failed to deserialize ticket context list", exception);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
