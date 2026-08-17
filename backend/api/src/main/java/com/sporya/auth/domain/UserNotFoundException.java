package com.sporya.auth.domain;

import java.util.UUID;

public class UserNotFoundException extends RuntimeException {

  public UserNotFoundException(UUID id) {
    super("User not found: " + id);
  }

  public UserNotFoundException(String email) {
    super("User not found: " + email);
  }
}
