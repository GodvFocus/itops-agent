package com.itops.itopsagent.service.impl;

import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.entity.ConversationMessage;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import com.itops.itopsagent.mapper.ConversationMessageMapper;
import com.itops.itopsagent.service.ConversationMessageService;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConversationMessageServiceImpl implements ConversationMessageService {

    private final ConversationMessageMapper conversationMessageMapper;
    private final Clock clock;

    @Override
    @Transactional
    public ConversationMessageResponse appendMessage(String ticketId, ConversationRole role, ConversationMessageType messageType, String content) {
        ConversationMessage saved = ConversationMessage.builder()
                .ticketId(ticketId)
                .role(role)
                .messageType(messageType)
                .content(content)
                .createdAt(Instant.now(clock))
                .build();
        conversationMessageMapper.insert(saved);
        return toResponse(saved);
    }

    @Override
    @Transactional
    public Optional<ConversationMessageResponse> appendMessageIfChanged(String ticketId, ConversationRole role, ConversationMessageType messageType, String content) {
        ConversationMessage latest = conversationMessageMapper.findTopByTicketIdOrderByCreatedAtDesc(ticketId);
        if (latest != null
                && latest.getRole() == role
                && latest.getMessageType() == messageType
                && latest.getContent().equals(content)) {
            return Optional.empty();
        }
        return Optional.of(appendMessage(ticketId, role, messageType, content));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> listMessages(String ticketId) {
        return conversationMessageMapper.findByTicketIdOrderByCreatedAtAsc(ticketId).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> listRecentMessages(String ticketId, int limit) {
        return conversationMessageMapper.findTop6ByTicketIdOrderByCreatedAtDesc(ticketId).stream()
                .sorted(Comparator.comparing(ConversationMessage::getCreatedAt))
                .limit(limit)
                .map(this::toResponse)
                .toList();
    }

    private ConversationMessageResponse toResponse(ConversationMessage message) {
        return new ConversationMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getMessageType(),
                message.getCreatedAt());
    }
}
