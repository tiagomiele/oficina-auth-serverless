package br.com.oficina.auth.domain;

import java.util.regex.Pattern;

public record NotificationMessage(
    String destination, String subject, String body, String requestId) {

  private static final Pattern EMAIL =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

  public NotificationMessage {
    destination = required(destination, "destination", 320);
    subject = required(subject, "subject", 200);
    body = required(body, "body", 5_000);
    requestId = required(requestId, "requestId", 128);
    if (!EMAIL.matcher(destination).matches()) {
      throw new IllegalArgumentException("destination inválido");
    }
  }

  private static String required(String value, String field, int maximumLength) {
    if (value == null || value.isBlank() || value.length() > maximumLength) {
      throw new IllegalArgumentException(field + " inválido");
    }
    return value.strip();
  }
}
