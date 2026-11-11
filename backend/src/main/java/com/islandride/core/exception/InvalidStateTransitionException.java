package com.islandride.core.exception;

/**
 * Thrown when a booking, payment, refund, or hold state machine transition violates allowed lifecycle rules.
 */
public class InvalidStateTransitionException extends ConflictException {

    public InvalidStateTransitionException(String message) {
        super(message);
    }
}

