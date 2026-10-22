package br.com.oficina.auth.adapter.out.ses;

import br.com.oficina.auth.application.port.out.NotificationSenderPort;
import br.com.oficina.auth.domain.NotificationMessage;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sesv2.model.Body;
import software.amazon.awssdk.services.sesv2.model.Content;
import software.amazon.awssdk.services.sesv2.model.Destination;
import software.amazon.awssdk.services.sesv2.model.EmailContent;
import software.amazon.awssdk.services.sesv2.model.Message;
import software.amazon.awssdk.services.sesv2.model.SendEmailRequest;

public final class SesNotificationAdapter implements NotificationSenderPort {

  private final SesV2Client client;
  private final String source;

  public SesNotificationAdapter(SesV2Client client, String source) {
    this.client = client;
    this.source = source;
  }

  @Override
  public void send(NotificationMessage notification) {
    Message message =
        Message.builder()
            .subject(Content.builder().data(notification.subject()).charset("UTF-8").build())
            .body(
                Body.builder()
                    .text(Content.builder().data(notification.body()).charset("UTF-8").build())
                    .build())
            .build();
    client.sendEmail(
        SendEmailRequest.builder()
            .fromEmailAddress(source)
            .destination(Destination.builder().toAddresses(notification.destination()).build())
            .content(EmailContent.builder().simple(message).build())
            .build());
  }
}
