package br.com.oficina.auth.adapter.out.http;

import br.com.oficina.auth.application.port.out.ClientRepositoryPort;
import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.Cpf;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Consulta o cliente no OS Service pela rota interna {@code POST /interno/clientes/autenticacao}. O
 * Auth não acessa o banco do OS Service: cada serviço só lê o próprio banco.
 */
public final class OsServiceClientRepository implements ClientRepositoryPort {

  static final String PATH = "/interno/clientes/autenticacao";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

  private final AuthConfig config;
  private final HttpClient http;

  public OsServiceClientRepository(AuthConfig config) {
    this(config, HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build());
  }

  public OsServiceClientRepository(AuthConfig config, HttpClient http) {
    this.config = config;
    this.http = http;
  }

  @Override
  public Optional<Long> findActiveClientId(Cpf cpf) {
    String baseUrl = config.osServiceBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException("OS_SERVICE_BASE_URL não configurada");
    }
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(baseUrl + PATH))
            .timeout(REQUEST_TIMEOUT)
            .header("Content-Type", "application/json")
            .header("X-Api-Key", config.internalApiKey())
            .POST(HttpRequest.BodyPublishers.ofString(body(cpf)))
            .build();
    HttpResponse<String> response = execute(request);
    return switch (response.statusCode()) {
      case 200 -> Optional.of(clientId(response.body()));
      case 404 -> Optional.empty();
      default ->
          throw new IllegalStateException(
              "OS Service respondeu status " + response.statusCode() + " na consulta do cliente");
    };
  }

  private static String body(Cpf cpf) {
    try {
      return JSON.writeValueAsString(Map.of("cpf", cpf.value()));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Falha ao montar a consulta do cliente", exception);
    }
  }

  private static long clientId(String body) {
    try {
      JsonNode id = JSON.readTree(body).get("idCliente");
      if (id == null || !id.canConvertToLong()) {
        throw new IllegalStateException("Resposta do OS Service sem idCliente");
      }
      return id.asLong();
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Resposta do OS Service inválida", exception);
    }
  }

  private HttpResponse<String> execute(HttpRequest request) {
    try {
      return http.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Consulta do cliente interrompida", exception);
    }
  }
}
