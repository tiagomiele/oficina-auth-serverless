package br.com.oficina.auth.application.usecase;

import br.com.oficina.auth.application.port.in.AuthorizeToken;
import br.com.oficina.auth.application.port.out.TokenValidatorPort;
import br.com.oficina.auth.domain.AuthorizedClient;

public final class AuthorizeTokenUseCase implements AuthorizeToken {

  private final TokenValidatorPort tokens;

  public AuthorizeTokenUseCase(TokenValidatorPort tokens) {
    this.tokens = tokens;
  }

  @Override
  public AuthorizedClient authorize(String token) {
    return tokens.validate(token);
  }
}
