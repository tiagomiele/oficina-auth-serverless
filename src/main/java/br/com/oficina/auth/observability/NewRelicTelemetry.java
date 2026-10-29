package br.com.oficina.auth.observability;

import com.newrelic.api.agent.NewRelic;
import com.newrelic.api.agent.TraceMetadata;

public final class NewRelicTelemetry implements Telemetry {

  @Override
  public TraceContext currentTrace() {
    TraceMetadata metadata = NewRelic.getAgent().getTraceMetadata();
    return new TraceContext(
        LogSanitizer.identifier(metadata.getTraceId()),
        LogSanitizer.identifier(metadata.getSpanId()));
  }

  @Override
  public void addAttribute(String name, String value) {
    String sanitized = LogSanitizer.identifier(value);
    if (sanitized != null) {
      NewRelic.addCustomParameter(name, sanitized);
    }
  }
}
