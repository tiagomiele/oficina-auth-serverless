package br.com.oficina.auth.application.port.out;

import br.com.oficina.auth.domain.AuthorizedClient;

public interface TokenValidatorPort {

  AuthorizedClient validate(String token);
}
