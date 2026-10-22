package br.com.oficina.auth.application.port.in;

import java.util.List;
import java.util.Map;

public interface ForwardAccessLogs {

  int forward(List<Map<String, Object>> sanitizedLogs);
}
