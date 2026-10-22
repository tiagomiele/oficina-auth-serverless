package br.com.oficina.auth.observability;

public record TraceContext(String traceId, String spanId) {

  private static final TraceContext EMPTY = new TraceContext(null, null);

  public static TraceContext empty() {
    return EMPTY;
  }
}
