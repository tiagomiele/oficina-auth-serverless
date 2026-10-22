package br.com.oficina.auth.config;

public record AuthConfig(
    String dbUrl,
    String dbUser,
    String dbPassword,
    String jwtPrivateKey,
    String jwtPublicKey,
    String jwtIssuer,
    String jwtAudience,
    long jwtTtlSeconds) {

  public static AuthConfig loginFromEnvironment() {
    return new AuthConfig(
        required("DB_URL"),
        required("DB_USER"),
        required("DB_PASSWORD"),
        required("JWT_PRIVATE_KEY"),
        required("JWT_PUBLIC_KEY"),
        valueOrDefault("JWT_ISSUER", "oficina-auth-serverless"),
        valueOrDefault("JWT_AUDIENCE", "oficina-backend"),
        Long.parseLong(valueOrDefault("JWT_TTL_SECONDS", "900")));
  }

  public static AuthConfig authorizerFromEnvironment() {
    return new AuthConfig(
        null,
        null,
        null,
        null,
        required("JWT_PUBLIC_KEY"),
        valueOrDefault("JWT_ISSUER", "oficina-auth-serverless"),
        valueOrDefault("JWT_AUDIENCE", "oficina-backend"),
        Long.parseLong(valueOrDefault("JWT_TTL_SECONDS", "900")));
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
