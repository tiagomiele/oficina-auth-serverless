package br.com.oficina.auth.application.port.out;

import br.com.oficina.auth.domain.NotificationMessage;

public interface NotificationPublisherPort {

  void publish(NotificationMessage notification);
}
