package br.com.oficina.auth.observability;

import java.util.Map;

public final class RequestIdentity {

  public static final String HEADER = "X-Request-Id";

  private RequestIdentity() {}

  /**
   * Usa o {@code X-Request-Id} do cliente quando presente e sanitizável, senão o identificador do
   * contexto do API Gateway e, por último, o identificador da invocação da Lambda.
   */
  public static String resolve(
      Map<String, String> headers, String apiGatewayRequestId, String lambdaRequestId) {
    String fromHeader = header(headers);
    if (fromHeader != null) {
      return fromHeader;
    }
    String fromContext = LogSanitizer.identifier(apiGatewayRequestId);
    return fromContext != null ? fromContext : LogSanitizer.identifier(lambdaRequestId);
  }

  private static String header(Map<String, String> headers) {
    if (headers == null) {
      return null;
    }
    for (Map.Entry<String, String> entry : headers.entrySet()) {
      if (entry.getKey() != null && HEADER.equalsIgnoreCase(entry.getKey())) {
        return LogSanitizer.identifier(entry.getValue());
      }
    }
    return null;
  }
}
