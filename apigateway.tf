locals {
  backend_route_keys = var.backend_base_url == null ? [] : [
    "GET /consulta/ordens-servico/{numeroOs}/status",
    "GET /ordens-servico/{numeroOs}/historico",
    "POST /ordens-servico/{numeroOs}/aprovar",
    "POST /ordens-servico/{numeroOs}/rejeitar-refazer",
    "POST /ordens-servico/{numeroOs}/rejeitar-cancelar",
    "POST /ordens-servico/{numeroOs}/confirmar-pagamento"
  ]

  backend_routes = {
    for route_key in local.backend_route_keys : route_key => {
      method = split(" ", route_key)[0]
      path   = split(" ", route_key)[1]
    }
  }
}

resource "aws_apigatewayv2_api" "main" {
  name          = "${local.name}-http-api"
  protocol_type = "HTTP"

  cors_configuration {
    allow_headers = ["Authorization", "Content-Type", "X-Request-Id"]
    allow_methods = ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"]
    allow_origins = ["*"]
    max_age       = 300
  }
}

resource "aws_apigatewayv2_integration" "login" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.login.invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 15000
}

resource "aws_apigatewayv2_route" "login" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /auth/cpf"
  target    = "integrations/${aws_apigatewayv2_integration.login.id}"
}

resource "aws_apigatewayv2_integration" "notification_ingress" {
  api_id                 = aws_apigatewayv2_api.main.id
  integration_type       = "AWS_PROXY"
  integration_uri        = aws_lambda_function.notification_ingress.invoke_arn
  payload_format_version = "2.0"
  timeout_milliseconds   = 22000
}

resource "aws_apigatewayv2_route" "notification_ingress" {
  api_id    = aws_apigatewayv2_api.main.id
  route_key = "POST /internal/notifications"
  target    = "integrations/${aws_apigatewayv2_integration.notification_ingress.id}"
}

resource "aws_apigatewayv2_authorizer" "jwt" {
  api_id                            = aws_apigatewayv2_api.main.id
  name                              = "${local.name}-jwt-authorizer"
  authorizer_type                   = "REQUEST"
  authorizer_uri                    = aws_lambda_function.authorizer.invoke_arn
  identity_sources                  = ["$request.header.Authorization"]
  authorizer_payload_format_version = "2.0"
  enable_simple_responses           = true
  authorizer_result_ttl_in_seconds  = 300
}

# O HTTP API não repassa o caminho da rota para integrações HTTP_PROXY: a URI da
# integração precisa conter o caminho do backend, por isso há uma integração por rota.
resource "aws_apigatewayv2_integration" "backend" {
  for_each = local.backend_routes

  api_id             = aws_apigatewayv2_api.main.id
  integration_type   = "HTTP_PROXY"
  integration_method = each.value.method
  integration_uri    = "${var.backend_base_url}${each.value.path}"

  # Garante o identificador de correlação no backend mesmo sem X-Request-Id do cliente.
  request_parameters = {
    "append:header.X-Request-Id" = "$context.requestId"
  }
}

resource "aws_apigatewayv2_route" "backend" {
  for_each = local.backend_routes

  api_id             = aws_apigatewayv2_api.main.id
  route_key          = each.key
  authorization_type = "CUSTOM"
  authorizer_id      = aws_apigatewayv2_authorizer.jwt.id
  target             = "integrations/${aws_apigatewayv2_integration.backend[each.key].id}"
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/apigateway/${local.name}"
  retention_in_days = 7
}

resource "aws_apigatewayv2_stage" "default" {
  api_id      = aws_apigatewayv2_api.main.id
  name        = "$default"
  auto_deploy = true

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api.arn
    format = jsonencode({
      requestId               = "$context.requestId"
      environment             = var.environment
      apiId                   = "$context.apiId"
      stage                   = "$context.stage"
      routeKey                = "$context.routeKey"
      httpMethod              = "$context.httpMethod"
      path                    = "$context.path"
      protocol                = "$context.protocol"
      status                  = "$context.status"
      responseLength          = "$context.responseLength"
      responseLatency         = "$context.responseLatency"
      integrationStatus       = "$context.integration.status"
      integrationLatency      = "$context.integration.latency"
      integrationErrorMessage = "$context.integration.error"
      errorMessage            = "$context.error.message"
      authorizerError         = "$context.authorizer.error"
    })
  }

  default_route_settings {
    throttling_burst_limit   = 20
    throttling_rate_limit    = 10
    detailed_metrics_enabled = var.api_detailed_metrics_enabled
  }
}

resource "aws_lambda_permission" "login" {
  statement_id  = "AllowApiGatewayLogin"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.login.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*/auth/cpf"
}

resource "aws_lambda_permission" "authorizer" {
  statement_id  = "AllowApiGatewayAuthorizer"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.authorizer.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/authorizers/${aws_apigatewayv2_authorizer.jwt.id}"
}

resource "aws_lambda_permission" "notification_ingress" {
  statement_id  = "AllowApiGatewayNotificationIngress"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notification_ingress.function_name
  principal     = "apigateway.amazonaws.com"
  source_arn    = "${aws_apigatewayv2_api.main.execution_arn}/*/*/internal/notifications"
}
