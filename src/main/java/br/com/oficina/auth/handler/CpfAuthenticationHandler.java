package br.com.oficina.auth.handler;

import br.com.oficina.auth.application.ClienteDirectory;
import br.com.oficina.auth.application.port.in.AuthenticateClient;
import br.com.oficina.auth.application.usecase.AuthenticateClientUseCase;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.infrastructure.ClienteRepository;
import br.com.oficina.auth.infrastructure.JwtService;
import br.com.oficina.auth.infrastructure.config.AuthComposition;
import br.com.oficina.auth.observability.RequestIdentity;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

public final class CpfAuthenticationHandler
    implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private static final String FUNCTION = "auth-cpf-login";
  private static final ObjectMapper JSON = new ObjectMapper();

  private final AuthenticateClient authenticateClient;
  private final Telemetry telemetry;

  public CpfAuthenticationHandler() {
    this(AuthComposition.login());
  }

  CpfAuthenticationHandler(AuthConfig config) {
    this(
        new AuthenticateClientUseCase(
            new ClienteRepository(config), new JwtService(config), config.jwtTtlSeconds()),
        Telemetry.fromEnvironment());
  }

  CpfAuthenticationHandler(
      ClienteDirectory clients, JwtService tokens, long ttlSeconds, Telemetry telemetry) {
    this(new AuthenticateClientUseCase(clients, tokens, ttlSeconds), telemetry);
  }

  CpfAuthenticationHandler(AuthenticateClient authenticateClient, Telemetry telemetry) {
    this.authenticateClient = authenticateClient;
    this.telemetry = telemetry;
  }

  private CpfAuthenticationHandler(AuthComposition.LoginDependencies dependencies) {
    this(dependencies.useCase(), dependencies.telemetry());
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = resolveRequestId(event, context);
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    telemetry.addAttribute("request.id", requestId);
    Invocation invocation = new Invocation(logger, requestId, startedAt);
    try {
      String cpfRaw = requestCpf(event);
      if (cpfRaw == null || cpfRaw.isBlank()) {
        return invocation.finish(
            "INVALID_REQUEST",
            "MISSING_CPF",
            error(400, "INVALID_REQUEST", "Informe o campo cpf.", requestId));
      }
      return authenticateClient
          .authenticate(cpfRaw)
          .map(
              result ->
                  invocation.finish(
                      "SUCCESS",
                      null,
                      HttpResponses.json(
                          200,
                          Map.of(
                              "accessToken",
                              result.accessToken(),
                              "tokenType",
                              result.tokenType(),
                              "expiresIn",
                              result.expiresIn()),
                          requestId)))
          .orElseGet(
              () ->
                  invocation.finish(
                      "DENIED", "CLIENT_NOT_ELIGIBLE", authenticationFailed(requestId)));
    } catch (JsonProcessingException exception) {
      return invocation.finish(
          "INVALID_REQUEST",
          "MALFORMED_JSON",
          error(400, "INVALID_REQUEST", "Corpo JSON inválido.", requestId));
    } catch (IllegalArgumentException exception) {
      return invocation.finish("DENIED", "INVALID_CPF", authenticationFailed(requestId));
    } catch (RuntimeException exception) {
      return invocation.finish(
          "ERROR",
          "INTERNAL_ERROR",
          error(500, "INTERNAL_ERROR", "Não foi possível processar a autenticação.", requestId));
    }
  }

  private final class Invocation {

    private final StructuredLogger logger;
    private final String requestId;
    private final long startedAt;

    private Invocation(StructuredLogger logger, String requestId, long startedAt) {
      this.logger = logger;
      this.requestId = requestId;
      this.startedAt = startedAt;
    }

    private APIGatewayV2HTTPResponse finish(
        String outcome, String errorCode, APIGatewayV2HTTPResponse response) {
      telemetry.addAttribute("auth.outcome", outcome);
      logger.log(outcome, requestId, (System.nanoTime() - startedAt) / 1_000_000L, errorCode);
      return response;
    }
  }

  private static String resolveRequestId(APIGatewayV2HTTPEvent event, Context context) {
    Map<String, String> headers = event == null ? null : event.getHeaders();
    String apiGatewayRequestId =
        event == null || event.getRequestContext() == null
            ? null
            : event.getRequestContext().getRequestId();
    return RequestIdentity.resolve(headers, apiGatewayRequestId, context.getAwsRequestId());
  }

  private static String requestCpf(APIGatewayV2HTTPEvent event) throws JsonProcessingException {
    if (event == null || event.getBody() == null) {
      return null;
    }
    String body =
        Boolean.TRUE.equals(event.getIsBase64Encoded())
            ? new String(Base64.getDecoder().decode(event.getBody()), StandardCharsets.UTF_8)
            : event.getBody();
    return JSON.readTree(body).path("cpf").asText(null);
  }

  private static APIGatewayV2HTTPResponse authenticationFailed(String requestId) {
    return error(401, "AUTHENTICATION_FAILED", "Não foi possível autenticar o cliente.", requestId);
  }

  private static APIGatewayV2HTTPResponse error(
      int status, String code, String message, String requestId) {
    return HttpResponses.json(
        status, Map.of("code", code, "message", message, "requestId", requestId), requestId);
  }
}
