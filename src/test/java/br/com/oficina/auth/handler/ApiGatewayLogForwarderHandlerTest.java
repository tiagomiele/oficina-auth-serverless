package br.com.oficina.auth.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.oficina.auth.forwarder.ApiGatewayLogForwarder;
import br.com.oficina.auth.observability.NoopTelemetry;
import br.com.oficina.auth.support.RecordingContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class ApiGatewayLogForwarderHandlerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private final RecordingContext context = new RecordingContext("lambda-request-1");

  @Test
  void logsForwardedRecordsWithoutSensitiveData() throws Exception {
    ApiGatewayLogForwarderHandler handler =
        new ApiGatewayLogForwarderHandler(
            new ApiGatewayLogForwarder(sent::addAll), new NoopTelemetry());

    String result = handler.handleRequest(event(), context);

    assertEquals("forwarded=1", result);
    assertEquals(1, sent.size());
    JsonNode log = JSON.readTree(context.logLines().get(0));
    assertEquals("apigateway-log-forwarder", log.get("function").asText());
    assertEquals("FORWARDED", log.get("outcome").asText());
    assertFalse(context.logLines().toString().contains("Bearer"));
  }

  @Test
  void logsErrorCodeWhenForwardingFails() throws Exception {
    ApiGatewayLogForwarderHandler handler =
        new ApiGatewayLogForwarderHandler(
            new ApiGatewayLogForwarder(
                logs -> {
                  throw new IllegalStateException("New Relic Log API respondeu status 500");
                }),
            new NoopTelemetry());

    assertThrows(IllegalStateException.class, () -> handler.handleRequest(event(), context));

    JsonNode log = JSON.readTree(context.logLines().get(0));
    assertEquals("ERROR", log.get("outcome").asText());
    assertEquals("FORWARDING_FAILED", log.get("errorCode").asText());
  }

  private static Map<String, Object> event() {
    String json =
        """
        {"messageType":"DATA_MESSAGE","logGroup":"/aws/apigateway/oficina-auth-homolog",
         "logEvents":[{"id":"1","timestamp":1,"message":"{\\"requestId\\":\\"a\\",\\"status\\":200,\\"authorization\\":\\"Bearer x\\"}"}]}
        """;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write(json.getBytes(StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return Map.of(
        "awslogs", Map.of("data", Base64.getEncoder().encodeToString(buffer.toByteArray())));
  }
}
