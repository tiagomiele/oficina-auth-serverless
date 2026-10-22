package br.com.oficina.auth.support;

import br.com.oficina.auth.config.AuthConfig;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class TestKeys {

  private TestKeys() {}

  public static KeyPair generate() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao gerar par de chaves de teste", exception);
    }
  }

  public static AuthConfig config(KeyPair pair, String issuer, String audience, long ttlSeconds) {
    return new AuthConfig(
        null,
        null,
        null,
        encoded(pair.getPrivate()),
        encoded(pair.getPublic()),
        issuer,
        audience,
        ttlSeconds);
  }

  public static String encoded(Key key) {
    return Base64.getEncoder().encodeToString(key.getEncoded());
  }
}
