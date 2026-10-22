locals {
  newrelic_layer_arn = coalesce(
    var.newrelic_layer_arn,
    "arn:aws:lambda:${var.aws_region}:${var.newrelic_layer_account_id}:layer:${var.newrelic_layer_name}:${var.newrelic_layer_version}"
  )

  newrelic_layers = var.newrelic_instrumentation_enabled ? [local.newrelic_layer_arn] : []

  newrelic_trusted_account_key = (
    var.newrelic_trusted_account_key != "" ? var.newrelic_trusted_account_key : var.newrelic_account_id
  )

  newrelic_credentials_present = var.newrelic_license_key != "" && var.newrelic_account_id != ""

  # Variáveis exigidas pela camada do agente Java. Somente valores técnicos e parametrizados.
  newrelic_environment = var.newrelic_instrumentation_enabled ? {
    AWS_LAMBDA_EXEC_WRAPPER                = "/opt/newrelic-java-handler"
    NEW_RELIC_LICENSE_KEY                  = var.newrelic_license_key
    NEW_RELIC_TRUSTED_ACCOUNT_KEY          = local.newrelic_trusted_account_key
    NEW_RELIC_ACCOUNT_ID                   = var.newrelic_account_id
    NEW_RELIC_APM_LAMBDA_MODE              = "true"
    NEW_RELIC_MONITORING_ENABLED           = "true"
    NEW_RELIC_DISTRIBUTED_TRACING_ENABLED  = tostring(var.newrelic_distributed_tracing_enabled)
    NEW_RELIC_LOG_LEVEL                    = var.newrelic_log_level
    NEW_RELIC_EXTENSION_SEND_FUNCTION_LOGS = tostring(var.newrelic_send_function_logs)
  } : {}

  login_handler      = "br.com.oficina.auth.handler.CpfAuthenticationHandler::handleRequest"
  authorizer_handler = "br.com.oficina.auth.handler.JwtAuthorizerHandler::handleRequest"
}

resource "aws_cloudwatch_log_group" "log_forwarder" {
  count = var.newrelic_log_forwarding_enabled ? 1 : 0

  name              = "/aws/lambda/${local.name}-log-forwarder"
  retention_in_days = 7
}

resource "aws_lambda_function" "log_forwarder" {
  count = var.newrelic_log_forwarding_enabled ? 1 : 0

  function_name = "${local.name}-log-forwarder"
  description   = "Encaminha o log de acesso sanitizado do API Gateway para o New Relic"
  role          = local.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = "br.com.oficina.auth.handler.ApiGatewayLogForwarderHandler::handleRequest"

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 384
  timeout          = 30

  environment {
    variables = {
      NEW_RELIC_LICENSE_KEY   = var.newrelic_license_key
      NEW_RELIC_LOGS_ENDPOINT = var.newrelic_logs_endpoint
      NEW_RELIC_LOG_TYPE      = var.newrelic_access_log_type
      ENVIRONMENT             = var.environment
    }
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  lifecycle {
    precondition {
      condition     = var.newrelic_license_key != ""
      error_message = "newrelic_license_key é obrigatória para encaminhar logs do API Gateway."
    }
  }

  depends_on = [aws_cloudwatch_log_group.log_forwarder]
}

resource "aws_lambda_permission" "log_forwarder" {
  count = var.newrelic_log_forwarding_enabled ? 1 : 0

  statement_id  = "AllowCloudWatchLogsInvoke"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.log_forwarder[0].function_name
  principal     = "logs.${var.aws_region}.amazonaws.com"
  source_arn    = "${aws_cloudwatch_log_group.api.arn}:*"
}

resource "aws_cloudwatch_log_subscription_filter" "api_access_logs" {
  count = var.newrelic_log_forwarding_enabled ? 1 : 0

  name            = "${local.name}-access-logs-to-newrelic"
  log_group_name  = aws_cloudwatch_log_group.api.name
  filter_pattern  = ""
  destination_arn = aws_lambda_function.log_forwarder[0].arn

  depends_on = [aws_lambda_permission.log_forwarder]
}
