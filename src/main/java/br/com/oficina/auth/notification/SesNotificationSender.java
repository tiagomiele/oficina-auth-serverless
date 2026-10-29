package br.com.oficina.auth.notification;

import br.com.oficina.auth.adapter.out.ses.SesNotificationAdapter;
import software.amazon.awssdk.services.sesv2.SesV2Client;

public final class SesNotificationSender implements NotificationSender {

  private final SesNotificationAdapter delegate;

  public SesNotificationSender(SesV2Client client, String source) {
    this.delegate = new SesNotificationAdapter(client, source);
  }

  @Override
  public void send(NotificationMessage notification) {
    delegate.send(notification.toDomain());
  }
}
