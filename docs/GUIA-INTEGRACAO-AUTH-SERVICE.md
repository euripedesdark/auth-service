# Guia de Implantação e Integração — Auth Service

## 1. Objetivo

O Auth Service é o serviço central de autenticação e resolução de identidade dos sistemas que precisam validar usuários contra o Active Directory (AD/LDAP).

Neste estágio, o contrato implementado é **REST + HTTP Basic Authentication**. O serviço não emite JWT, OAuth2/OIDC ou outros tokens.

Fluxo atual:

~~~text
ERP / SGDB-adapter / Firewall / Proxy / outro serviço
                    |
                    | HTTPS + HTTP Basic
                    v
             Auth Service :8080
                    |
                    | LDAPS
                    v
             Active Directory
~~~

O Auth Service retorna a identidade:

~~~json
{
  "identityId": "CN=Euripedes Batista,OU=Usuarios,DC=homelab,DC=local",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "ERP-Administradores",
    "ERP-Financeiro"
  ]
}
~~~

> **Importante:** grupos do AD são retornados como dados de identidade. O serviço consumidor continua responsável por aplicar suas próprias regras de autorização.

---

## 2. Dados padrão de implantação

### Porta HTTP

A aplicação Spring Boot utiliza **porta 8080 por padrão**, pois não existe uma porta diferente definida no application.yml.

Para alterar:

~~~bash
SERVER_PORT=8081
~~~

ou:

~~~yaml
server:
  port: 8081
~~~

Em produção, recomenda-se colocar o Auth Service atrás de HTTPS/reverse proxy e não expor a porta 8080 diretamente à rede de usuários.

### Endereço de exemplo

Supondo:

- Auth Service: 192.168.2.50
- Porta: 8080
- AD/DC: 100.100.100.100
- domínio AD: homelab.local

O endereço interno será:

~~~text
http://192.168.2.50:8080
~~~

Em produção:

~~~text
https://auth.homelab.local
~~~

O hostname HTTPS é apenas um exemplo; deve ser substituído pelo DNS real da instalação.

---

## 3. Pré-requisitos

### Auth Service

- Java compatível com a versão utilizada no build.
- Maven para compilação.
- Acesso de rede ao AD.
- Certificado confiável para LDAPS.
- Conta de serviço do AD para consultas.
- DNS funcional.
- Hora sincronizada nos servidores.

### Active Directory

O Auth Service precisa conseguir:

1. estabelecer conexão LDAPS;
2. autenticar a conta de serviço;
3. pesquisar usuários;
4. consultar o atributo memberOf;
5. autenticar a senha apresentada pelo usuário.

A conta de serviço **não deve ser uma conta administrativa de domínio**.

---

# 4. Configuração do Auth Service

A configuração atual usa variáveis de ambiente.

## 4.1 Variáveis obrigatórias

| Variável | Exemplo | Finalidade |
|---|---|---|
| AUTH_LDAP_URL | ldaps://100.100.100.100:636 | Endpoint LDAP seguro |
| AUTH_LDAP_BASE | DC=homelab,DC=local | Base LDAP |
| AUTH_LDAP_USER_DN | CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local | Conta de serviço |
| AUTH_LDAP_PASSWORD | ******** | Senha da conta de serviço |
| AUTH_LDAP_USER_SEARCH_BASE | OU=Usuarios,DC=homelab,DC=local | Base de pesquisa dos usuários |

## 4.2 Variáveis opcionais

| Variável | Padrão | Finalidade |
|---|---:|---|
| AUTH_LDAP_REQUIRE_SECURE | true | Exige ldaps:// |
| AUTH_LDAP_CONNECT_TIMEOUT_MS | 5000 | Timeout de conexão |
| AUTH_LDAP_READ_TIMEOUT_MS | 5000 | Timeout de leitura |
| SERVER_PORT | 8080 | Porta HTTP da aplicação |

### Exemplo

~~~bash
export AUTH_LDAP_URL='ldaps://100.100.100.100:636'
export AUTH_LDAP_BASE='DC=homelab,DC=local'
export AUTH_LDAP_USER_DN='CN=svc-auth,OU=Service Accounts,DC=homelab,DC=local'
export AUTH_LDAP_PASSWORD='SENHA_DA_CONTA_DE_SERVICO'
export AUTH_LDAP_USER_SEARCH_BASE='OU=Usuarios,DC=homelab,DC=local'
export AUTH_LDAP_REQUIRE_SECURE='true'
export AUTH_LDAP_CONNECT_TIMEOUT_MS='5000'
export AUTH_LDAP_READ_TIMEOUT_MS='5000'
export SERVER_PORT='8080'
~~~

**Não coloque a senha da conta de serviço no Git.**

---

# 5. LDAPS e certificado

A configuração atual exige LDAPS quando:

~~~text
AUTH_LDAP_REQUIRE_SECURE=true
~~~

Nesse cenário, ldaps:// é obrigatório.

Não utilize ldap:// em produção quando a exigência de LDAP seguro estiver habilitada.

O certificado apresentado pelo controlador de domínio precisa ser confiável pela JVM que executa o Auth Service.

Exemplo conceitual:

~~~text
Auth Service
     |
     | TLS
     v
AD/DC:636
     |
     +-- certificado do servidor AD
~~~

Antes da implantação, valide:

~~~bash
openssl s_client -connect 100.100.100.100:636 -showcerts
~~~

O teste deve ser feito a partir do servidor onde o Auth Service será executado.

---

# 6. Subindo o Auth Service

Depois de configurar as variáveis:

~~~bash
mvn clean package
~~~

Execute:

~~~bash
java -jar target/auth-service-1.0.0-SNAPSHOT.jar
~~~

Ou, durante desenvolvimento:

~~~bash
mvn spring-boot:run
~~~

A aplicação ficará, por padrão, em:

~~~text
http://localhost:8080
~~~

---

# 7. Verificação de saúde

O endpoint de health é público:

~~~http
GET /actuator/health
~~~

Teste:

~~~bash
curl -i http://localhost:8080/actuator/health
~~~

Esse endpoint não substitui o teste de autenticação LDAP. Ele serve para verificar se a aplicação está respondendo.

---

# 8. Autenticação de usuário

O endpoint principal para autenticar um usuário é:

~~~http
POST /api/v1/identity/authenticate
Content-Type: application/json
~~~

Payload:

~~~json
{
  "username": "euripedes",
  "password": "SENHA_DO_USUARIO",
  "provider": "AD"
}
~~~

Exemplo:

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"username":"euripedes","password":"SENHA_DO_USUARIO","provider":"AD"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Resposta esperada:

~~~json
{
  "identityId": "CN=Euripedes,OU=Usuarios,DC=homelab,DC=local",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "ERP-Administradores"
  ]
}
~~~

## 8.1 O que o consumidor deve fazer com essa resposta

O sistema consumidor deve:

1. identificar o usuário;
2. obter os grupos;
3. localizar seu cadastro interno, quando existir;
4. aplicar suas próprias permissões;
5. criar sua própria sessão local, se necessário.

O Auth Service não deve ser tratado como banco de dados funcional do ERP.

---

# 9. Consulta de identidade usando HTTP Basic

Os endpoints GET de identidade exigem autenticação HTTP Basic.

### Identidade

~~~http
GET /api/v1/identity/me
Authorization: Basic <credenciais>
~~~

Exemplo:

~~~bash
curl -i \
  -u 'euripedes:SENHA_DO_USUARIO' \
  https://auth.homelab.local/api/v1/identity/me
~~~

### Grupos

~~~bash
curl -i \
  -u 'euripedes:SENHA_DO_USUARIO' \
  https://auth.homelab.local/api/v1/identity/groups
~~~

### Provider

~~~bash
curl -i \
  -u 'euripedes:SENHA_DO_USUARIO' \
  https://auth.homelab.local/api/v1/identity/provider
~~~

### Estado autenticado

~~~bash
curl -i \
  -u 'euripedes:SENHA_DO_USUARIO' \
  https://auth.homelab.local/api/v1/identity/authenticated
~~~

Os quatro endpoints retornam o mesmo contrato Identity; os nomes existem para separar semanticamente os usos dos consumidores.

---

# 10. Códigos HTTP que os sistemas consumidores devem tratar

| Código | Significado |
|---:|---|
| 200 | Autenticação/consulta concluída |
| 400 | Requisição inválida |
| 401 | Credenciais ausentes ou inválidas |
| 403 | Acesso negado |
| 503 | Provedor de identidade indisponível |

### Regra importante

O sistema consumidor **não deve tratar 503 como senha errada**.

~~~text
401 -> credencial inválida / não autenticado
503 -> Auth Service ou AD indisponível
~~~

Isso permite diferenciar falha de autenticação de indisponibilidade da infraestrutura.

---

# 11. Integração do ERP

O ERP pode utilizar o Auth Service como seu ponto central de autenticação.

Fluxo recomendado:

~~~text
Usuário
  |
  v
ERP
  |
  | POST /api/v1/identity/authenticate
  | provider=AD
  v
Auth Service
  |
  | LDAPS
  v
AD
  |
  | identidade + grupos
  v
Auth Service
  |
  v
ERP
  |
  +--> sessão local
  +--> usuário interno
  +--> permissões do ERP
~~~

## 11.1 Configuração sugerida do ERP

Exemplo:

~~~properties
auth.service.url=https://auth.homelab.local
auth.service.authenticate-path=/api/v1/identity/authenticate
auth.service.me-path=/api/v1/identity/me
auth.service.groups-path=/api/v1/identity/groups
auth.provider=AD
~~~

Se o ERP utilizar variáveis de ambiente:

~~~bash
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
AUTH_SERVICE_ME_PATH=/api/v1/identity/me
AUTH_SERVICE_GROUPS_PATH=/api/v1/identity/groups
~~~

## 11.2 Exemplo Java/Spring

O ERP deve possuir um cliente HTTP simples:

~~~java
public IdentityDto authenticate(String username, String password) {
    return webClient.post()
        .uri(authServiceUrl + "/api/v1/identity/authenticate")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new LoginRequest(username, password, "AD"))
        .retrieve()
        .bodyToMono(IdentityDto.class)
        .block();
}
~~~

DTO:

~~~java
public record IdentityDto(
    String identityId,
    String username,
    String provider,
    List<String> groups
) {}
~~~

---

# 12. Como o ERP deve tratar grupos do AD

O Auth Service retorna:

~~~json
"groups": [
  "ERP-Administradores",
  "ERP-Financeiro",
  "ERP-Vendas"
]
~~~

O ERP deve mapear esses grupos para suas permissões internas.

Exemplo:

~~~text
AD group                    ERP permission

ERP-Administradores   ->    ADMIN
ERP-Financeiro        ->    FINANCEIRO
ERP-Vendas            ->    VENDAS
ERP-Compras           ->    COMPRAS
ERP-Estoque           ->    ESTOQUE
~~~

Esse mapeamento pertence ao ERP.

Não coloque regras específicas do ERP dentro do Auth Service.

---

# 13. Integração com banco de dados / SGDB

## 13.1 Ponto importante

Um SGDB tradicional, como PostgreSQL ou MySQL, **não deve ser configurado como se pudesse chamar diretamente uma API REST HTTP durante cada login**, porque o Auth Service é um serviço REST e não um mecanismo LDAP nativo do banco.

Arquitetura da aplicação:

~~~text
Aplicação
   |
   +----> Auth Service ----> AD
   |
   +----> PostgreSQL/MySQL
~~~

Isso é diferente de:

~~~text
PostgreSQL ----HTTP----> Auth Service
~~~

A segunda arquitetura não é uma integração nativa fornecida pelo Auth Service atual.

---

# 14. ERP + PostgreSQL

Para um ERP com PostgreSQL, a separação é:

~~~text
                  +--> Auth Service --> AD
                  |
Usuário --> ERP --+
                  |
                  +--> PostgreSQL
~~~

O PostgreSQL armazena:

- dados do ERP;
- usuários internos, se o ERP mantiver esse cadastro;
- vínculos internos entre usuário e permissões;
- auditoria;
- configurações funcionais.

O Auth Service fica responsável por:

- validar credenciais AD;
- resolver identidade;
- retornar grupos AD.

## 14.1 Exemplo de configuração

~~~properties
auth.service.url=https://auth.homelab.local
database.url=jdbc:postgresql://db-erp:5432/brasilcloud
database.username=brasilcloud
database.password=********
~~~

Fluxo:

~~~text
1. Usuário informa login/senha
2. ERP chama Auth Service
3. Auth Service valida no AD
4. Auth Service retorna identidade
5. ERP procura o usuário interno pelo username/identityId
6. ERP aplica permissões
7. ERP abre a sessão
~~~

---

# 15. Sistemas de banco que precisam autenticar usuários

Se o objetivo for permitir que uma ferramenta de administração de banco, como um cliente SQL, autentique usuários usando o mesmo AD, existem duas possibilidades diferentes.

### Opção A — autenticação nativa do SGDB

O SGDB pode ser configurado para usar LDAP/AD diretamente, quando o produto suportar isso.

Fluxo:

~~~text
Cliente SQL --> SGDB --> LDAP/AD
~~~

Nesse caso o Auth Service não participa da autenticação.

### Opção B — autenticação através do Auth Service

Para obrigatoriamente passar pelo Auth Service:

~~~text
Cliente SQL
     |
     v
Gateway/adapter/proxy de autenticação
     |
     v
Auth Service
     |
     v
AD
~~~

Isso exige um componente intermediário que conheça o protocolo de autenticação esperado pelo SGDB e converta a autenticação para HTTP.

**Não se deve configurar o SGDB para apontar simplesmente para http://auth-service:8080.**

---

# 16. Integração com firewall/proxy

Para firewall, proxy ou gateway que possua aplicação própria:

~~~text
Usuário
  |
  v
Firewall / Proxy
  |
  | HTTPS
  v
Auth Service
  |
  | LDAPS
  v
AD
~~~

Configuração conceitual:

~~~text
AUTH_SERVICE_URL=https://auth.homelab.local
AUTH_SERVICE_PROVIDER=AD
AUTH_SERVICE_AUTHENTICATE_PATH=/api/v1/identity/authenticate
~~~

O firewall/proxy recebe:

~~~json
{
  "identityId": "...",
  "username": "euripedes",
  "provider": "AD",
  "groups": [
    "FW-Administradores"
  ]
}
~~~

E decide localmente:

~~~text
FW-Administradores -> administração
FW-Operadores      -> operação
FW-Auditoria       -> somente leitura
~~~

---

# 17. Integração de um novo serviço

Para adicionar qualquer novo sistema:

### Passo 1 — Definir a URL

~~~text
https://auth.exemplo.local
~~~

### Passo 2 — Definir a rota de autenticação

~~~text
POST /api/v1/identity/authenticate
~~~

### Passo 3 — Enviar

~~~json
{
  "username": "usuario",
  "password": "senha",
  "provider": "AD"
}
~~~

### Passo 4 — Validar resposta

O serviço deve exigir:

~~~text
HTTP 200
~~~

e validar:

~~~text
identityId
username
provider
groups
~~~

### Passo 5 — Criar a identidade local

O sistema pode procurar o usuário pelo username ou, quando precisar de um identificador persistente, por identityId.

### Passo 6 — Aplicar autorização local

O serviço consumidor decide quais funcionalidades o usuário pode executar.

---

# 18. Segurança de rede

Em produção, não exponha o endpoint de autenticação sem TLS.

Recomendação:

~~~text
                    INTERNET
                       |
                       X
                       |
                 [Firewall]
                       |
                       v
             Reverse Proxy / LB
                       |
                     HTTPS
                       |
                       v
                Auth Service
                    :8080
                       |
                     LDAPS
                    :636
                       |
                       v
                      AD
~~~

A porta 8080 pode ficar restrita à rede interna ou ao reverse proxy.

Exemplo:

~~~text
Reverse Proxy -> Auth Service:8080    ALLOW
ERP            -> Auth Service:8080    ALLOW
Firewall       -> Auth Service:8080    ALLOW
Usuários       -> Auth Service:8080    DENY
Internet       -> Auth Service:8080    DENY
~~~

A política real deve ser adaptada à topologia da instalação.

---

# 19. DNS recomendado

Crie um nome estável para o serviço:

~~~text
auth.homelab.local
~~~

Exemplo:

~~~text
auth.homelab.local -> 192.168.2.50
~~~

Os sistemas devem utilizar o hostname, e não o IP diretamente:

~~~text
https://auth.homelab.local
~~~

Isso permite trocar servidor, balanceador ou IP sem alterar todos os sistemas consumidores.

---

# 20. Reverse proxy

Exemplo conceitual com Nginx:

~~~nginx
server {
    listen 443 ssl;
    server_name auth.homelab.local;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
}
~~~

O certificado deve ser emitido para o hostname utilizado pelos consumidores.

---

# 21. Timeouts e indisponibilidade

Os consumidores devem possuir timeout próprio para chamadas ao Auth Service.

Não deixe uma requisição de login ficar indefinidamente bloqueada.

Exemplo:

~~~text
Connect timeout: 5s
Read timeout:    5s
~~~

Quando o Auth Service retornar:

~~~text
503 Service Unavailable
~~~

o consumidor deve tratar como indisponibilidade do serviço/provedor, e não como senha incorreta.

---

# 22. Não armazenar senha AD

ERP, firewall, proxy e outros consumidores não devem armazenar a senha do usuário para reutilização.

Fluxo correto:

~~~text
senha
  |
  v
Auth Service
  |
  v
AD
~~~

Depois da autenticação, o consumidor deve manter somente a sessão/identidade necessária para seu próprio funcionamento.

---

# 23. Logs

Nunca registre em log:

~~~text
password
Authorization: Basic ...
credenciais LDAP
tokens contendo credenciais
~~~

Pode registrar, conforme a política de auditoria:

~~~text
timestamp
serviço consumidor
username
resultado
provider
motivo técnico sem senha
correlation/request id
~~~

Exemplo:

~~~text
2026-09-30T15:30:00Z
service=erp
username=euripedes
provider=AD
result=SUCCESS
~~~

---

# 24. Swagger / OpenAPI

A aplicação disponibiliza documentação OpenAPI em:

~~~text
/v3/api-docs
~~~

e Swagger UI em:

~~~text
/swagger-ui.html
~~~

Exemplo:

~~~text
https://auth.homelab.local/swagger-ui.html
~~~

Esses endpoints são úteis durante integração e homologação.

Em produção, avalie a exposição pública da documentação e restrinja-a conforme a política de segurança.

---

# 25. Checklist de implantação

## Auth Service

- [ ] Java instalado
- [ ] Maven funcionando
- [ ] mvn clean test executado
- [ ] mvn clean package executado
- [ ] JAR gerado
- [ ] SERVER_PORT definido quando necessário
- [ ] variáveis LDAP configuradas
- [ ] senha LDAP fora do Git
- [ ] LDAPS funcionando
- [ ] certificado AD confiável
- [ ] DNS funcionando
- [ ] relógio sincronizado
- [ ] firewall liberando somente os consumidores necessários

## Active Directory

- [ ] conta de serviço criada
- [ ] conta de serviço sem privilégios administrativos desnecessários
- [ ] LDAPS funcionando
- [ ] usuários localizados pela base configurada
- [ ] sAMAccountName funcionando
- [ ] atributo memberOf disponível

## ERP

- [ ] URL do Auth Service configurada
- [ ] provider AD
- [ ] endpoint de autenticação configurado
- [ ] DTO de identidade implementado
- [ ] tratamento de 401
- [ ] tratamento de 403
- [ ] tratamento de 503
- [ ] mapeamento dos grupos AD
- [ ] permissões locais configuradas
- [ ] senha não armazenada

## Banco / SGDB

- [ ] separar autenticação da aplicação do acesso ao banco
- [ ] não apontar o SGDB diretamente para uma URL HTTP do Auth Service
- [ ] definir se a autenticação será nativa LDAP/AD ou via adapter/proxy
- [ ] manter credenciais do banco separadas das credenciais de usuário
- [ ] restringir acesso de rede ao banco

## Firewall / Proxy

- [ ] URL do Auth Service configurada
- [ ] HTTPS habilitado
- [ ] provider AD
- [ ] grupos mapeados
- [ ] 401 tratado como falha de autenticação
- [ ] 503 tratado como indisponibilidade
- [ ] nenhuma senha armazenada

---

# 26. Teste completo de homologação

## Teste 1 — Serviço

~~~bash
curl -i https://auth.homelab.local/actuator/health
~~~

Resultado esperado:

~~~text
HTTP/1.1 200
~~~

## Teste 2 — Credencial válida

~~~bash
curl -i \
  -X POST \
  -H 'Content-Type: application/json' \
  -d '{"username":"usuario-teste","password":"SENHA","provider":"AD"}' \
  https://auth.homelab.local/api/v1/identity/authenticate
~~~

Resultado esperado:

~~~text
HTTP 200
~~~

## Teste 3 — Credencial inválida

Utilize uma senha incorreta.

Resultado esperado:

~~~text
HTTP 401
~~~

## Teste 4 — AD indisponível

Bloqueie temporariamente o acesso do Auth Service ao controlador de domínio.

Resultado esperado:

~~~text
HTTP 503
~~~

## Teste 5 — Grupo AD

Use um usuário que pertença a grupos conhecidos.

Confirme que:

~~~json
"groups": [...]
~~~

contém os grupos esperados.

---

# 27. Modelo de arquitetura para os três sistemas

Para ERP, SGDB/serviços de banco e firewall/proxy:

~~~text
                         +----------------+
                         |      AD/LDAP   |
                         +-------^--------+
                                 |
                               LDAPS
                                 |
                         +-------+--------+
                         |   AUTH SERVICE |
                         |    HTTPS       |
                         |     :8080      |
                         +---^-------^----+
                             |       |
                    HTTPS    |       | HTTPS
                             |       |
                +------------+       +------------+
                |                                 |
        +-------+-------+                 +-------+-------+
        |      ERP      |                 | Firewall/Proxy |
        +-------+-------+                 +---------------+
                |
                |
             JDBC
                |
        +-------v-------+
        | PostgreSQL /  |
        | outro SGDB    |
        +---------------+
~~~

O ponto central é:

**Auth Service autentica a identidade; cada produto continua responsável pelos seus dados e pela sua autorização funcional.**

---

# 28. Limitações do contrato atual

O consumidor deve considerar o contrato atual exatamente como está implementado.

Atualmente existem:

~~~text
POST /api/v1/identity/authenticate
GET  /api/v1/identity/me
GET  /api/v1/identity/groups
GET  /api/v1/identity/provider
GET  /api/v1/identity/authenticated
GET  /actuator/health
~~~

A autenticação HTTP é baseada em Basic Authentication.

O contrato atual **não deve ser documentado como se já fosse OAuth2/OIDC/JWT**.

Caso futuramente seja implementado um protocolo de token, esse será um novo contrato e os consumidores deverão ser migrados de forma controlada.

---

# 29. Resumo para equipes de desenvolvimento

Para integrar um sistema novo:

~~~text
1. Configure AUTH_SERVICE_URL
2. Use HTTPS
3. Chame POST /api/v1/identity/authenticate
4. Envie username/password/provider=AD
5. Valide HTTP 200
6. Leia identityId, username, provider e groups
7. Crie/recupere a identidade local
8. Aplique autorização local
9. Trate 401 como falha de credencial
10. Trate 503 como indisponibilidade
11. Nunca grave a senha AD
12. Não acople o sistema diretamente à estrutura interna do AD
~~~

Esse é o padrão de integração para ERP, firewall/proxy e demais serviços que precisem utilizar o Auth Service como ponto central de autenticação.
