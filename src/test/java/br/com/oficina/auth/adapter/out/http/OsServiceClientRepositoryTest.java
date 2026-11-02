package br.com.oficina.auth.adapter.out.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.oficina.auth.config.AuthConfig;
import br.com.oficina.auth.domain.Cpf;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OsServiceClientRepositoryTest {

  private static final String API_KEY = "chave-tecnica-com-mais-de-32-caracteres";
  private static final Cpf CPF = Cpf.parse("529.982.247-25");

  private final AtomicReference<String> receivedKey = new AtomicReference<>();
  private final AtomicReference<String> receivedBody = new AtomicReference<>();
  private HttpServer server;

  @AfterEach
  void stop() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void activeClientReturnsIdAndSendsKeyAndCpf() throws IOException {
    String baseUrl = serve(200, "{\"idCliente\":42}");

    Optional<Long> id = repository(baseUrl).findActiveClientId(CPF);

    assertEquals(Optional.of(42L), id);
    assertEquals(API_KEY, receivedKey.get());
    assertEquals("{\"cpf\":\"52998224725\"}", receivedBody.get());
  }

  @Test
  void notEligibleClientReturnsEmpty() throws IOException {
    assertTrue(repository(serve(404, "")).findActiveClientId(CPF).isEmpty());
  }

  @Test
  void unexpectedStatusFails() throws IOException {
    var repository = repository(serve(401, ""));

    assertThrows(IllegalStateException.class, () -> repository.findActiveClientId(CPF));
  }

  @Test
  void responseWithoutIdFails() throws IOException {
    var repository = repository(serve(200, "{}"));

    assertThrows(IllegalStateException.class, () -> repository.findActiveClientId(CPF));
  }

  @Test
  void invalidJsonFails() throws IOException {
    var repository = repository(serve(200, "nao-json"));

    assertThrows(IllegalStateException.class, () -> repository.findActiveClientId(CPF));
  }

  @Test
  void missingBaseUrlFails() {
    var repository = repository("");

    assertThrows(IllegalStateException.class, () -> repository.findActiveClientId(CPF));
  }

  @Test
  void unreachableServiceFails() {
    var repository = repository("http://127.0.0.1:9");

    assertThrows(RuntimeException.class, () -> repository.findActiveClientId(CPF));
  }

  private OsServiceClientRepository repository(String baseUrl) {
    return new OsServiceClientRepository(
        new AuthConfig(baseUrl, API_KEY, null, null, "issuer", "audience", 900));
  }

  private String serve(int status, String body) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        OsServiceClientRepository.PATH,
        exchange -> {
          receivedKey.set(exchange.getRequestHeaders().getFirst("X-Api-Key"));
          receivedBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
          if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
          }
          exchange.close();
        });
    server.start();
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }
}
