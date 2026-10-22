package br.com.oficina.auth.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructuredLoggerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final List<String> lines = new ArrayList<>();

  @Test
  void emitsOnlyTechnicalFields() throws Exception {
    logger(new TraceContext("trace-1", "span-1")).log("SUCCESS", "req-1", 12, null);

    JsonNode event = JSON.readTree(lines.get(0));

    assertEquals(
        Set.of("function", "outcome", "requestId", "traceId", "spanId", "durationMs"),
        fieldNames(event));
    assertEquals("auth-cpf-login", event.get("function").asText());
    assertEquals("SUCCESS", event.get("outcome").asText());
    assertEquals("req-1", event.get("requestId").asText());
    assertEquals("trace-1", event.get("traceId").asText());
    assertEquals("span-1", event.get("spanId").asText());
    assertEquals(12, event.get("durationMs").asLong());
  }

  @Test
  void includesErrorCodeOnlyWhenPresent() throws Exception {
    logger(TraceContext.empty()).log("DENIED", "req-2", 5, "token expired");

    JsonNode event = JSON.readTree(lines.get(0));

    assertEquals("TOKEN_EXPIRED", event.get("errorCode").asText());
    assertTrue(event.get("traceId").isNull());
    assertTrue(event.get("spanId").isNull());
  }

  @Test
  void redactsDocumentLikeValues() throws Exception {
    logger(TraceContext.empty())
        .log("DENIED 52998224725", "cpf 529.982.247-25", 1, "CPF52998224725");

    String line = lines.get(0);
    JsonNode event = JSON.readTree(line);

    assertFalse(line.contains("52998224725"));
    assertFalse(line.contains("529.982.247-25"));
    assertTrue(event.get("requestId").asText().contains(LogSanitizer.REDACTED));
    assertTrue(event.get("outcome").asText().contains(LogSanitizer.REDACTED));
    assertTrue(event.get("errorCode").asText().contains(LogSanitizer.REDACTED));
  }

  @Test
  void normalizesNegativeDuration() throws Exception {
    logger(TraceContext.empty()).log("ERROR", "req-3", -10, "INTERNAL_ERROR");

    assertEquals(0, JSON.readTree(lines.get(0)).get("durationMs").asLong());
  }

  private StructuredLogger logger(TraceContext trace) {
    Telemetry telemetry =
        new Telemetry() {
          @Override
          public TraceContext currentTrace() {
            return trace;
          }

          @Override
          public void addAttribute(String name, String value) {}
        };
    return new StructuredLogger("auth-cpf-login", telemetry, lines::add);
  }

  private static Set<String> fieldNames(JsonNode event) {
    Set<String> names = new LinkedHashSet<>();
    event.fieldNames().forEachRemaining(names::add);
    return names;
  }
}
