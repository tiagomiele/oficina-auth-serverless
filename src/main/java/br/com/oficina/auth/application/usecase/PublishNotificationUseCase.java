package br.com.oficina.auth.application.usecase;

import br.com.oficina.auth.application.port.in.PublishNotification;
import br.com.oficina.auth.application.port.out.NotificationPublisherPort;
import br.com.oficina.auth.domain.NotificationMessage;

public final class PublishNotificationUseCase implements PublishNotification {

  private final NotificationPublisherPort notifications;

  public PublishNotificationUseCase(NotificationPublisherPort notifications) {
    this.notifications = notifications;
  }

  @Override
  public void publish(String destination, String subject, String body, String requestId) {
    notifications.publish(new NotificationMessage(destination, subject, body, requestId));
  }
}
