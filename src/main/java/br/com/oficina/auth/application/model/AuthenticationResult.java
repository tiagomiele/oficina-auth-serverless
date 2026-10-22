package br.com.oficina.auth.application.model;

public record AuthenticationResult(String accessToken, String tokenType, long expiresIn) {

  public AuthenticationResult {
    if (accessToken == null || accessToken.isBlank()) {
      throw new IllegalArgumentException("accessToken inválido");
    }
    if (tokenType == null || tokenType.isBlank()) {
      throw new IllegalArgumentException("tokenType inválido");
    }
    if (expiresIn <= 0) {
      throw new IllegalArgumentException("expiresIn inválido");
    }
  }
}
