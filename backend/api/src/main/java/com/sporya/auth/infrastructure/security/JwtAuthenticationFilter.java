package com.sporya.auth.infrastructure.security;

import com.sporya.auth.domain.ClubRole;
import com.sporya.auth.domain.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Authentifie la requête à partir d'un Bearer token JWT valide, sinon la laisse anonyme. */
@Component
class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtService jwtService;

  JwtAuthenticationFilter(JwtService jwtService) {
    this.jwtService = jwtService;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header != null && header.startsWith("Bearer ")) {
      try {
        Claims claims = jwtService.parseAndValidate(header.substring("Bearer ".length()));
        UUID userId = UUID.fromString(claims.getSubject());
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(userId, parseMemberships(claims));
        var authentication =
            new UsernamePasswordAuthenticationToken(authenticatedUser, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
      } catch (JwtException | IllegalArgumentException ignored) {
        // Token absent/invalide/expiré -> requête traitée comme anonyme, rejetée plus loin
        // par la configuration Spring Security si la route l'exige.
      }
    }
    filterChain.doFilter(request, response);
  }

  @SuppressWarnings("unchecked")
  private static List<ClubRole> parseMemberships(Claims claims) {
    List<Map<String, Object>> raw = claims.get("memberships", List.class);
    return raw.stream()
        .map(
            entry ->
                new ClubRole(
                    UUID.fromString((String) entry.get("clubId")),
                    Role.valueOf((String) entry.get("role"))))
        .toList();
  }
}
