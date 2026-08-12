package com.sporya.auth.infrastructure.security;

import com.sporya.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Émission et validation de JWT signés RS256 (voir ADR-013) — vérifiables localement par tout
 * service détenant la clé publique, sans appel réseau à Auth Service.
 */
@Service
public class JwtService {

  private final RSAPrivateKey privateKey;
  private final RSAPublicKey publicKey;
  private final String issuer;
  private final long accessTokenTtlMinutes;

  JwtService(
      RSAPrivateKey privateKey,
      RSAPublicKey publicKey,
      @Value("${sporya.jwt.issuer}") String issuer,
      @Value("${sporya.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.issuer = issuer;
    this.accessTokenTtlMinutes = accessTokenTtlMinutes;
  }

  public long accessTokenTtlSeconds() {
    return accessTokenTtlMinutes * 60;
  }

  /** Claim "memberships" vide pour l'instant : Club Service n'existe pas encore (voir ADR-011). */
  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("email", user.getEmail())
        .claim("memberships", List.of())
        .issuer(issuer)
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plusSeconds(accessTokenTtlSeconds())))
        .signWith(privateKey, Jwts.SIG.RS256)
        .compact();
  }

  public Claims parseAndValidate(String token) {
    Jws<Claims> jws = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token);
    return jws.getPayload();
  }
}
