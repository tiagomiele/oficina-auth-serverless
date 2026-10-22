package br.com.oficina.auth.notification;

public interface NotificationPublisher {

  void publish(NotificationMessage message);
}
