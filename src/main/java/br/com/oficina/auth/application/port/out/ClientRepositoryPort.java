package br.com.oficina.auth.application.port.out;

import br.com.oficina.auth.domain.Cpf;
import java.util.Optional;

public interface ClientRepositoryPort {

  Optional<Long> findActiveClientId(Cpf cpf);
}
