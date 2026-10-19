# Superutilisateur PostgreSQL dans le contrat IAM

Le provider `POSTGRES` ajoute `POSTGRES_SUPERUSER` aux groupes uniquement lorsqu'une
connexion acceptée par la base interroge `pg_catalog.pg_roles` et confirme
`rolsuper` pour `current_user`.

La requête n'utilise pas le nom de login comme preuve de privilège. Un rôle
nommé `postgres` sans SUPERUSER ne reçoit pas le marqueur. Un rôle avec un autre
nom et SUPERUSER le reçoit. Le fallback par hash d'utilisateur de l'application
et `resolve(username)` n'ajoutent pas le marqueur, car ils ne prouvent pas le
privilège du rôle.

Le groupe configuré par `AUTH_PG_ACCESS_GROUP` continue d'être retourné. Ce
contrat additionnel permet à l'ERP de distinguer l'entrée directe du
superutilisateur sans revalider le mot de passe ni copier le certificat
technique `sa` vers une connexion personnelle. Les configurations LDAPS,
certificats de service et émission par utilisateur restent de la responsabilité
de l'IAM.

La suite inclut la vérification de la requête, l'absence d'escalade par nom et
le refus de mot de passe. Le CI utilise aussi un PostgreSQL 18 jetable pour
authentifier un rôle superutilisateur, un rôle ordinaire et un mauvais mot de
passe. Ce test désactive TLS uniquement sur la base jetable du CI ; il ne prouve
pas la PKI de l'environnement réel ni ne modifie ses options TLS.

Le groupe primaire AD `Domain Users` est également retourné quand
`primaryGroupID=513`, même s'il n'apparaît pas dans `memberOf`. Cela permet au
consommateur de préserver l'entrée directe sans inventer des groupes à partir de
l'OU. Les autres IDs primaires ne sont pas convertis en `Domain Users`.