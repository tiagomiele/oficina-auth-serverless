package br.com.oficina.auth.forwarder;

import br.com.oficina.auth.application.port.out.AccessLogPublisherPort;
import java.util.List;
import java.util.Map;

public interface NewRelicLogsClient extends AccessLogPublisherPort {

  void send(List<Map<String, Object>> sanitizedLogs);

  @Override
  default void publish(List<Map<String, Object>> sanitizedLogs) {
    send(sanitizedLogs);
  }
}
