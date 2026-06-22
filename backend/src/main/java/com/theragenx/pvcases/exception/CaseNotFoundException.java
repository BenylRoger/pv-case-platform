package com.theragenx.pvcases.exception;

/**
 * Thrown when a requested case ID does not exist in the store.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class CaseNotFoundException extends RuntimeException {
    public CaseNotFoundException(String message) {
        super(message);
    }
}
