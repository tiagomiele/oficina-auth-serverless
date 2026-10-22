package br.com.oficina.auth.application.usecase;

import br.com.oficina.auth.application.port.in.ForwardAccessLogs;
import br.com.oficina.auth.application.port.out.AccessLogPublisherPort;
import java.util.List;
import java.util.Map;

public final class ForwardAccessLogsUseCase implements ForwardAccessLogs {

  private final AccessLogPublisherPort publisher;

  public ForwardAccessLogsUseCase(AccessLogPublisherPort publisher) {
    this.publisher = publisher;
  }

  @Override
  public int forward(List<Map<String, Object>> sanitizedLogs) {
    List<Map<String, Object>> logs = sanitizedLogs == null ? List.of() : List.copyOf(sanitizedLogs);
    publisher.publish(logs);
    return logs.size();
  }
}
