package com.itops.itopsagent.utils.exception;

public class TicketConflictException extends RuntimeException {

    public TicketConflictException(String ticketId) {
        super("Ticket was updated concurrently: " + ticketId);
    }
}
