package br.com.oficina.auth.application.port.in;

public interface PublishNotification {

  void publish(String destination, String subject, String body, String requestId);
}
