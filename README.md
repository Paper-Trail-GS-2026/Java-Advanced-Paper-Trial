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

## Testes

```bash
./gradlew test
```

Os testes de autenticação usam H2 em memória e chaves RSA temporárias geradas na
execução. Os testes de IA com chamadas reais são habilitados quando `OPENAI_API_KEY`
está definida; essas chamadas usam a conta configurada.
