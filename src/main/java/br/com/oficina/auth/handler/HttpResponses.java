package br.com.oficina.auth.handler;

import br.com.oficina.auth.observability.RequestIdentity;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;

final class HttpResponses {

  private static final ObjectMapper JSON = new ObjectMapper();

  private HttpResponses() {}

  static APIGatewayV2HTTPResponse json(int status, Object body, String requestId) {
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Content-Type", "application/json");
    headers.put("Cache-Control", "no-store");
    if (requestId != null) {
      headers.put(RequestIdentity.HEADER, requestId);
    }
    try {
      return APIGatewayV2HTTPResponse.builder()
          .withStatusCode(status)
          .withHeaders(headers)
          .withBody(JSON.writeValueAsString(body))
          .build();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao serializar resposta", exception);
    }
  }
}
