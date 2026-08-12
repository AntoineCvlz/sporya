package com.sporya.auth.infrastructure.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Décode une clé RSA fournie en variable d'environnement comme un PEM encodé en base64 (le wrapping
 * base64 évite les soucis de sauts de ligne dans les fichiers .env / Secrets K8s).
 */
final class PemUtils {

  private PemUtils() {}

  static RSAPrivateKey parsePrivateKey(String base64WrappedPem) throws GeneralSecurityException {
    byte[] der = decodePemBody(base64WrappedPem);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
  }

  static RSAPublicKey parsePublicKey(String base64WrappedPem) throws GeneralSecurityException {
    byte[] der = decodePemBody(base64WrappedPem);
    KeyFactory keyFactory = KeyFactory.getInstance("RSA");
    return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(der));
  }

  private static byte[] decodePemBody(String base64WrappedPem) {
    String pem = new String(Base64.getDecoder().decode(base64WrappedPem), StandardCharsets.UTF_8);
    String body =
        pem.replaceAll("-----BEGIN [^-]+-----", "")
            .replaceAll("-----END [^-]+-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(body);
  }
}
