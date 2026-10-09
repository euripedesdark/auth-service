# Superusuário PostgreSQL no contrato IAM

O provider `POSTGRES` adiciona `POSTGRES_SUPERUSER` aos grupos somente quando uma conexão aceita pelo banco consulta `pg_catalog.pg_roles` e confirma `rolsuper` para `current_user`.

A consulta não usa o nome de login como prova de privilégio. Uma role chamada `postgres` sem SUPERUSER não recebe o marcador. Uma role com outro nome e SUPERUSER recebe. O fallback por hash de usuário da aplicação e `resolve(username)` não adicionam o marcador, pois não provam o privilégio da role.

O grupo configurado por `AUTH_PG_ACCESS_GROUP` continua sendo retornado. Esse contrato adicional permite ao ERP distinguir a entrada direta do superusuário sem validar a senha novamente ou copiar o certificado técnico `sa` para uma conexão pessoal. As configurações de LDAPS, certificados de serviço e emissão por usuário permanecem sob responsabilidade do IAM.

A suíte inclui verificação da consulta, ausência de escalada por nome e recusa de senha. O CI também usa PostgreSQL 18 descartável para autenticar uma role superusuária, uma role comum e uma senha errada. Esse teste usa TLS desabilitado somente no banco descartável do CI; não comprova a PKI do ambiente real nem altera suas opções TLS.

O grupo primário AD `Domain Users` também é retornado quando `primaryGroupID=513`, mesmo que não apareça em `memberOf`. Isso permite ao consumidor preservar entrada direta sem inventar grupos a partir da OU. Outros IDs primários não são convertidos em `Domain Users`.
