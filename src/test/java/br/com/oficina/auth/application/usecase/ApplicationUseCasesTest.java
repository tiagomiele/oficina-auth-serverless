package br.com.oficina.auth.application.usecase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.oficina.auth.application.model.AuthenticationResult;
import br.com.oficina.auth.domain.AuthorizedClient;
import br.com.oficina.auth.domain.NotificationMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ApplicationUseCasesTest {

  @Test
  void authenticatesActiveClientUsingOnlyPorts() {
    AtomicReference<String> normalizedCpf = new AtomicReference<>();
    AtomicLong issuedFor = new AtomicLong();
    AuthenticateClientUseCase useCase =
        new AuthenticateClientUseCase(
            cpf -> {
              normalizedCpf.set(cpf.value());
              return Optional.of(42L);
            },
            clientId -> {
              issuedFor.set(clientId);
              return "signed-token";
            },
            900);

    AuthenticationResult result = useCase.authenticate("529.982.247-25").orElseThrow();

    assertEquals("52998224725", normalizedCpf.get());
    assertEquals(42L, issuedFor.get());
    assertEquals("signed-token", result.accessToken());
    assertEquals("Bearer", result.tokenType());
    assertEquals(900, result.expiresIn());
  }

  @Test
  void doesNotIssueTokenForInactiveClient() {
    AtomicLong issuedFor = new AtomicLong();
    AuthenticateClientUseCase useCase =
        new AuthenticateClientUseCase(
            cpf -> Optional.empty(),
            clientId -> {
              issuedFor.set(clientId);
              return "unexpected";
            },
            900);

    assertFalse(useCase.authenticate("52998224725").isPresent());
    assertEquals(0, issuedFor.get());
  }

  @Test
  void authorizesUsingTheValidatorPort() {
    AuthorizedClient expected = new AuthorizedClient("42", 42L, "CLIENTE");
    AuthorizeTokenUseCase useCase = new AuthorizeTokenUseCase(token -> expected);

    assertSame(expected, useCase.authorize("token"));
  }

  @Test
  void validatesAndPublishesNotificationThroughTheOutputPort() {
    AtomicReference<NotificationMessage> published = new AtomicReference<>();
    PublishNotificationUseCase useCase = new PublishNotificationUseCase(published::set);

    useCase.publish("cliente@example.com", "OS atualizada", "Status atualizado", "request-1");

    assertEquals("cliente@example.com", published.get().destination());
    assertEquals("request-1", published.get().requestId());
    assertThrows(
        IllegalArgumentException.class,
        () -> useCase.publish("invalid", "assunto", "corpo", "request-1"));
  }

  @Test
  void deliversAllNotificationsAndPropagatesFailures() {
    List<NotificationMessage> sent = new ArrayList<>();
    DeliverNotificationUseCase useCase = new DeliverNotificationUseCase(sent::add);
    NotificationMessage first = notification("one@example.com", "request-1");
    NotificationMessage second = notification("two@example.com", "request-2");

    assertEquals(2, useCase.deliver(List.of(first, second)));
    assertEquals(List.of(first, second), sent);

    DeliverNotificationUseCase failing =
        new DeliverNotificationUseCase(
            notification -> {
              throw new IllegalStateException("delivery unavailable");
            });
    assertThrows(IllegalStateException.class, () -> failing.deliver(List.of(first)));
  }

  @Test
  void forwardsOnlyTheLogsReceivedFromTheInputAdapter() {
    AtomicReference<List<Map<String, Object>>> published = new AtomicReference<>();
    ForwardAccessLogsUseCase useCase = new ForwardAccessLogsUseCase(published::set);
    List<Map<String, Object>> logs = List.of(Map.of("requestId", "request-1", "status", 200));

    assertEquals(1, useCase.forward(logs));
    assertEquals(logs, published.get());
  }

  private static NotificationMessage notification(String destination, String requestId) {
    return new NotificationMessage(destination, "OS atualizada", "Status atualizado", requestId);
  }
}
