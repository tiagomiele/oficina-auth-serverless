package br.com.oficina.auth.handler;

import br.com.oficina.auth.application.port.in.AuthorizeToken;
import br.com.oficina.auth.application.usecase.AuthorizeTokenUseCase;
import br.com.oficina.auth.domain.AuthorizedClient;
import br.com.oficina.auth.domain.TokenValidationException;
import br.com.oficina.auth.infrastructure.JwtService;
import br.com.oficina.auth.infrastructure.config.AuthComposition;
import br.com.oficina.auth.observability.RequestIdentity;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JwtAuthorizerHandler
    implements RequestHandler<Map<String, Object>, Map<String, Object>> {

  private static final String FUNCTION = "auth-jwt-authorizer";

  private final AuthorizeToken authorizeToken;
  private final Telemetry telemetry;

  public JwtAuthorizerHandler() {
    this(AuthComposition.authorizer());
  }

  JwtAuthorizerHandler(JwtService tokens, Telemetry telemetry) {
    this(new AuthorizeTokenUseCase(tokens), telemetry);
  }

  JwtAuthorizerHandler(AuthorizeToken authorizeToken, Telemetry telemetry) {
    this.authorizeToken = authorizeToken;
    this.telemetry = telemetry;
  }

  private JwtAuthorizerHandler(AuthComposition.AuthorizerDependencies dependencies) {
    this(dependencies.useCase(), dependencies.telemetry());
  }

  @Override
  public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = resolveRequestId(event, context);
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    telemetry.addAttribute("request.id", requestId);
    try {
      AuthorizedClient client = authorizeToken.authorize(bearerToken(event));
      Map<String, Object> authorizerContext = new LinkedHashMap<>();
      authorizerContext.put("subject", client.subject());
      authorizerContext.put("clientId", client.clientId());
      authorizerContext.put("role", client.role());
      authorizerContext.put("requestId", requestId);
      telemetry.addAttribute("authorization.outcome", "ALLOW");
      logger.log("ALLOW", requestId, elapsedMillis(startedAt), null);
      return Map.of("isAuthorized", true, "context", authorizerContext);
    } catch (TokenValidationException exception) {
      return deny(logger, requestId, startedAt, exception.code());
    } catch (RuntimeException exception) {
      return deny(logger, requestId, startedAt, "AUTHORIZATION_ERROR");
    }
  }

  private Map<String, Object> deny(
      StructuredLogger logger, String requestId, long startedAt, String errorCode) {
    telemetry.addAttribute("authorization.outcome", "DENY");
    logger.log("DENY", requestId, elapsedMillis(startedAt), errorCode);
    return Map.of("isAuthorized", false, "context", Map.of("requestId", String.valueOf(requestId)));
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }

  private static String resolveRequestId(Map<String, Object> event, Context context) {
    return RequestIdentity.resolve(
        stringHeaders(event), apiGatewayRequestId(event), context.getAwsRequestId());
  }

  private static Map<String, String> stringHeaders(Map<String, Object> event) {
    if (event == null || !(event.get("headers") instanceof Map<?, ?> values)) {
      return Map.of();
    }
    Map<String, String> headers = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : values.entrySet()) {
      if (entry.getKey() instanceof String key && entry.getValue() instanceof String value) {
        headers.put(key, value);
      }
    }
    return headers;
  }

  private static String apiGatewayRequestId(Map<String, Object> event) {
    if (event != null
        && event.get("requestContext") instanceof Map<?, ?> requestContext
        && requestContext.get("requestId") instanceof String requestId) {
      return requestId;
    }
    return null;
  }

  private static String bearerToken(Map<String, Object> event) {
    if (event == null) {
      throw new TokenValidationException("TOKEN_MISSING");
    }
    if (event.get("authorizationToken") instanceof String value) {
      return stripBearer(value);
    }
    String authorization =
        stringHeaders(event).entrySet().stream()
            .filter(entry -> "authorization".equalsIgnoreCase(entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(null);
    if (authorization != null) {
      return stripBearer(authorization);
    }
    if (event.get("identitySource") instanceof Collection<?> values && !values.isEmpty()) {
      Object first = values.iterator().next();
      if (first instanceof String value) {
        return stripBearer(value);
      }
    }
    throw new TokenValidationException("TOKEN_MISSING");
  }

  private static String stripBearer(String value) {
    if (value == null || value.isBlank()) {
      throw new TokenValidationException("TOKEN_MISSING");
    }
    if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) {
      throw new TokenValidationException("TOKEN_SCHEME_INVALID");
    }
    return value.substring(7).trim();
  }
}
