package br.com.oficina.auth.application.usecase;

import br.com.oficina.auth.application.model.AuthenticationResult;
import br.com.oficina.auth.application.port.in.AuthenticateClient;
import br.com.oficina.auth.application.port.out.ClientRepositoryPort;
import br.com.oficina.auth.application.port.out.TokenIssuerPort;
import br.com.oficina.auth.domain.Cpf;
import java.util.Optional;

public final class AuthenticateClientUseCase implements AuthenticateClient {

  private static final String TOKEN_TYPE = "Bearer";

  private final ClientRepositoryPort clients;
  private final TokenIssuerPort tokens;
  private final long ttlSeconds;

  public AuthenticateClientUseCase(
      ClientRepositoryPort clients, TokenIssuerPort tokens, long ttlSeconds) {
    this.clients = clients;
    this.tokens = tokens;
    this.ttlSeconds = ttlSeconds;
  }

  @Override
  public Optional<AuthenticationResult> authenticate(String rawCpf) {
    Cpf cpf = Cpf.parse(rawCpf);
    return clients
        .findActiveClientId(cpf)
        .map(clientId -> new AuthenticationResult(tokens.issue(clientId), TOKEN_TYPE, ttlSeconds));
  }
}
