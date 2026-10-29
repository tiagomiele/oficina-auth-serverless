package br.com.oficina.auth.notification;

public interface NotificationSender {

  void send(NotificationMessage message);
}
