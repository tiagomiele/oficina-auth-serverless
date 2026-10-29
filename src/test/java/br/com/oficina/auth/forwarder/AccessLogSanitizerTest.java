package br.com.oficina.auth.forwarder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AccessLogSanitizerTest {

  @Test
  void keepsOnlyTechnicalFields() {
    String message =
        """
        {"requestId":"abc123","routeKey":"POST /auth/cpf","status":"200",
         "integrationStatus":"200","integrationErrorMessage":"-","responseLatency":"87",
         "environment":"homolog","authorization":"Bearer token","body":"{\\"cpf\\":\\"52998224725\\"}",
         "headers":{"Authorization":"Bearer token"},"sourceIp":"200.1.1.1"}
        """;

    Map<String, Object> sanitized = AccessLogSanitizer.sanitize(message).orElseThrow();

    assertEquals("abc123", sanitized.get("requestId"));
    assertEquals("POST /auth/cpf", sanitized.get("routeKey"));
    assertEquals("200", sanitized.get("status"));
    assertEquals("homolog", sanitized.get("environment"));
    assertFalse(sanitized.containsKey("authorization"));
    assertFalse(sanitized.containsKey("body"));
    assertFalse(sanitized.containsKey("headers"));
    assertFalse(sanitized.containsKey("sourceIp"));
    assertFalse(sanitized.containsKey("integrationErrorMessage"));
    assertFalse(sanitized.toString().contains("52998224725"));
  }

  @Test
  void keepsNumericFields() {
    Map<String, Object> sanitized =
        AccessLogSanitizer.sanitize("{\"requestId\":\"abc\",\"status\":502,\"responseLatency\":12}")
            .orElseThrow();

    assertEquals(502, ((Number) sanitized.get("status")).intValue());
    assertEquals(12, ((Number) sanitized.get("responseLatency")).intValue());
  }

  @Test
  void skipsNonJsonAndUnknownPayloads() {
    assertTrue(AccessLogSanitizer.sanitize("START RequestId: 1 Version: $LATEST").isEmpty());
    assertTrue(AccessLogSanitizer.sanitize("{\"unexpected\":\"value\"}").isEmpty());
    assertEquals(Optional.empty(), AccessLogSanitizer.sanitize(null));
    assertEquals(Optional.empty(), AccessLogSanitizer.sanitize("   "));
  }
}
