package com.hackathon.leave.web;

/**
 * Carries an HTTP status and a message to the GlobalExceptionHandler,
 * which renders it as {"status": n, "message": "..."} .
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
