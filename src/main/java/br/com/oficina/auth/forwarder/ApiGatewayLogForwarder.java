package br.com.oficina.auth.forwarder;

import br.com.oficina.auth.application.port.in.ForwardAccessLogs;
import br.com.oficina.auth.application.port.out.AccessLogPublisherPort;
import br.com.oficina.auth.application.usecase.ForwardAccessLogsUseCase;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ApiGatewayLogForwarder {

  private final ForwardAccessLogs forwarder;

  public ApiGatewayLogForwarder(AccessLogPublisherPort publisher) {
    this.forwarder = new ForwardAccessLogsUseCase(publisher);
  }

  public int forward(CloudWatchLogsPayload payload) {
    List<Map<String, Object>> sanitized =
        payload.records().stream()
            .map(record -> AccessLogSanitizer.sanitize(record.message()))
            .flatMap(Optional::stream)
            .toList();
    return forwarder.forward(sanitized);
  }
}
