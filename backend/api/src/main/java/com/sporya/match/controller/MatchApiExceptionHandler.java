package com.sporya.match.controller;

import com.sporya.match.controller.dto.ErrorResponse;
import com.sporya.match.domain.CompetitionNotFoundException;
import com.sporya.match.domain.InvalidMatchStateException;
import com.sporya.match.domain.MatchAccessDeniedException;
import com.sporya.match.domain.MatchNotFoundException;
import com.sporya.match.domain.PlayerNotInMatchException;
import com.sporya.match.domain.RedCardViolationException;
import com.sporya.match.domain.SeasonNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class MatchApiExceptionHandler {

  @ExceptionHandler(CompetitionNotFoundException.class)
  ResponseEntity<ErrorResponse> handleCompetitionNotFound(CompetitionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(SeasonNotFoundException.class)
  ResponseEntity<ErrorResponse> handleSeasonNotFound(SeasonNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MatchNotFoundException.class)
  ResponseEntity<ErrorResponse> handleMatchNotFound(MatchNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(MatchAccessDeniedException.class)
  ResponseEntity<ErrorResponse> handleMatchAccessDenied(MatchAccessDeniedException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(InvalidMatchStateException.class)
  ResponseEntity<ErrorResponse> handleInvalidMatchState(InvalidMatchStateException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(PlayerNotInMatchException.class)
  ResponseEntity<ErrorResponse> handlePlayerNotInMatch(PlayerNotInMatchException ex) {
    return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
  }

  @ExceptionHandler(RedCardViolationException.class)
  ResponseEntity<ErrorResponse> handleRedCardViolation(RedCardViolationException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
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
