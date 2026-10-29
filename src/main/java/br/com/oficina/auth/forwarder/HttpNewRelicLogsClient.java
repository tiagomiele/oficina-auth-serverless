package br.com.oficina.auth.forwarder;

import br.com.oficina.auth.adapter.out.newrelic.NewRelicAccessLogAdapter;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;

public final class HttpNewRelicLogsClient implements NewRelicLogsClient {

  private final NewRelicAccessLogAdapter delegate;

  public HttpNewRelicLogsClient(ForwarderConfig config) {
    this.delegate = new NewRelicAccessLogAdapter(config);
  }

  HttpNewRelicLogsClient(ForwarderConfig config, HttpClient http) {
    this.delegate = new NewRelicAccessLogAdapter(config, http);
  }

  @Override
  public void send(List<Map<String, Object>> sanitizedLogs) {
    delegate.publish(sanitizedLogs);
  }
}
