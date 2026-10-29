package br.com.oficina.auth.infrastructure.config;

import br.com.oficina.auth.adapter.out.jdbc.JdbcClientRepository;
import br.com.oficina.auth.adapter.out.jwt.JwtTokenAdapter;
import br.com.oficina.auth.adapter.out.newrelic.NewRelicAccessLogAdapter;
import br.com.oficina.auth.adapter.out.ses.SesNotificationAdapter;
import br.com.oficina.auth.adapter.out.sns.SnsNotificationAdapter;
import br.com.oficina.auth.application.port.in.AuthenticateClient;
import br.com.oficina.auth.application.port.in.AuthorizeToken;
import br.com.oficina.auth.application.port.in.DeliverNotification;
import br.com.oficina.auth.application.port.in.PublishNotification;
import br.com.oficina.auth.application.port.out.NotificationSenderPort;
import br.com.oficina.auth.application.usecase.AuthenticateClientUseCase;
import br.com.oficina.auth.application.usecase.AuthorizeTokenUseCase;
import br.com.oficina.auth.application.usecase.DeliverNotificationUseCase;
import br.com.oficina.auth.application.usecase.PublishNotificationUseCase;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.forwarder.ApiGatewayLogForwarder;
import br.com.oficina.auth.forwarder.ForwarderConfig;
import br.com.oficina.auth.observability.Telemetry;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;
import software.amazon.awssdk.services.sesv2.SesV2Client;
import software.amazon.awssdk.services.sns.SnsClient;

public final class AuthComposition {

  private AuthComposition() {}

  public static LoginDependencies login() {
    AuthConfig config = AuthConfig.loginFromEnvironment();
    JwtTokenAdapter tokens = new JwtTokenAdapter(config);
    return new LoginDependencies(
        new AuthenticateClientUseCase(
            new JdbcClientRepository(config), tokens, config.jwtTtlSeconds()),
        Telemetry.fromEnvironment());
  }

  public static AuthorizerDependencies authorizer() {
    JwtTokenAdapter tokens = new JwtTokenAdapter(AuthConfig.authorizerFromEnvironment());
    return new AuthorizerDependencies(
        new AuthorizeTokenUseCase(tokens), Telemetry.fromEnvironment());
  }

  public static NotificationIngressDependencies notificationIngress() {
    PublishNotification useCase =
        new PublishNotificationUseCase(
            new SnsNotificationAdapter(
                notificationClient(), requiredEnvironment("NOTIFICATION_TOPIC_ARN")));
    return new NotificationIngressDependencies(
        useCase, requiredEnvironment("NOTIFICATION_API_KEY"), Telemetry.fromEnvironment());
  }

  public static NotificationDeliveryDependencies notificationDelivery() {
    String mode = System.getenv().getOrDefault("NOTIFICATION_DELIVERY_MODE", "log");
    NotificationSenderPort sender =
        notificationSender(
            mode,
            () ->
                new SesNotificationAdapter(
                    SesV2Client.create(), requiredEnvironment("NOTIFICATION_SOURCE_EMAIL")));
    return new NotificationDeliveryDependencies(
        new DeliverNotificationUseCase(sender), Telemetry.fromEnvironment());
  }

  public static LogForwarderDependencies logForwarder() {
    return new LogForwarderDependencies(
        new ApiGatewayLogForwarder(new NewRelicAccessLogAdapter(ForwarderConfig.fromEnvironment())),
        Telemetry.fromEnvironment());
  }

  public static NotificationSenderPort notificationSender(
      String mode, Supplier<NotificationSenderPort> sesSender) {
    return switch (mode.toLowerCase(Locale.ROOT)) {
      case "log" -> notification -> {};
      case "ses" -> sesSender.get();
      default -> throw new IllegalStateException("NOTIFICATION_DELIVERY_MODE inválido");
    };
  }

  private static SnsClient notificationClient() {
    return SnsClient.builder()
        .overrideConfiguration(
            builder ->
                builder
                    .apiCallAttemptTimeout(Duration.ofSeconds(3))
                    .apiCallTimeout(Duration.ofSeconds(8)))
        .build();
  }

  private static String requiredEnvironment(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " não configurada");
    }
    return value;
  }

  public record LoginDependencies(AuthenticateClient useCase, Telemetry telemetry) {}

  public record AuthorizerDependencies(AuthorizeToken useCase, Telemetry telemetry) {}

  public record NotificationIngressDependencies(
      PublishNotification useCase, String apiKey, Telemetry telemetry) {}

  public record NotificationDeliveryDependencies(
      DeliverNotification useCase, Telemetry telemetry) {}

  public record LogForwarderDependencies(ApiGatewayLogForwarder forwarder, Telemetry telemetry) {}
}
