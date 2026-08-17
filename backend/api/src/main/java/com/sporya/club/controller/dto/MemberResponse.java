package com.sporya.club.controller.dto;

import com.sporya.auth.domain.Role;
import java.time.Instant;
import java.util.UUID;

public record MemberResponse(UUID userId, String email, Role role, Instant createdAt) {}
