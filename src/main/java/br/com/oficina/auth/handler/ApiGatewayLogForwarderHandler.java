package br.com.oficina.auth.handler;

import br.com.oficina.auth.forwarder.ApiGatewayLogForwarder;
import br.com.oficina.auth.forwarder.CloudWatchLogsPayload;
import br.com.oficina.auth.infrastructure.config.AuthComposition;
import br.com.oficina.auth.observability.StructuredLogger;
import br.com.oficina.auth.observability.Telemetry;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import java.util.Map;

public final class ApiGatewayLogForwarderHandler
    implements RequestHandler<Map<String, Object>, String> {

  private static final String FUNCTION = "apigateway-log-forwarder";

  private final ApiGatewayLogForwarder forwarder;
  private final Telemetry telemetry;

  public ApiGatewayLogForwarderHandler() {
    this(AuthComposition.logForwarder());
  }

  ApiGatewayLogForwarderHandler(ApiGatewayLogForwarder forwarder, Telemetry telemetry) {
    this.forwarder = forwarder;
    this.telemetry = telemetry;
  }

  private ApiGatewayLogForwarderHandler(AuthComposition.LogForwarderDependencies dependencies) {
    this(dependencies.forwarder(), dependencies.telemetry());
  }

  @Override
  public String handleRequest(Map<String, Object> event, Context context) {
    long startedAt = System.nanoTime();
    String requestId = context.getAwsRequestId();
    StructuredLogger logger =
        new StructuredLogger(FUNCTION, telemetry, line -> context.getLogger().log(line + "\n"));
    try {
      int forwarded = forwarder.forward(CloudWatchLogsPayload.fromEvent(event));
      telemetry.addAttribute("forwarded.records", Integer.toString(forwarded));
      logger.log("FORWARDED", requestId, elapsedMillis(startedAt), null);
      return "forwarded=" + forwarded;
    } catch (RuntimeException exception) {
      logger.log("ERROR", requestId, elapsedMillis(startedAt), "FORWARDING_FAILED");
      throw exception;
    }
  }

  private static long elapsedMillis(long startedAt) {
    return (System.nanoTime() - startedAt) / 1_000_000L;
  }
}
