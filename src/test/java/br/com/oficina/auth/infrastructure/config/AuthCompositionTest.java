package br.com.oficina.auth.infrastructure.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.oficina.auth.application.port.out.NotificationSenderPort;
import br.com.oficina.auth.domain.NotificationMessage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AuthCompositionTest {

  @Test
  void logModeDoesNotCreateSesAdapter() {
    AtomicBoolean sesCreated = new AtomicBoolean();

    AuthComposition.notificationSender(
            "log",
            () -> {
              sesCreated.set(true);
              return notification -> {};
            })
        .send(null);

    assertFalse(sesCreated.get());
  }

  @Test
  void sesModeUsesConfiguredAdapter() {
    AtomicReference<NotificationMessage> sent = new AtomicReference<>();
    NotificationSenderPort sender = AuthComposition.notificationSender("ses", () -> sent::set);
    NotificationMessage notification =
        new NotificationMessage("cliente@example.com", "assunto", "corpo", "request-1");

    sender.send(notification);

    assertSame(notification, sent.get());
  }

  @Test
  void unknownDeliveryModeFailsFast() {
    assertThrows(
        IllegalStateException.class,
        () -> AuthComposition.notificationSender("smtp", () -> notification -> {}));
  }
}
