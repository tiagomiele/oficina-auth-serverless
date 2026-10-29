package br.com.oficina.auth.adapter.out.sns;

import br.com.oficina.auth.application.port.out.NotificationPublisherPort;
import br.com.oficina.auth.domain.NotificationMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

public final class SnsNotificationAdapter implements NotificationPublisherPort {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final SnsClient client;
  private final String topicArn;

  public SnsNotificationAdapter(SnsClient client, String topicArn) {
    this.client = client;
    this.topicArn = topicArn;
  }

  @Override
  public void publish(NotificationMessage notification) {
    try {
      client.publish(
          PublishRequest.builder()
              .topicArn(topicArn)
              .message(JSON.writeValueAsString(notification))
              .build());
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Não foi possível serializar a notificação", exception);
    }
  }
}
