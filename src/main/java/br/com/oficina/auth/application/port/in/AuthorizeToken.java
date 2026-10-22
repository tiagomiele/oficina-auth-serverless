package br.com.oficina.auth.application.port.in;

import br.com.oficina.auth.domain.AuthorizedClient;

public interface AuthorizeToken {

  AuthorizedClient authorize(String token);
}
