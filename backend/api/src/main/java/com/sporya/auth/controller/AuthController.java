package com.sporya.auth.controller;

import com.sporya.auth.application.AuthenticationService;
import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.controller.dto.UserResponse;
import com.sporya.auth.infrastructure.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final AuthenticationService authenticationService;

  public AuthController(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse register(@Valid @RequestBody RegisterRequest request) {
    return authenticationService.register(request);
  }

  @PostMapping("/login")
  public AuthResponse login(@Valid @RequestBody LoginRequest request) {
    return authenticationService.login(request);
  }

  @GetMapping("/me")
  public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
    return authenticationService.currentUser(user.userId());
  }
}
