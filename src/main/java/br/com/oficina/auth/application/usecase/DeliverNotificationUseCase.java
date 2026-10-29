package br.com.oficina.auth.application.usecase;

import br.com.oficina.auth.application.port.in.DeliverNotification;
import br.com.oficina.auth.application.port.out.NotificationSenderPort;
import br.com.oficina.auth.domain.NotificationMessage;
import java.util.List;

public final class DeliverNotificationUseCase implements DeliverNotification {

  private final NotificationSenderPort sender;

  public DeliverNotificationUseCase(NotificationSenderPort sender) {
    this.sender = sender;
  }

  @Override
  public int deliver(List<NotificationMessage> notifications) {
    if (notifications == null || notifications.isEmpty()) {
      throw new IllegalArgumentException("Nenhuma notificação informada");
    }
    notifications.forEach(sender::send);
    return notifications.size();
  }
}
