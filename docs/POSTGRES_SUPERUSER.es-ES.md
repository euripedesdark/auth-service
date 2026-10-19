# Superusuario de PostgreSQL en el contrato IAM

El provider `POSTGRES` agrega `POSTGRES_SUPERUSER` a los grupos solo cuando una
conexión aceptada por la base consulta `pg_catalog.pg_roles` y confirma
`rolsuper` para `current_user`.

La consulta no usa el nombre de login como prueba de privilegio. Una role
llamada `postgres` sin SUPERUSER no recibe el marcador. Una role con otro nombre
y SUPERUSER sí lo recibe. El fallback por hash de usuario de la aplicación y
`resolve(username)` no agregan el marcador, pues no prueban el privilegio de la
role.

El grupo configurado por `AUTH_PG_ACCESS_GROUP` sigue retornándose. Este contrato
adicional permite al ERP distinguir la entrada directa del superusuario sin
revalidar la contraseña ni copiar el certificado técnico `sa` a una conexión
personal. Las configuraciones de LDAPS, certificados de servicio y emisión por
usuario siguen bajo responsabilidad del IAM.

La suite incluye verificación de la consulta, ausencia de escalada por nombre y
rechazo de contraseña. El CI también usa PostgreSQL 18 descartable para
autenticar una role superusuaria, una role común y una contraseña errónea. Esa
prueba usa TLS deshabilitado solo en la base descartable del CI; no comprueba la
PKI del entorno real ni altera sus opciones TLS.

El grupo primario AD `Domain Users` también se retorna cuando
`primaryGroupID=513`, aunque no aparezca en `memberOf`. Esto permite al
consumidor preservar la entrada directa sin inventar grupos a partir de la OU.
Otros IDs primarios no se convierten a `Domain Users`.