package br.com.oficina.auth.notification;

public record NotificationMessage(
    String destination, String subject, String body, String requestId) {

  public NotificationMessage {
    br.com.oficina.auth.domain.NotificationMessage validated =
        new br.com.oficina.auth.domain.NotificationMessage(destination, subject, body, requestId);
    destination = validated.destination();
    subject = validated.subject();
    body = validated.body();
    requestId = validated.requestId();
  }

  public br.com.oficina.auth.domain.NotificationMessage toDomain() {
    return new br.com.oficina.auth.domain.NotificationMessage(
        destination, subject, body, requestId);
  }

  public static NotificationMessage fromDomain(
      br.com.oficina.auth.domain.NotificationMessage notification) {
    return new NotificationMessage(
        notification.destination(),
        notification.subject(),
        notification.body(),
        notification.requestId());
  }
}
