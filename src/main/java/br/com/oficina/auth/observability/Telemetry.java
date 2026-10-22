package br.com.oficina.auth.observability;

public interface Telemetry {

  TraceContext currentTrace();

  void addAttribute(String name, String value);

  static Telemetry fromEnvironment() {
    return enabled(System.getenv("NEW_RELIC_MONITORING_ENABLED"))
        ? new NewRelicTelemetry()
        : new NoopTelemetry();
  }

  static boolean enabled(String flag) {
    return flag != null && flag.equalsIgnoreCase("true");
  }
}
