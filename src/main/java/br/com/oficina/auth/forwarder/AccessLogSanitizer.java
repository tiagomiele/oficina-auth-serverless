package br.com.oficina.auth.forwarder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mantém apenas os campos técnicos do log de acesso do API Gateway. Cabeçalhos, corpo, token e
 * qualquer campo desconhecido são descartados antes do envio.
 */
public final class AccessLogSanitizer {

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final List<String> ALLOWED_FIELDS =
      List.of(
          "requestId",
          "environment",
          "apiId",
          "stage",
          "routeKey",
          "httpMethod",
          "path",
          "protocol",
          "status",
          "integrationStatus",
          "integrationErrorMessage",
          "integrationLatency",
          "responseLatency",
          "responseLength",
          "errorMessage",
          "authorizerError");

  private AccessLogSanitizer() {}

  public static Optional<Map<String, Object>> sanitize(String message) {
    JsonNode root = parse(message);
    if (root == null || !root.isObject()) {
      return Optional.empty();
    }
    Map<String, Object> sanitized = new LinkedHashMap<>();
    for (String field : ALLOWED_FIELDS) {
      JsonNode value = root.get(field);
      if (value == null || value.isNull() || value.isContainerNode()) {
        continue;
      }
      if (value.isNumber()) {
        sanitized.put(field, value.numberValue());
      } else {
        String text = value.asText();
        if (!text.isBlank() && !"-".equals(text)) {
          sanitized.put(field, text);
        }
      }
    }
    return sanitized.isEmpty() ? Optional.empty() : Optional.of(sanitized);
  }

  private static JsonNode parse(String message) {
    if (message == null || message.isBlank()) {
      return null;
    }
    try {
      return JSON.readTree(message);
    } catch (JsonProcessingException exception) {
      return null;
    }
  }
}
