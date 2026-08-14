package com.sporya.auth.domain;

public class EmailAlreadyUsedException extends RuntimeException {

  public EmailAlreadyUsedException(String email) {
    super("Email already used: " + email);
  }
}
