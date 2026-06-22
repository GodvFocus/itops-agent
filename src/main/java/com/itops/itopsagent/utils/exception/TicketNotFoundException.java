package com.itops.itopsagent.utils.exception;

public class TicketNotFoundException extends RuntimeException {

    public TicketNotFoundException(String ticketId) {
        super("Ticket not found: " + ticketId);
    }
}
