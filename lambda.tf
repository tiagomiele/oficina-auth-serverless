locals {
  name         = "${var.project_name}-${var.environment}"
  lab_role_arn = "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:role/LabRole"
}

resource "aws_cloudwatch_log_group" "login" {
  name              = "/aws/lambda/${local.name}-login"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "authorizer" {
  name              = "/aws/lambda/${local.name}-authorizer"
  retention_in_days = 7
}

resource "aws_lambda_function" "login" {
  function_name = "${local.name}-login"
  description   = "Autenticação de cliente por CPF"
  role          = local.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = local.login_handler
  layers        = local.newrelic_layers

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 512
  timeout          = 15

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  environment {
    variables = merge(
      {
        DB_URL          = var.db_url
        DB_USER         = var.db_user
        DB_PASSWORD     = var.db_password
        JWT_PRIVATE_KEY = var.jwt_private_key
        JWT_PUBLIC_KEY  = var.jwt_public_key
        JWT_ISSUER      = var.jwt_issuer
        JWT_AUDIENCE    = var.jwt_audience
        JWT_TTL_SECONDS = tostring(var.jwt_ttl_seconds)
        ENVIRONMENT     = var.environment
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

  depends_on = [aws_cloudwatch_log_group.login]
}

resource "aws_lambda_function" "authorizer" {
  function_name = "${local.name}-authorizer"
  description   = "Validação de JWT para rotas protegidas"
  role          = local.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = local.authorizer_handler
  layers        = local.newrelic_layers

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 384
  timeout          = 10

  environment {
    variables = merge(
      {
        JWT_PUBLIC_KEY = var.jwt_public_key
        JWT_ISSUER     = var.jwt_issuer
        JWT_AUDIENCE   = var.jwt_audience
        ENVIRONMENT    = var.environment
      },
      local.newrelic_environment
    )
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  depends_on = [aws_cloudwatch_log_group.authorizer]
}
