package com.itops.itopsagent.service;

import com.itops.itopsagent.dto.ConversationMessageResponse;
import com.itops.itopsagent.entity.enums.ConversationMessageType;
import com.itops.itopsagent.entity.enums.ConversationRole;
import java.util.List;
import java.util.Optional;

public interface ConversationMessageService {

    ConversationMessageResponse appendMessage(String ticketId, ConversationRole role, ConversationMessageType messageType, String content);

    Optional<ConversationMessageResponse> appendMessageIfChanged(String ticketId, ConversationRole role, ConversationMessageType messageType, String content);

    List<ConversationMessageResponse> listMessages(String ticketId);

    List<ConversationMessageResponse> listRecentMessages(String ticketId, int limit);
}
