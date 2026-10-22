package br.com.oficina.auth.application.port.out;

import java.util.List;
import java.util.Map;

public interface AccessLogPublisherPort {

  void publish(List<Map<String, Object>> sanitizedLogs);
}
