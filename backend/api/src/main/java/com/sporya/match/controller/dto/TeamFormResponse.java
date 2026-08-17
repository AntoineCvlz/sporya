package com.sporya.match.controller.dto;

import java.util.List;
import java.util.UUID;

public record TeamFormResponse(UUID teamId, List<RecentMatchResult> recentResults) {}
