package com.islandride.core.exception;

/**
 * Thrown when an Idempotency-Key is reused with a mismatched request payload or conflicting concurrent execution.
 */
public class IdempotencyConflictException extends ConflictException {

    public IdempotencyConflictException(String message) {
        super(message);
    }
}

