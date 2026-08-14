package com.sporya.club.controller;

import com.sporya.club.controller.dto.ErrorResponse;
import com.sporya.club.domain.ClubNotFoundException;
import com.sporya.club.domain.TeamNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ClubApiExceptionHandler {

  @ExceptionHandler(ClubNotFoundException.class)
  ResponseEntity<ErrorResponse> handleClubNotFound(ClubNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(TeamNotFoundException.class)
  ResponseEntity<ErrorResponse> handleTeamNotFound(TeamNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
    String message =
        ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return ResponseEntity.badRequest().body(new ErrorResponse(message));
  }
}
