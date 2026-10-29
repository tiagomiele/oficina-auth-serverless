package br.com.oficina.auth.forwarder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApiGatewayLogForwarderTest {

  private final List<Map<String, Object>> sent = new ArrayList<>();
  private final ApiGatewayLogForwarder forwarder = new ApiGatewayLogForwarder(sent::addAll);

  @Test
  void forwardsOnlySanitizedAccessLogs() {
    String data =
        CloudWatchLogsPayloadTest.encode(
            """
            {"messageType":"DATA_MESSAGE","logGroup":"/aws/apigateway/oficina-auth-homolog",
             "logEvents":[
               {"id":"1","timestamp":1,"message":"{\\"requestId\\":\\"a\\",\\"routeKey\\":\\"POST /auth/cpf\\",\\"status\\":200,\\"authorization\\":\\"Bearer x\\"}"},
               {"id":"2","timestamp":2,"message":"START RequestId: 1"}
             ]}
            """);

    int forwarded = forwarder.forward(CloudWatchLogsPayload.decode(data));

    assertEquals(1, forwarded);
    assertEquals(1, sent.size());
    assertEquals("a", sent.get(0).get("requestId"));
    assertTrue(sent.get(0).containsKey("routeKey"));
    assertFalse(sent.get(0).containsKey("authorization"));
  }

  @Test
  void sendsNothingWhenThereIsNoAccessLog() {
    String data =
        CloudWatchLogsPayloadTest.encode(
            "{\"messageType\":\"DATA_MESSAGE\",\"logEvents\":[{\"message\":\"END RequestId: 1\"}]}");

    assertEquals(0, forwarder.forward(CloudWatchLogsPayload.decode(data)));
    assertTrue(sent.isEmpty());
  }
}
