package com.sporya.auth.domain;

import java.util.UUID;

public record ClubRole(UUID clubId, Role role) {}
