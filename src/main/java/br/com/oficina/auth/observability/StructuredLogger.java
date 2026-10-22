package br.com.oficina.auth.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Emite uma linha JSON por evento contendo somente campos técnicos. Qualquer valor é sanitizado
 * antes da serialização para impedir CPF, token ou dado de cliente no log.
 */
public final class StructuredLogger {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final String function;
  private final Telemetry telemetry;
  private final Consumer<String> sink;

  public StructuredLogger(String function, Telemetry telemetry, Consumer<String> sink) {
    this.function = function;
    this.telemetry = telemetry;
    this.sink = sink;
  }

  public void log(String outcome, String requestId, long durationMillis, String errorCode) {
    TraceContext trace = telemetry.currentTrace();
    Map<String, Object> event = new LinkedHashMap<>();
    event.put("function", LogSanitizer.identifier(function));
    event.put("outcome", LogSanitizer.code(outcome));
    event.put("requestId", LogSanitizer.identifier(requestId));
    event.put("traceId", trace.traceId());
    event.put("spanId", trace.spanId());
    event.put("durationMs", Math.max(durationMillis, 0));
    String sanitizedError = LogSanitizer.code(errorCode);
    if (sanitizedError != null) {
      event.put("errorCode", sanitizedError);
    }
    sink.accept(serialize(event));
  }

  private static String serialize(Map<String, Object> event) {
    try {
      return JSON.writeValueAsString(event);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar log estruturado", exception);
    }
  }
}
