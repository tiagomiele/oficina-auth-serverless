package br.com.oficina.auth.infrastructure;

import br.com.oficina.auth.adapter.out.http.OsServiceClientRepository;
import br.com.oficina.auth.application.ClienteDirectory;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.Cpf;
import java.util.Optional;

public final class ClienteRepository implements ClienteDirectory {

  private final OsServiceClientRepository delegate;

  public ClienteRepository(AuthConfig config) {
    this.delegate = new OsServiceClientRepository(config);
  }

  @Override
  public Optional<Long> findActiveClientId(Cpf cpf) {
    return delegate.findActiveClientId(cpf);
  }
}
