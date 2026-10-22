output "api_base_url" {
  description = "URL base do API Gateway."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "cpf_authentication_url" {
  description = "Endpoint POST de autenticação por CPF."
  value       = "${aws_apigatewayv2_api.main.api_endpoint}/auth/cpf"
}

output "login_lambda_name" {
  value = aws_lambda_function.login.function_name
}

output "authorizer_lambda_name" {
  value = aws_lambda_function.authorizer.function_name
}

output "api_access_log_group" {
  description = "Log group com o log de acesso técnico do HTTP API."
  value       = aws_cloudwatch_log_group.api.name
}

output "newrelic_instrumentation" {
  description = "Camada aplicada às Lambdas quando a instrumentação está ativa."
  value = {
    enabled        = var.newrelic_instrumentation_enabled
    layer_arn      = var.newrelic_instrumentation_enabled ? local.newrelic_layer_arn : null
    log_forwarding = var.newrelic_log_forwarding_enabled
    forwarder_name = var.newrelic_log_forwarding_enabled ? aws_lambda_function.log_forwarder[0].function_name : null
  }
}

output "notification_endpoint" {
  description = "Endpoint técnico consumido pelo backend para publicar notificações."
  value       = "${aws_apigatewayv2_api.main.api_endpoint}/internal/notifications"
}

output "notification_topic_arn" {
  description = "Tópico SNS usado para entrega assíncrona."
  value       = aws_sns_topic.notifications.arn
}

output "notification_dlq_url" {
  description = "Fila que recebe notificações não entregues após as tentativas do SNS."
  value       = aws_sqs_queue.notification_dlq.url
}
