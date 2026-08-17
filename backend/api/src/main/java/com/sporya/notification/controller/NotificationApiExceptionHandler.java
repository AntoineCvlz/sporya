package com.sporya.notification.controller;

import com.sporya.notification.domain.NotificationNotFoundException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class NotificationApiExceptionHandler {

  @ExceptionHandler(NotificationNotFoundException.class)
  ResponseEntity<Map<String, String>> handleNotificationNotFound(NotificationNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
  }
}
