package com.itops.itopsagent.dto;

public record TicketConfirmRequest(
        Boolean resolved,
        String comment) {
}
