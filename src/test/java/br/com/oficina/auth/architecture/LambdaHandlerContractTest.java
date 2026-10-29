package br.com.oficina.auth.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.oficina.auth.handler.ApiGatewayLogForwarderHandler;
import br.com.oficina.auth.handler.CpfAuthenticationHandler;
import br.com.oficina.auth.handler.JwtAuthorizerHandler;
import br.com.oficina.auth.handler.NotificationDeliveryHandler;
import br.com.oficina.auth.handler.NotificationIngressHandler;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class LambdaHandlerContractTest {

  private static final List<Class<?>> HANDLERS =
      List.of(
          CpfAuthenticationHandler.class,
          JwtAuthorizerHandler.class,
          NotificationIngressHandler.class,
          NotificationDeliveryHandler.class,
          ApiGatewayLogForwarderHandler.class);

  @Test
  void preservesPublicNoArgumentConstructors() throws Exception {
    for (Class<?> handler : HANDLERS) {
      assertTrue(Modifier.isPublic(handler.getDeclaredConstructor().getModifiers()));
    }
  }

  @Test
  void preservesTerraformEntrypoints() throws Exception {
    String terraform =
        Files.readString(Path.of("lambda.tf"))
            + Files.readString(Path.of("notification.tf"))
            + Files.readString(Path.of("newrelic.tf"));

    for (Class<?> handler : HANDLERS) {
      assertTrue(terraform.contains(handler.getName() + "::handleRequest"));
    }
  }
}
