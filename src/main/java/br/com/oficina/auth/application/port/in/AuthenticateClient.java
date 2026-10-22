package br.com.oficina.auth.application.port.in;

import br.com.oficina.auth.application.model.AuthenticationResult;
import java.util.Optional;

public interface AuthenticateClient {

  Optional<AuthenticationResult> authenticate(String rawCpf);
}
