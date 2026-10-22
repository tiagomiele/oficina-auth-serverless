package br.com.oficina.auth.forwarder;

public record ForwarderConfig(
    String endpoint, String licenseKey, String environment, String logType) {

  private static final String DEFAULT_ENDPOINT = "https://log-api.newrelic.com/log/v1";
  private static final String DEFAULT_LOG_TYPE = "api-gateway-access";

  public static ForwarderConfig fromEnvironment() {
    return new ForwarderConfig(
        valueOrDefault("NEW_RELIC_LOGS_ENDPOINT", DEFAULT_ENDPOINT),
        required("NEW_RELIC_LICENSE_KEY"),
        required("ENVIRONMENT"),
        valueOrDefault("NEW_RELIC_LOG_TYPE", DEFAULT_LOG_TYPE));
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Variável obrigatória ausente: " + name);
    }
    return value;
  }

  private static String valueOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
