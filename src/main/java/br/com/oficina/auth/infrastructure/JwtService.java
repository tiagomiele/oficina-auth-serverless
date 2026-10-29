package br.com.oficina.auth.infrastructure;

import br.com.oficina.auth.adapter.out.jwt.JwtTokenAdapter;
import br.com.oficina.auth.application.port.out.TokenIssuerPort;
import br.com.oficina.auth.application.port.out.TokenValidatorPort;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.AuthorizedClient;
import io.jsonwebtoken.Claims;

public final class JwtService implements TokenIssuerPort, TokenValidatorPort {

  private final JwtTokenAdapter delegate;

  public JwtService(AuthConfig config) {
    this.delegate = new JwtTokenAdapter(config);
  }

  @Override
  public String issue(long clientId) {
    return delegate.issue(clientId);
  }

  @Override
  public AuthorizedClient validate(String token) {
    return delegate.validate(token);
  }

  public Claims verify(String token) {
    return delegate.verifyClaims(token);
  }
}
