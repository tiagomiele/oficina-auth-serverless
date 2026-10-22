variable "aws_region" {
  description = "Região AWS Academy."
  type        = string
  default     = "us-west-2"
}

variable "project_name" {
  description = "Nome base dos recursos."
  type        = string
  default     = "oficina-auth"
}

variable "environment" {
  description = "Ambiente lógico."
  type        = string
  default     = "homolog"

  validation {
    condition     = contains(["homolog", "production"], var.environment)
    error_message = "environment deve ser homolog ou production."
  }
}

variable "lambda_security_group_id" {
  description = "Security group do EKS já autorizado no RDS e reutilizado pela Lambda."
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnets privadas usadas pelas Lambdas."
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "Informe ao menos duas subnets privadas."
  }
}

variable "db_url" {
  description = "URL JDBC PostgreSQL com sslmode=require."
  type        = string
  sensitive   = true
}

variable "db_user" {
  description = "Usuário PostgreSQL de leitura para autenticação."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Senha PostgreSQL do usuário de autenticação."
  type        = string
  sensitive   = true
}

variable "jwt_private_key" {
  description = "Chave privada RSA PKCS#8 em PEM usada apenas pela Lambda de login."
  type        = string
  sensitive   = true
}

variable "jwt_public_key" {
  description = "Chave pública RSA em PEM usada pelo authorizer e pelo backend."
  type        = string
}

variable "jwt_issuer" {
  description = "Emissor obrigatório do JWT."
  type        = string
  default     = "oficina-auth-serverless"
}

variable "jwt_audience" {
  description = "Audiência obrigatória do JWT."
  type        = string
  default     = "oficina-backend"
}

variable "jwt_ttl_seconds" {
  description = "Validade do token do cliente."
  type        = number
  default     = 900

  validation {
    condition     = var.jwt_ttl_seconds >= 300 && var.jwt_ttl_seconds <= 3600
    error_message = "jwt_ttl_seconds deve estar entre 300 e 3600 segundos."
  }
}

variable "lambda_package_path" {
  description = "JAR empacotado pelo Maven antes do plan."
  type        = string
  default     = "target/oficina-auth.jar"
}

variable "newrelic_instrumentation_enabled" {
  description = "Ativa a camada do agente Java do New Relic nas Lambdas de autenticação."
  type        = bool
  default     = false
}

variable "newrelic_layer_arn" {
  description = "ARN completo da camada do agente Java. Nulo monta o ARN pelos campos abaixo."
  type        = string
  default     = null
  nullable    = true
}

variable "newrelic_layer_account_id" {
  description = "Conta AWS que publica as camadas do New Relic."
  type        = string
  default     = "451483290750"
}

variable "newrelic_layer_name" {
  description = "Nome da camada do agente Java. O padrão é a variante slim para ARM64."
  type        = string
  default     = "NewRelicAgentJavaARM64-slim"
}

variable "newrelic_layer_version" {
  description = "Versão da camada do agente Java publicada na região."
  type        = number
  default     = 1
}

variable "newrelic_license_key" {
  description = "Ingest license key do New Relic. Obrigatória quando a instrumentação está ativa."
  type        = string
  default     = ""
  sensitive   = true
}

variable "newrelic_account_id" {
  description = "Identificador da conta New Relic."
  type        = string
  default     = ""
}

variable "newrelic_trusted_account_key" {
  description = "Conta pai do New Relic usada em distributed tracing. Vazio reutiliza newrelic_account_id."
  type        = string
  default     = ""
}

variable "newrelic_distributed_tracing_enabled" {
  description = "Habilita distributed tracing no agente."
  type        = bool
  default     = true
}

variable "newrelic_log_level" {
  description = "Nível de log do agente Java."
  type        = string
  default     = "warning"

  validation {
    condition     = contains(["off", "severe", "warning", "info", "fine", "finer", "finest"], var.newrelic_log_level)
    error_message = "newrelic_log_level deve ser um nível suportado pelo agente Java."
  }
}

variable "newrelic_send_function_logs" {
  description = "Permite que a extensão do New Relic envie os logs da função além do CloudWatch."
  type        = bool
  default     = false
}

variable "newrelic_log_forwarding_enabled" {
  description = "Cria a Lambda que encaminha o log de acesso sanitizado do API Gateway para o New Relic."
  type        = bool
  default     = false
}

variable "newrelic_logs_endpoint" {
  description = "Endpoint da Log API do New Relic. Use o domínio .eu para contas europeias."
  type        = string
  default     = "https://log-api.newrelic.com/log/v1"

  validation {
    condition     = can(regex("^https://[^/]+/log/v1$", var.newrelic_logs_endpoint))
    error_message = "newrelic_logs_endpoint deve ser uma URL HTTPS da Log API do New Relic."
  }
}

variable "newrelic_access_log_type" {
  description = "Valor de logtype aplicado aos registros de acesso enviados ao New Relic."
  type        = string
  default     = "api-gateway-access"
}

variable "api_detailed_metrics_enabled" {
  description = "Habilita métricas detalhadas por rota no stage do HTTP API."
  type        = bool
  default     = true
}

variable "backend_base_url" {
  description = "URL pública do LoadBalancer do backend, sem barra final. Nulo cria somente /auth/cpf."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.backend_base_url == null || can(regex("^https?://[^/]+$", var.backend_base_url))
    error_message = "backend_base_url deve ser uma URL HTTP(S) sem barra final."
  }
}

variable "notification_api_key" {
  description = "Chave compartilhada pelo backend e pela Lambda de ingresso de notificações."
  type        = string
  sensitive   = true

  validation {
    condition     = can(regex("^[!-~]{32,128}$", var.notification_api_key))
    error_message = "notification_api_key deve possuir entre 32 e 128 caracteres ASCII sem espaço."
  }
}

variable "notification_delivery_mode" {
  description = "Modo de entrega: log para ambientes sem permissão SES ou ses para envio real."
  type        = string
  default     = "log"

  validation {
    condition     = contains(["log", "ses"], var.notification_delivery_mode)
    error_message = "notification_delivery_mode deve ser log ou ses."
  }
}

variable "notification_source_email" {
  description = "Endereço verificado no Amazon SES usado como remetente quando o modo ses está habilitado."
  type        = string

  validation {
    condition     = can(regex("^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$", var.notification_source_email))
    error_message = "notification_source_email deve ser um endereço de e-mail válido."
  }
}

variable "notification_create_ses_identity" {
  description = "Solicita a verificação do remetente somente quando notification_delivery_mode é ses."
  type        = bool
  default     = false
}
