# PaperTrail

API Java do PaperTrail: identidade, catálogo de serviços públicos e orientação com fontes.

## Executar localmente

Use Java 17 e o Oracle configurado para o projeto. Na primeira instalação, execute
`docs/database/papertrail.sql` no SQL Developer com F5. Se as tabelas `PT_` já estiverem
instaladas, siga direto para a configuração da aplicação.

No Git Bash, dentro do repositório, gere um par RSA próprio:

```bash
mkdir -p .local
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out .local/private.pem
openssl pkey -in .local/private.pem -pubout -out .local/public.pem
export JWT_PRIVATE_KEY="file:$(pwd -W)/.local/private.pem"
export JWT_PUBLIC_KEY="file:$(pwd -W)/.local/public.pem"
```

Configure a conta Oracle na mesma sessão do terminal:

```bash
read -r -p "Oracle user: " DATABASE_USERNAME
read -r -s -p "Oracle password: " DATABASE_PASSWORD
export DATABASE_USERNAME DATABASE_PASSWORD
./gradlew bootRun
```

A URL padrão é `jdbc:oracle:thin:@//oracle.fiap.com.br:1521/ORCL`.
Para outro servidor, defina `DATABASE_URL` antes de iniciar. A aplicação apenas valida
o schema; o Flyway fica desativado enquanto a instalação é feita pelo script SQL.

As chaves em `.local/` ficam fora do Git. Preserve o par entre execuções e compartilhe
somente a chave pública com a API .NET.

O Swagger fica em `http://localhost:8080/swagger-ui/index.html`.
Para usar a orientação com IA, defina também `OPENAI_API_KEY`.

## Autenticação

O cadastro público cria usuários `CITIZEN`. O papel `MANAGER` é atribuído pela
administração do banco, sem opção de escolhê-lo no cadastro.

```bash
curl -X POST http://localhost:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"name":"Cidadão de teste","email":"citizen@example.invalid","password":"example-password"}'

curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"citizen@example.invalid","password":"example-password"}'
```

O login retorna `{"token":"..."}`. Envie o valor como `Authorization: Bearer <token>`
nas chamadas protegidas. `GET /auth/me` retorna os dados do usuário autenticado.

O JWT usa RS256, emissor `papertrail`, audiência `papertrail-api` e validade de
30 minutos. O `sub` contém o ID do usuário como texto e `roles` contém uma lista
com `CITIZEN` ou `MANAGER`. O Java também verifica se o usuário e seu papel continuam
válidos no banco a cada requisição.

As senhas usam BCrypt e aceitam de 8 a 72 caracteres, com limite de 72 bytes em UTF-8.
E-mails são salvos sem espaços nas pontas e em minúsculas.

## Catálogo

Cidadãos e gestores autenticados podem consultar o catálogo. Somente gestores
podem criar, atualizar ou excluir registros.

| Método | Rota | Uso |
| --- | --- | --- |
| GET | `/categories` | Listar categorias com paginação |
| GET | `/categories/{id}` | Consultar uma categoria |
| POST | `/categories` | Criar uma categoria com `name` |
| PUT | `/categories/{id}` | Atualizar uma categoria |
| DELETE | `/categories/{id}` | Excluir uma categoria sem serviços vinculados |
| GET | `/services` | Listar serviços com paginação |
| GET | `/services/{id}` | Consultar um serviço |
| POST | `/services` | Criar um serviço |
| PUT | `/services/{id}` | Atualizar um serviço e incrementar sua versão |
| DELETE | `/services/{id}` | Excluir um serviço sem requisitos ou fontes vinculados |
| GET | `/services/{id}/requirements-snapshot` | Consultar a versão e os requisitos para criar um preparativo |

As listas aceitam `page`, `size` e `sort`, por exemplo
`/services?page=0&size=10&sort=title,asc`. A página inicial é 0 e o limite é de
100 registros por página.

A criação e atualização de serviços recebem `categoryId`, `title`, `description`
e `cityName`. Serviços novos começam na versão 1. Exclusões com vínculos e nomes
de categoria duplicados retornam 409; registros inexistentes retornam 404.

O snapshot retorna `serviceId`, `version`, `title` e
`requirements[{id,title,required,sourceUrl}]`. Os requisitos vêm em ordem de ID,
incluindo os opcionais. Um serviço sem requisitos retorna uma lista vazia.
Os dados de demonstração do script SQL são fictícios.

### Integração com o módulo de requisitos

`CatalogReader.currentVersion(serviceId)` consulta a versão e retorna 404 se o
serviço não existir. O modelo `Requirement` e seu repositório estão disponíveis
para o módulo de requisitos; o CRUD desse módulo será implementado separadamente.

Para alterar requisitos, chame `CatalogVersionService.incrementVersion(serviceId)`
uma vez, antes de editar os dados, dentro da mesma transação. O método bloqueia a
linha do serviço e retorna a entidade com a nova versão. Se qualquer operação
falhar, a versão e as alterações dos requisitos são revertidas juntas.

O incremento publica `ServiceVersionChanged`, um evento local do Spring com
`eventId`, `serviceId`, `version` e `occurredAt`. O adaptador de mensageria deve usar
`@TransactionalEventListener(phase = AFTER_COMMIT)` para agir após a confirmação
da transação. O transporte por broker e o tratamento de falhas de publicação
continuam pendentes.

## Testes

```bash
./gradlew test
```

Os testes de autenticação usam H2 em memória e chaves RSA temporárias geradas na
execução. Os testes de IA com chamadas reais são habilitados quando `OPENAI_API_KEY`
está definida; essas chamadas usam a conta configurada.
