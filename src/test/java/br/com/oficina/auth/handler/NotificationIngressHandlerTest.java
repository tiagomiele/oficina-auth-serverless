package br.com.oficina.auth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import br.com.oficina.auth.notification.NotificationMessage;
import br.com.oficina.auth.observability.NoopTelemetry;
import br.com.oficina.auth.support.RecordingContext;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NotificationIngressHandlerTest {

  private final RecordingContext context = new RecordingContext("lambda-request-1");

  @Test
  void acceptsAuthenticatedNotificationWithoutLoggingPayload() {
    AtomicReference<NotificationMessage> published = new AtomicReference<>();
    NotificationIngressHandler handler =
        new NotificationIngressHandler(published::set, "a".repeat(32), new NoopTelemetry());

    APIGatewayV2HTTPResponse response =
        handler.handleRequest(
            event(
                """
                {"destinatario":"cliente@example.com","assunto":"OS atualizada","corpo":"Status atualizado"}
                """,
                "a".repeat(32)),
            context);

    assertEquals(202, response.getStatusCode());
    assertEquals("client-request-1", published.get().requestId());
    assertFalse(context.logLines().toString().contains("cliente@example.com"));
    assertFalse(context.logLines().toString().contains("Status atualizado"));
  }

  @Test
  void reportsServiceUnavailableWhenSnsRejectsNotification() {
    NotificationIngressHandler handler =
        new NotificationIngressHandler(
            message -> {
              throw new IllegalStateException("SNS indisponível");
            },
            "a".repeat(32),
            new NoopTelemetry());

    APIGatewayV2HTTPResponse response =
        handler.handleRequest(
            event(
                """
                {"destinatario":"cliente@example.com","assunto":"OS atualizada","corpo":"Status atualizado"}
                """,
                "a".repeat(32)),
            context);

    assertEquals(503, response.getStatusCode());
    assertFalse(context.logLines().toString().contains("cliente@example.com"));
    assertFalse(context.logLines().toString().contains("SNS indisponível"));
  }

  @Test
  void rejectsInvalidCredentialBeforeReadingPayload() {
    AtomicReference<NotificationMessage> published = new AtomicReference<>();
    NotificationIngressHandler handler =
        new NotificationIngressHandler(published::set, "a".repeat(32), new NoopTelemetry());

    APIGatewayV2HTTPResponse response =
        handler.handleRequest(event("not-json", "invalid"), context);

    assertEquals(401, response.getStatusCode());
    assertNull(published.get());
  }

  @Test
  void rejectsMalformedOrOversizedPayload() {
    NotificationIngressHandler handler =
        new NotificationIngressHandler(message -> {}, "a".repeat(32), new NoopTelemetry());

    APIGatewayV2HTTPResponse response =
        handler.handleRequest(
            event(
                "{\"destinatario\":\"cliente@example.com\",\"assunto\":\"a\",\"corpo\":\""
                    + "x".repeat(5_001)
                    + "\"}",
                "a".repeat(32)),
            context);

    assertEquals(400, response.getStatusCode());
  }

  private static APIGatewayV2HTTPEvent event(String body, String apiKey) {
    APIGatewayV2HTTPEvent.RequestContext requestContext =
        new APIGatewayV2HTTPEvent.RequestContext();
    requestContext.setRequestId("gateway-request-1");
    APIGatewayV2HTTPEvent event = new APIGatewayV2HTTPEvent();
    event.setBody(body);
    event.setHeaders(Map.of("X-Notification-Key", apiKey, "X-Request-Id", "client-request-1"));
    event.setRequestContext(requestContext);
    return event;
  }
}
