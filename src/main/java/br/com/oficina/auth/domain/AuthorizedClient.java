package br.com.oficina.auth.domain;

public record AuthorizedClient(String subject, long clientId, String role) {

  public AuthorizedClient {
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject inválido");
    }
    if (clientId <= 0) {
      throw new IllegalArgumentException("clientId inválido");
    }
    if (role == null || role.isBlank()) {
      throw new IllegalArgumentException("role inválida");
    }
  }
}
