package com.hackathon.leave.web;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * One error shape for the whole API: {"status": n, "message": "..."}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> apiException(ApiException ex) {
        return body(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> validation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .orElse("Invalid request");
        return body(400, msg);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, Object>> optimistic(OptimisticLockingFailureException ex) {
        return body(409, "The request was already decided by someone else — refresh and try again");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception ex) {
        return body(500, ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    }

    private ResponseEntity<Map<String, Object>> body(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("status", status, "message", message));
    }
}
