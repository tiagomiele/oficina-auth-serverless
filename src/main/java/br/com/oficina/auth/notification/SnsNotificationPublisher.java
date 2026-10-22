package br.com.oficina.auth.notification;

import br.com.oficina.auth.adapter.out.sns.SnsNotificationAdapter;
import software.amazon.awssdk.services.sns.SnsClient;

public final class SnsNotificationPublisher implements NotificationPublisher {

  private final SnsNotificationAdapter delegate;

  public SnsNotificationPublisher(SnsClient client, String topicArn) {
    this.delegate = new SnsNotificationAdapter(client, topicArn);
  }

  @Override
  public void publish(NotificationMessage message) {
    delegate.publish(message.toDomain());
  }
}
