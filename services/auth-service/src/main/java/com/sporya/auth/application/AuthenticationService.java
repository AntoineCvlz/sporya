package com.sporya.auth.application;

import com.sporya.auth.controller.dto.AuthResponse;
import com.sporya.auth.controller.dto.LoginRequest;
import com.sporya.auth.controller.dto.RegisterRequest;
import com.sporya.auth.controller.dto.UserResponse;
import com.sporya.auth.domain.EmailAlreadyUsedException;
import com.sporya.auth.domain.InvalidCredentialsException;
import com.sporya.auth.domain.User;
import com.sporya.auth.domain.UserNotFoundException;
import com.sporya.auth.infrastructure.persistence.UserRepository;
import com.sporya.auth.infrastructure.security.JwtService;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;

  public AuthenticationService(
      UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    if (userRepository.existsByEmail(request.email())) {
      throw new EmailAlreadyUsedException(request.email());
    }
    User user = new User(request.email(), passwordEncoder.encode(request.password()));
    return UserResponse.from(userRepository.save(user));
  }

  @Transactional(readOnly = true)
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new InvalidCredentialsException();
    }
    String accessToken = jwtService.generateAccessToken(user);
    return AuthResponse.bearer(accessToken, jwtService.accessTokenTtlSeconds());
  }

  @Transactional(readOnly = true)
  public UserResponse currentUser(UUID userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    return UserResponse.from(user);
  }
}
