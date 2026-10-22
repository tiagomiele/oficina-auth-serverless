package br.com.oficina.auth.adapter.out.newrelic;

import br.com.oficina.auth.application.port.out.AccessLogPublisherPort;
import br.com.oficina.auth.forwarder.ForwarderConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NewRelicAccessLogAdapter implements AccessLogPublisherPort {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private final ForwarderConfig config;
  private final HttpClient http;

  public NewRelicAccessLogAdapter(ForwarderConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(TIMEOUT).build());
  }

  public NewRelicAccessLogAdapter(ForwarderConfig config, HttpClient http) {
    this.config = config;
    this.http = http;
  }

  @Override
  public void publish(List<Map<String, Object>> sanitizedLogs) {
    if (sanitizedLogs.isEmpty()) {
      return;
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(config.endpoint()))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .header("Api-Key", config.licenseKey())
            .POST(HttpRequest.BodyPublishers.ofString(body(sanitizedLogs)))
            .build();
    HttpResponse<Void> response = execute(request);
    if (response.statusCode() >= 300) {
      throw new IllegalStateException(
          "New Relic Log API respondeu status " + response.statusCode());
    }
  }

  private String body(List<Map<String, Object>> sanitizedLogs) {
    Map<String, Object> commonAttributes = new LinkedHashMap<>();
    commonAttributes.put("logtype", config.logType());
    commonAttributes.put("environment", config.environment());
    commonAttributes.put("service.name", "oficina-auth-apigateway");

    List<Map<String, Object>> logs =
        sanitizedLogs.stream().map(NewRelicAccessLogAdapter::logEntry).toList();

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("common", Map.of("attributes", commonAttributes));
    payload.put("logs", logs);
    return serialize(List.of(payload));
  }

  private static Map<String, Object> logEntry(Map<String, Object> attributes) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("message", serialize(attributes));
    entry.put("attributes", attributes);
    return entry;
  }

  private HttpResponse<Void> execute(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Envio de logs interrompido", exception);
    }
  }

  private static String serialize(Object value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar log sanitizado", exception);
    }
  }
}
