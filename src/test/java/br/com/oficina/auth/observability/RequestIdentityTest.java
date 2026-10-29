package br.com.oficina.auth.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class RequestIdentityTest {

  @Test
  void prefersClientHeaderIgnoringCase() {
    assertEquals(
        "client-123",
        RequestIdentity.resolve(Map.of("x-request-id", "client-123"), "gateway-1", "lambda-1"));
  }

  @Test
  void fallsBackToApiGatewayContext() {
    assertEquals("gateway-1", RequestIdentity.resolve(Map.of(), "gateway-1", "lambda-1"));
    assertEquals("gateway-1", RequestIdentity.resolve(null, "gateway-1", "lambda-1"));
  }

  @Test
  void fallsBackToLambdaRequestId() {
    assertEquals(
        "lambda-1", RequestIdentity.resolve(Map.of("x-request-id", "   "), null, "lambda-1"));
  }

  @Test
  void redactsDocumentSentInHeader() {
    assertEquals(
        LogSanitizer.REDACTED,
        RequestIdentity.resolve(Map.of("X-Request-Id", "529.982.247-25"), "gateway-1", "lambda-1"));
  }
}
