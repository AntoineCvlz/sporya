package com.sporya.auth.infrastructure.security;

import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class JwtKeyConfig {

  @Bean
  RSAPrivateKey jwtPrivateKey(@Value("${sporya.jwt.private-key-base64}") String base64Pem)
      throws GeneralSecurityException {
    return PemUtils.parsePrivateKey(base64Pem);
  }

  @Bean
  RSAPublicKey jwtPublicKey(@Value("${sporya.jwt.public-key-base64}") String base64Pem)
      throws GeneralSecurityException {
    return PemUtils.parsePublicKey(base64Pem);
  }
}
