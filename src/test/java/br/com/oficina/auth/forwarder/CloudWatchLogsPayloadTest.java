package br.com.oficina.auth.forwarder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

class CloudWatchLogsPayloadTest {

  @Test
  void decodesDataMessages() {
    String data =
        encode(
            """
            {"messageType":"DATA_MESSAGE","logGroup":"/aws/apigateway/oficina-auth-homolog",
             "logEvents":[{"id":"1","timestamp":1700000000000,"message":"{\\"requestId\\":\\"a\\"}"},
                          {"id":"2","timestamp":1700000000001,"message":"  "}]}
            """);

    CloudWatchLogsPayload payload =
        CloudWatchLogsPayload.fromEvent(Map.of("awslogs", Map.of("data", data)));

    assertEquals("/aws/apigateway/oficina-auth-homolog", payload.logGroup());
    assertEquals(1, payload.records().size());
    assertEquals(1700000000000L, payload.records().get(0).timestamp());
    assertEquals("{\"requestId\":\"a\"}", payload.records().get(0).message());
  }

  @Test
  void ignoresControlMessagesAndUnknownEvents() {
    String data =
        encode("{\"messageType\":\"CONTROL_MESSAGE\",\"logEvents\":[{\"message\":\"ping\"}]}");

    assertTrue(CloudWatchLogsPayload.decode(data).records().isEmpty());
    assertTrue(CloudWatchLogsPayload.fromEvent(Map.of()).records().isEmpty());
    assertTrue(CloudWatchLogsPayload.fromEvent(null).records().isEmpty());
  }

  static String encode(String json) {
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer)) {
      gzip.write(json.getBytes(StandardCharsets.UTF_8));
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
    return Base64.getEncoder().encodeToString(buffer.toByteArray());
  }
}
