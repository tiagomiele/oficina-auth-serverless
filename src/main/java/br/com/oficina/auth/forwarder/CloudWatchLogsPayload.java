package br.com.oficina.auth.forwarder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/** Decodifica o payload gzip/base64 entregue por uma subscription do CloudWatch Logs. */
public record CloudWatchLogsPayload(String logGroup, List<LogRecord> records) {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String DATA_MESSAGE = "DATA_MESSAGE";

  public record LogRecord(long timestamp, String message) {}

  public static CloudWatchLogsPayload fromEvent(Map<String, Object> event) {
    if (event == null || !(event.get("awslogs") instanceof Map<?, ?> awslogs)) {
      return empty();
    }
    if (!(awslogs.get("data") instanceof String data)) {
      return empty();
    }
    return decode(data);
  }

  public static CloudWatchLogsPayload decode(String base64GzipData) {
    JsonNode root = readJson(inflate(base64GzipData));
    if (!DATA_MESSAGE.equals(root.path("messageType").asText(DATA_MESSAGE))) {
      return empty();
    }
    List<LogRecord> records = new ArrayList<>();
    for (JsonNode logEvent : root.path("logEvents")) {
      String message = logEvent.path("message").asText(null);
      if (message != null && !message.isBlank()) {
        records.add(new LogRecord(logEvent.path("timestamp").asLong(), message));
      }
    }
    return new CloudWatchLogsPayload(root.path("logGroup").asText(""), List.copyOf(records));
  }

  private static CloudWatchLogsPayload empty() {
    return new CloudWatchLogsPayload("", List.of());
  }

  private static String inflate(String base64GzipData) {
    byte[] compressed = Base64.getDecoder().decode(base64GzipData);
    try (var stream = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static JsonNode readJson(String content) {
    try {
      return JSON.readTree(content);
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }
}
