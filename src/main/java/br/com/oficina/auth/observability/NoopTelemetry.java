package br.com.oficina.auth.observability;

public final class NoopTelemetry implements Telemetry {

  @Override
  public TraceContext currentTrace() {
    return TraceContext.empty();
  }

  @Override
  public void addAttribute(String name, String value) {}
}
