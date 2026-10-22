resource "aws_sns_topic" "notifications" {
  name              = "${local.name}-notifications"
  kms_master_key_id = "alias/aws/sns"
}

resource "aws_sqs_queue" "notification_dlq" {
  name                      = "${local.name}-notification-dlq"
  sqs_managed_sse_enabled   = true
  message_retention_seconds = 1209600
}

resource "aws_sqs_queue_policy" "notification_dlq" {
  queue_url = aws_sqs_queue.notification_dlq.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid       = "AllowSnsRedrive"
      Effect    = "Allow"
      Principal = { Service = "sns.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.notification_dlq.arn
      Condition = {
        ArnEquals = { "aws:SourceArn" = aws_sns_topic.notifications.arn }
      }
    }]
  })
}

resource "aws_cloudwatch_log_group" "notification_ingress" {
  name              = "/aws/lambda/${local.name}-notification-ingress"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "notification_delivery" {
  name              = "/aws/lambda/${local.name}-notification-delivery"
  retention_in_days = 7
}

resource "aws_lambda_function" "notification_ingress" {
  function_name = "${local.name}-notification-ingress"
  description   = "Valida e publica notificações técnicas no SNS"
  role          = local.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = "br.com.oficina.auth.handler.NotificationIngressHandler::handleRequest"
  layers        = local.newrelic_layers

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 512
  timeout          = 18

  environment {
    variables = merge(
      {
        NOTIFICATION_TOPIC_ARN = aws_sns_topic.notifications.arn
        NOTIFICATION_API_KEY   = var.notification_api_key
        ENVIRONMENT            = var.environment
      },
      local.newrelic_environment
    )
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  lifecycle {
    precondition {
      condition     = !var.newrelic_instrumentation_enabled || local.newrelic_credentials_present
      error_message = "Informe newrelic_license_key e newrelic_account_id para instrumentar as Lambdas."
    }
  }

  depends_on = [aws_cloudwatch_log_group.notification_ingress]
}

resource "aws_lambda_function" "notification_delivery" {
  function_name = "${local.name}-notification-delivery"
  description   = "Entrega notificações assíncronas por Amazon SES"
  role          = local.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = "br.com.oficina.auth.handler.NotificationDeliveryHandler::handleRequest"
  layers        = local.newrelic_layers

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 384
  timeout          = 30

  environment {
    variables = merge(
      {
        NOTIFICATION_DELIVERY_MODE = var.notification_delivery_mode
        NOTIFICATION_SOURCE_EMAIL  = var.notification_source_email
        ENVIRONMENT                = var.environment
      },
      local.newrelic_environment
    )
  }

  dead_letter_config {
    target_arn = aws_sqs_queue.notification_dlq.arn
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  lifecycle {
    precondition {
      condition     = !var.newrelic_instrumentation_enabled || local.newrelic_credentials_present
      error_message = "Informe newrelic_license_key e newrelic_account_id para instrumentar as Lambdas."
    }
  }

  depends_on = [aws_cloudwatch_log_group.notification_delivery]
}

resource "aws_lambda_permission" "notification_delivery" {
  statement_id  = "AllowSnsNotificationDelivery"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.notification_delivery.function_name
  principal     = "sns.amazonaws.com"
  source_arn    = aws_sns_topic.notifications.arn
}

resource "aws_sns_topic_subscription" "notification_delivery" {
  topic_arn = aws_sns_topic.notifications.arn
  protocol  = "lambda"
  endpoint  = aws_lambda_function.notification_delivery.arn

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notification_dlq.arn
  })

  depends_on = [
    aws_lambda_permission.notification_delivery,
    aws_sqs_queue_policy.notification_dlq
  ]
}

resource "aws_ses_email_identity" "notification_source" {
  count = var.notification_delivery_mode == "ses" && var.notification_create_ses_identity ? 1 : 0

  email = var.notification_source_email
}
