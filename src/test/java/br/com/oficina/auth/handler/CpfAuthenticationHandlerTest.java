package br.com.oficina.auth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.oficina.auth.application.ClienteDirectory;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.infrastructure.JwtService;
import br.com.oficina.auth.observability.NoopTelemetry;
import br.com.oficina.auth.observability.RequestIdentity;
import br.com.oficina.auth.support.RecordingContext;
import br.com.oficina.auth.support.TestKeys;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CpfAuthenticationHandlerTest {

  private static final String VALID_CPF = "529.982.247-25";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final AuthConfig config =
      TestKeys.config(TestKeys.generate(), "oficina-auth-serverless", "oficina-backend", 900);
  private final RecordingContext context = new RecordingContext("lambda-request-1");

  @Test
  void issuesTokenForActiveClient() throws Exception {
    APIGatewayV2HTTPResponse response =
        handler(cpf -> Optional.of(42L))
            .handleRequest(event("{\"cpf\":\"" + VALID_CPF + "\"}", "client-1"), context);

    assertEquals(200, response.getStatusCode());
    assertEquals("client-1", response.getHeaders().get(RequestIdentity.HEADER));
    JsonNode body = JSON.readTree(response.getBody());
    assertEquals("Bearer", body.get("tokenType").asText());
    assertEquals(900, body.get("expiresIn").asLong());
    assertNotNull(body.get("accessToken").asText());
    assertLog("SUCCESS", null);
  }

  @Test
  void deniesUnknownOrInactiveClient() {
    APIGatewayV2HTTPResponse response =
        handler(cpf -> Optional.empty())
            .handleRequest(event("{\"cpf\":\"" + VALID_CPF + "\"}", null), context);

    assertEquals(401, response.getStatusCode());
    assertTrue(response.getBody().contains("AUTHENTICATION_FAILED"));
    assertLog("DENIED", "CLIENT_NOT_ELIGIBLE");
  }

  @Test
  void deniesInvalidCpfWithGenericResponse() {
    APIGatewayV2HTTPResponse response =
        handler(cpf -> Optional.of(42L))
            .handleRequest(event("{\"cpf\":\"11111111111\"}", null), context);

    assertEquals(401, response.getStatusCode());
    assertLog("DENIED", "INVALID_CPF");
  }

  @Test
  void rejectsMissingCpfField() {
    APIGatewayV2HTTPResponse response =
        handler(cpf -> Optional.of(42L)).handleRequest(event("{}", null), context);

    assertEquals(400, response.getStatusCode());
    assertLog("INVALID_REQUEST", "MISSING_CPF");
  }

  @Test
  void rejectsInvalidJson() {
    APIGatewayV2HTTPResponse response =
        handler(cpf -> Optional.of(42L)).handleRequest(event("{\"cpf\":", null), context);

    assertEquals(400, response.getStatusCode());
    assertLog("INVALID_REQUEST", "MALFORMED_JSON");
  }

  @Test
  void returnsInternalErrorWhenDirectoryFails() {
    APIGatewayV2HTTPResponse response =
        handler(
                cpf -> {
                  throw new IllegalStateException("banco indisponível");
                })
            .handleRequest(event("{\"cpf\":\"" + VALID_CPF + "\"}", null), context);

    assertEquals(500, response.getStatusCode());
    assertLog("ERROR", "INTERNAL_ERROR");
  }

  @Test
  void neverLogsCpfOrToken() {
    handler(cpf -> Optional.of(42L))
        .handleRequest(event("{\"cpf\":\"" + VALID_CPF + "\"}", null), context);

    String logs = String.join("\n", context.logLines());
    assertFalse(logs.contains("52998224725"));
    assertFalse(logs.contains(VALID_CPF));
    assertFalse(logs.contains("accessToken"));
    assertFalse(logs.contains("eyJ"));
  }

  private CpfAuthenticationHandler handler(ClienteDirectory clients) {
    return new CpfAuthenticationHandler(
        clients, new JwtService(config), config.jwtTtlSeconds(), new NoopTelemetry());
  }

  private void assertLog(String outcome, String errorCode) {
    try {
      JsonNode event = JSON.readTree(context.logLines().get(0));
      assertEquals("auth-cpf-login", event.get("function").asText());
      assertEquals(outcome, event.get("outcome").asText());
      if (errorCode == null) {
        assertFalse(event.has("errorCode"));
      } else {
        assertEquals(errorCode, event.get("errorCode").asText());
      }
    } catch (Exception exception) {
      throw new AssertionError("Log estruturado inválido", exception);
    }
  }

  private static APIGatewayV2HTTPEvent event(String body, String requestIdHeader) {
    APIGatewayV2HTTPEvent.RequestContext requestContext =
        APIGatewayV2HTTPEvent.RequestContext.builder().withRequestId("gateway-request-1").build();
    return APIGatewayV2HTTPEvent.builder()
        .withBody(body)
        .withHeaders(requestIdHeader == null ? Map.of() : Map.of("x-request-id", requestIdHeader))
        .withRequestContext(requestContext)
        .build();
  }
}
