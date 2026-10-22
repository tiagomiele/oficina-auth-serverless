package br.com.oficina.auth.handler;

import br.com.oficina.auth.application.port.in.PublishNotification;
import br.com.oficina.auth.application.usecase.PublishNotificationUseCase;
import br.com.oficina.auth.infrastructure.config.AuthComposition;
import br.com.oficina.auth.notification.NotificationMessage;
import br.com.oficina.auth.notification.NotificationPublisher;
import br.com.oficina.auth.observability.RequestIdentity;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;

public final class NotificationIngressHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private static final String FUNCTION = "notification-ingress";
  private static final String API_KEY_HEADER = "x-notification-key";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final PublishNotification publishNotification;
  private final String apiKey;
  private final Telemetry telemetry;

  public NotificationIngressHandler() {
    this(AuthComposition.notificationIngress());
  }

  NotificationIngressHandler(NotificationPublisher publisher, String apiKey, Telemetry telemetry) {
    this(
        new PublishNotificationUseCase(
            notification -> publisher.publish(NotificationMessage.fromDomain(notification))),
        apiKey,
        telemetry);
  }

  NotificationIngressHandler(
      PublishNotification publishNotification, String apiKey, Telemetry telemetry) {
    this.publishNotification = publishNotification;
    this.apiKey = apiKey;
    this.telemetry = telemetry;
  }

  private NotificationIngressHandler(AuthComposition.NotificationIngressDependencies dependencies) {
    this(dependencies.useCase(), dependencies.apiKey(), dependencies.telemetry());
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = resolveRequestId(event, context);
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    telemetry.addAttribute("request.id", requestId);
    if (!authorized(event)) {
      logger.log("DENIED", requestId, elapsedMillis(startedAt), "UNAUTHORIZED");
      return HttpResponses.json(
          401,
          Map.of("code", "UNAUTHORIZED", "message", "Credencial inválida.", "requestId", requestId),
          requestId);
    }
    try {
      JsonNode payload = JSON.readTree(requestBody(event));
      publishNotification.publish(
          payload.path("destinatario").asText(null),
          payload.path("assunto").asText(null),
          payload.path("corpo").asText(null),
          requestId);
      logger.log("ACCEPTED", requestId, elapsedMillis(startedAt), null);
      return HttpResponses.json(
          202, Map.of("status", "ACCEPTED", "requestId", requestId), requestId);
    } catch (IllegalArgumentException | JsonProcessingException exception) {
      logger.log("INVALID_REQUEST", requestId, elapsedMillis(startedAt), "INVALID_PAYLOAD");
      return HttpResponses.json(
          400,
          Map.of("code", "INVALID_REQUEST", "message", "Payload inválido.", "requestId", requestId),
          requestId);
    } catch (Exception exception) {
      logger.log("ERROR", requestId, elapsedMillis(startedAt), "PUBLISH_FAILED");
      return HttpResponses.json(
          503,
          Map.of(
              "code",
              "SERVICE_UNAVAILABLE",
              "message",
              "Não foi possível aceitar a notificação.",
              "requestId",
              requestId),
          requestId);
    }
  }

  private boolean authorized(APIGatewayV2HTTPEvent event) {
    String supplied = header(event == null ? null : event.getHeaders(), API_KEY_HEADER);
    return supplied != null
        && MessageDigest.isEqual(
            apiKey.getBytes(StandardCharsets.UTF_8), supplied.getBytes(StandardCharsets.UTF_8));
  }

  private static String requestBody(APIGatewayV2HTTPEvent event) {
    if (event == null || event.getBody() == null) {
      throw new IllegalArgumentException("body ausente");
    }
    return Boolean.TRUE.equals(event.getIsBase64Encoded())
        ? new String(Base64.getDecoder().decode(event.getBody()), StandardCharsets.UTF_8)
        : event.getBody();
  }

  private static String resolveRequestId(APIGatewayV2HTTPEvent event, Context context) {
    String apiRequestId =
        event == null || event.getRequestContext() == null
            ? null
            : event.getRequestContext().getRequestId();
    return RequestIdentity.resolve(
        event == null ? null : event.getHeaders(), apiRequestId, context.getAwsRequestId());
  }

  private static String header(Map<String, String> headers, String name) {
    if (headers == null) {
      return null;
    }
    return headers.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(Map.Entry::getValue)
        .findFirst()
        .orElse(null);
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
