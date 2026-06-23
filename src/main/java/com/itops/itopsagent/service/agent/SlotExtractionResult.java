package com.itops.itopsagent.service.agent;

import java.util.List;
import java.util.Map;

public record SlotExtractionResult(
        Map<String, Object> slots,
        List<String> missingSlots,
        String reasoning) {
}
