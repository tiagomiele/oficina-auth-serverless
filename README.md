> **Tech Challenge Fase 4 — `oficina-auth-serverless`**
>
> Login por CPF, Lambda Authorizer JWT, notificações e API Gateway.
>
> Base importada de [`fiap-tech-challenge-fase3-oficina-auth-serverless`](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-auth-serverless) (commit `92c367c`), sem alterações funcionais. A evolução para a Fase 4 é feita por PRs nas próximas fases; o conteúdo abaixo ainda descreve a Fase 3.

# Oficina Fase 3 — Auth Serverless

Serviço serverless responsável pela autenticação de clientes por CPF, emissão e validação de JWT e entrega assíncrona de notificações da Oficina.

## Visão de negócio

O Auth permite que um cliente ativo acesse com segurança as funcionalidades relacionadas às suas Ordens de Serviço sem utilizar uma senha criada pela oficina. O CPF identifica o cadastro; um JWT de curta duração representa a sessão autorizada e protege consultas e decisões do cliente.

O mesmo projeto desacopla as notificações da execução da OS. Assim, o atendimento principal não depende do tempo de entrega de e-mail e falhas podem ser repetidas ou encaminhadas para uma fila de erro.

## Responsabilidades

- validar e normalizar o CPF;
- consultar existência e situação ativa do cliente no PostgreSQL;
- emitir e validar JWT RSA de curta duração;
- proteger rotas do cliente com Lambda Authorizer;
- controlar e rotear requisições pelo API Gateway;
- receber notificações técnicas, publicá-las no SNS e tratar falhas pela DLQ;
- entregar por log técnico ou SES opcional, sem expor CPF, token ou mensagem nos logs;
- publicar logs estruturados, correlação e telemetria New Relic;
- provisionar os componentes serverless com Terraform.

Regras da Ordem de Serviço permanecem no Backend; EKS e RDS pertencem aos respectivos projetos de infraestrutura.

## Arquitetura do componente

![Arquitetura resumida do Auth Serverless](docs/assets/arquitetura-auth-resumida.png)

### Fluxo de autenticação

```text
Cliente
  → POST /auth/cpf
  → API Gateway
  → Lambda de autenticação
  → consulta ao PostgreSQL
  → emissão do JWT RSA
  → cliente utiliza Bearer Token
  → Lambda Authorizer valida o token
  → API protegida do Backend
```

### Fluxo de notificação

```text
Backend
  → endpoint interno do API Gateway
  → Lambda publicadora
  → SNS
  → Lambda de entrega
  → SES ou log técnico
  → SQS/DLQ em caso de falha definitiva
```

## Modelo arquitetural

O código utiliza uma Clean Architecture adaptada ao contexto serverless:

- `domain`: regras independentes da AWS, como CPF;
- `application`: casos de uso e portas de entrada e saída;
- `adapter/out`: integrações JDBC, JWT, SNS, SES e New Relic;
- `handler`: adaptadores de entrada das funções Lambda;
- `config`: composição das dependências;
- `notification`, `forwarder` e `observability`: processamento assíncrono e telemetria.

Cada função possui responsabilidade delimitada, e serviços externos ficam atrás de interfaces da aplicação.

## Stack e ferramentas

| Área | Tecnologias |
|---|---|
| Aplicação | Java 21 e Maven Wrapper |
| APIs e segurança | API Gateway v2, AWS Lambda, Lambda Authorizer e JWT RSA |
| Integração | PostgreSQL, Amazon SNS, SQS/DLQ e SES opcional |
| Infraestrutura | Terraform, HCP Terraform e AWS Academy `LabRole` |
| Qualidade | JUnit, Spotless, TFLint, Trivy, Gitleaks e actionlint |
| Entrega | GitHub Actions, GitHub Environments e sincronização de outputs |
| Observabilidade | Logs JSON, mascaramento de PII, correlação e New Relic serverless |

## Estrutura de pastas

```text
.
├── .github/workflows/
│   ├── ci.yml                     # build, testes e segurança
│   ├── terraform-plan.yml         # plan de homologação e produção
│   └── terraform-deploy.yml       # apply por ambiente
├── docs/
│   ├── arquitetura.md
│   ├── implantacao-operacao.md
│   ├── openapi/oficina-auth.yaml
│   └── assets/                    # diagramas do componente
├── environments/                  # exemplos de variáveis por ambiente
├── scripts/                       # validação AWS e sincronização de outputs
├── src/main/java/br/com/oficina/auth/
│   ├── domain/                    # regras independentes da infraestrutura
│   ├── application/               # casos de uso e portas
│   ├── adapter/out/               # JDBC, JWT, SNS, SES e New Relic
│   ├── handler/                   # entradas das Lambdas
│   ├── config/                    # composição das dependências
│   ├── notification/              # processamento de notificações
│   ├── forwarder/                 # encaminhamento de logs
│   └── observability/             # telemetria
├── src/test/                       # testes Java
├── tests/                          # testes dos scripts de integração
├── apigateway.tf                   # HTTP API, rotas e Authorizer
├── lambda.tf                       # funções de autenticação
├── notification.tf                # SNS, SQS/DLQ e entrega
├── newrelic.tf                     # instrumentação serverless
├── providers.tf
├── variables.tf
├── outputs.tf
└── pom.xml
```

## Pré-requisitos

- Java 21;
- Git;
- Terraform compatível com `versions.tf`;
- credenciais AWS válidas somente para plans e deploys remotos;
- organização e workspaces HCP Terraform configurados para homologação e produção.

O Maven Wrapper está incluído; não é necessário instalar Maven globalmente.

## Validar localmente

Linux/macOS:

```bash
./mvnw -B clean verify spotless:check
terraform fmt -check -recursive
terraform init -backend=false -input=false -lockfile=readonly
terraform validate
python3 -m unittest discover -s tests -p 'test_*.py'
```

Windows PowerShell:

```powershell
.\mvnw.cmd -B clean verify spotless:check
terraform fmt -check -recursive
terraform init -backend=false -input=false -lockfile=readonly
terraform validate
python -m unittest discover -s tests -p "test_*.py"
```

A validação local não cria recursos na AWS.

## Contrato da API

- autenticação: `POST /auth/cpf`;
- notificação técnica: `POST /internal/notifications`;
- a URL base é gerada pelo Terraform no output `api_base_url`.

A URL do API Gateway muda quando o ambiente AWS Academy é reconstruído. Não grave URLs temporárias como configuração permanente.

## Configuração e segurança

Os arquivos de exemplo estão em:

- [Homologação](environments/homolog.tfvars.example)
- [Produção](environments/production.tfvars.example)

Chaves RSA, credenciais do banco, API keys e chaves New Relic devem ser armazenadas como variáveis sensíveis no HCP Terraform ou como Secrets dos GitHub Environments. Nenhum segredo real deve ser versionado.

Os logs não devem registrar CPF completo, JWT, corpo de notificação ou credenciais.

## CI/CD e implantação

| Etapa | Comportamento |
|---|---|
| Pull Request | executa CI e Terraform Plan, sem criar recursos |
| Merge em `homolog` | empacota as Lambdas e implanta homologação |
| Promoção para `main` | implanta produção pelo ambiente protegido |
| Pós-deploy | sincroniza URLs e configurações necessárias com o Backend |

Fluxo de promoção:

```text
feature → Pull Request → homolog → Pull Request → main
```

A implantação cria API Gateway, Lambdas, Authorizer, logs, SNS, SQS/DLQ, integração SES opcional e instrumentação New Relic.

## Outputs principais

| Output | Finalidade |
|---|---|
| `api_base_url` | URL base do API Gateway |
| `cpf_authentication_url` | endpoint de autenticação por CPF |
| `notification_endpoint` | endpoint interno consumido pelo Backend |
| `login_lambda_name` | nome da Lambda de autenticação |
| `authorizer_lambda_name` | nome da Lambda Authorizer |
| `notification_topic_arn` | ARN do tópico SNS |
| `notification_dlq_url` | URL da fila de falhas |

## Documentação e evidências

- [Documentação central da Fase 3](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/tree/feature/validacao-deploy-aplicacao)
- [Requisitos obrigatórios e evidências](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/feature/validacao-deploy-aplicacao/README-requisitos-obrigatorios-fase-3.md)
- [Collection Postman integrada](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend/blob/feature/validacao-deploy-aplicacao/tests/postman/oficina-weeks4-5.postman_collection.json)

## Projetos relacionados

- [Backend](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-backend)
- [Database Infra](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-database-infra)
- [Kubernetes Infra](https://github.com/tiagomiele/fiap-tech-challenge-fase3-oficina-kubernetes-infra)

## Licença

Consulte o arquivo [LICENSE](LICENSE).
