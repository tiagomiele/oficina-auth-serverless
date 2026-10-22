package br.com.oficina.auth.handler;

import br.com.oficina.auth.application.port.in.DeliverNotification;
import br.com.oficina.auth.application.usecase.DeliverNotificationUseCase;
import br.com.oficina.auth.domain.NotificationMessage;
import br.com.oficina.auth.infrastructure.config.AuthComposition;
import br.com.oficina.auth.notification.NotificationSender;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

public final class NotificationDeliveryHandler implements RequestHandler<SNSEvent, String> {

  private static final String FUNCTION = "notification-delivery";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final DeliverNotification deliverNotification;
  private final Telemetry telemetry;

  public NotificationDeliveryHandler() {
    this(AuthComposition.notificationDelivery());
  }

  NotificationDeliveryHandler(NotificationSender sender, Telemetry telemetry) {
    this(
        new DeliverNotificationUseCase(
            notification ->
                sender.send(
                    br.com.oficina.auth.notification.NotificationMessage.fromDomain(notification))),
        telemetry);
  }

  NotificationDeliveryHandler(DeliverNotification deliverNotification, Telemetry telemetry) {
    this.deliverNotification = deliverNotification;
    this.telemetry = telemetry;
  }

  private NotificationDeliveryHandler(
      AuthComposition.NotificationDeliveryDependencies dependencies) {
    this(dependencies.useCase(), dependencies.telemetry());
  }

  @Override
  public String handleRequest(SNSEvent event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = context.getAwsRequestId();
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    try {
      if (event == null || event.getRecords() == null || event.getRecords().isEmpty()) {
        throw new IllegalArgumentException("Evento SNS vazio");
      }
      int processed = 0;
      for (SNSEvent.SNSRecord record : event.getRecords()) {
        NotificationMessage notification = parse(record);
        requestId = notification.requestId();
        telemetry.addAttribute("request.id", requestId);
        processed += deliverNotification.deliver(List.of(notification));
      }
      telemetry.addAttribute("notification.processed", Integer.toString(processed));
      logger.log("PROCESSED", requestId, elapsedMillis(startedAt), null);
      return "processed=" + processed;
    } catch (RuntimeException exception) {
      logger.log("ERROR", requestId, elapsedMillis(startedAt), "PROCESSING_FAILED");
      throw exception;
    }
  }

  private static NotificationMessage parse(SNSEvent.SNSRecord record) {
    if (record == null || record.getSNS() == null || record.getSNS().getMessage() == null) {
      throw new IllegalArgumentException("Registro SNS inválido");
    }
    try {
      return JSON.readValue(record.getSNS().getMessage(), NotificationMessage.class);
    } catch (JsonProcessingException exception) {
      throw new IllegalArgumentException("Mensagem SNS inválida", exception);
    }
  }

  static NotificationSender sender(String mode, Supplier<NotificationSender> sesSender) {
    return switch (mode.toLowerCase(Locale.ROOT)) {
      case "log" -> notification -> {};
      case "ses" -> sesSender.get();
      default -> throw new IllegalStateException("NOTIFICATION_DELIVERY_MODE inválido");
    };
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
