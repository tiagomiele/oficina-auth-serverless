package br.com.oficina.auth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.infrastructure.JwtService;
import br.com.oficina.auth.observability.NoopTelemetry;
import br.com.oficina.auth.support.RecordingContext;
import br.com.oficina.auth.support.TestKeys;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.KeyPair;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JwtAuthorizerHandlerTest {

  private static final String ISSUER = "oficina-auth-serverless";
  private static final String AUDIENCE = "oficina-backend";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final KeyPair pair = TestKeys.generate();
  private final AuthConfig config = TestKeys.config(pair, ISSUER, AUDIENCE, 900);
  private final RecordingContext context = new RecordingContext("lambda-request-1");

  @Test
  @SuppressWarnings("unchecked")
  void allowsValidTokenAndExposesClientContext() {
    String token = new JwtService(config).issue(42L);

    Map<String, Object> result = handler().handleRequest(event("Bearer " + token), context);

    assertEquals(Boolean.TRUE, result.get("isAuthorized"));
    Map<String, Object> authorizerContext = (Map<String, Object>) result.get("context");
    assertEquals("42", authorizerContext.get("subject"));
    assertEquals(42L, authorizerContext.get("clientId"));
    assertEquals("CLIENTE", authorizerContext.get("role"));
    assertEquals("gateway-request-1", authorizerContext.get("requestId"));
    assertLog("ALLOW", null);
  }

  @Test
  void allowsTokenSentByIdentitySource() {
    String token = new JwtService(config).issue(7L);

    Map<String, Object> result =
        handler()
            .handleRequest(
                Map.of("identitySource", List.of("Bearer " + token), "requestContext", Map.of()),
                context);

    assertEquals(Boolean.TRUE, result.get("isAuthorized"));
  }

  @Test
  void deniesMissingToken() {
    assertDenied(handler().handleRequest(Map.of("headers", Map.of()), context), "TOKEN_MISSING");
  }

  @Test
  void deniesTokenWithoutBearerScheme() {
    assertDenied(handler().handleRequest(event("Token abc"), context), "TOKEN_SCHEME_INVALID");
  }

  @Test
  void deniesMalformedToken() {
    assertDenied(handler().handleRequest(event("Bearer not-a-jwt"), context), "TOKEN_MALFORMED");
  }

  @Test
  void deniesTokenSignedByAnotherKey() {
    String token =
        new JwtService(TestKeys.config(TestKeys.generate(), ISSUER, AUDIENCE, 900)).issue(42L);

    assertDenied(
        handler().handleRequest(event("Bearer " + token), context), "TOKEN_SIGNATURE_INVALID");
  }

  @Test
  void deniesExpiredToken() {
    String token = new JwtService(TestKeys.config(pair, ISSUER, AUDIENCE, -60)).issue(42L);

    assertDenied(handler().handleRequest(event("Bearer " + token), context), "TOKEN_EXPIRED");
  }

  @Test
  void deniesTokenFromAnotherIssuer() {
    String token = new JwtService(TestKeys.config(pair, "outro-emissor", AUDIENCE, 900)).issue(42L);

    assertDenied(
        handler().handleRequest(event("Bearer " + token), context), "TOKEN_ISSUER_INVALID");
  }

  @Test
  void deniesTokenForAnotherAudience() {
    String token = new JwtService(TestKeys.config(pair, ISSUER, "outra-audiencia", 900)).issue(42L);

    assertDenied(
        handler().handleRequest(event("Bearer " + token), context), "TOKEN_AUDIENCE_INVALID");
  }

  @Test
  void neverLogsTheToken() {
    String token = new JwtService(config).issue(42L);

    handler().handleRequest(event("Bearer " + token), context);

    String logs = String.join("\n", context.logLines());
    assertFalse(logs.contains(token));
    assertFalse(logs.contains("Bearer"));
  }

  private JwtAuthorizerHandler handler() {
    return new JwtAuthorizerHandler(new JwtService(config), new NoopTelemetry());
  }

  private void assertDenied(Map<String, Object> result, String errorCode) {
    assertEquals(Boolean.FALSE, result.get("isAuthorized"));
    assertLog("DENY", errorCode);
  }

  private void assertLog(String outcome, String errorCode) {
    try {
      JsonNode event = JSON.readTree(context.logLines().get(0));
      assertEquals("auth-jwt-authorizer", event.get("function").asText());
      assertEquals(outcome, event.get("outcome").asText());
      if (errorCode == null) {
        assertFalse(event.has("errorCode"));
      } else {
        assertEquals(errorCode, event.get("errorCode").asText());
      }
      assertTrue(event.get("durationMs").asLong() >= 0);
    } catch (Exception exception) {
      throw new AssertionError("Log estruturado inválido", exception);
    }
  }

  private static Map<String, Object> event(String authorization) {
    return Map.of(
        "headers",
        Map.of("Authorization", authorization),
        "requestContext",
        Map.of("requestId", "gateway-request-1"));
  }
}
