package br.com.oficina.auth.support;

import com.amazonaws.services.lambda.runtime.ClientContext;
import com.amazonaws.services.lambda.runtime.CognitoIdentity;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class RecordingContext implements Context {

  private final String requestId;
  private final List<String> lines = new ArrayList<>();

  public RecordingContext(String requestId) {
    this.requestId = requestId;
  }

  public List<String> logLines() {
    return List.copyOf(lines);
  }

  @Override
  public String getAwsRequestId() {
    return requestId;
  }

  @Override
  public LambdaLogger getLogger() {
    return new LambdaLogger() {
      @Override
      public void log(String message) {
        lines.add(message.strip());
      }

      @Override
      public void log(byte[] message) {
        log(new String(message, StandardCharsets.UTF_8));
      }
    };
  }

  @Override
  public String getLogGroupName() {
    return "/aws/lambda/test";
  }

  @Override
  public String getLogStreamName() {
    return "test-stream";
  }

  @Override
  public String getFunctionName() {
    return "test-function";
  }

  @Override
  public String getFunctionVersion() {
    return "$LATEST";
  }

  @Override
  public String getInvokedFunctionArn() {
    return "arn:aws:lambda:us-west-2:000000000000:function:test-function";
  }

  @Override
  public CognitoIdentity getIdentity() {
    return null;
  }

  @Override
  public ClientContext getClientContext() {
    return null;
  }

  @Override
  public int getRemainingTimeInMillis() {
    return 15_000;
  }

  @Override
  public int getMemoryLimitInMB() {
    return 512;
  }
}
