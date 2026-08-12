package com.sporya.auth.controller.dto;

import com.sporya.auth.domain.User;
import java.util.UUID;

public record UserResponse(UUID id, String email) {

  public static UserResponse from(User user) {
    return new UserResponse(user.getId(), user.getEmail());
  }
}
