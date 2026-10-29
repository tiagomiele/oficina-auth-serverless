package br.com.oficina.auth.application.port.in;

import br.com.oficina.auth.domain.NotificationMessage;
import java.util.List;

public interface DeliverNotification {

  int deliver(List<NotificationMessage> notifications);
}
