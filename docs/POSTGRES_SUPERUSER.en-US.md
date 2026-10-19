# PostgreSQL superuser in the IAM contract

The `POSTGRES` provider adds `POSTGRES_SUPERUSER` to the groups only when a
connection accepted by the database queries `pg_catalog.pg_roles` and confirms
`rolsuper` for `current_user`.

The query does not use the login name as proof of privilege. A role named
`postgres` without SUPERUSER does not receive the marker. A role with another
name that has SUPERUSER does receive it. The application user-hash fallback and
`resolve(username)` do not add the marker, since they do not prove the role's
privilege.

The group configured by `AUTH_PG_ACCESS_GROUP` continues to be returned. This
additional contract lets the ERP distinguish direct superuser entry without
re-validating the password or copying the technical `sa` certificate to a personal
connection. LDAPS settings, service certificates and per-user issuance remain
the IAM's responsibility.

The suite includes verification of the query, absence of name-based escalation
and password rejection. CI also uses a disposable PostgreSQL 18 to authenticate a
superuser role, a regular role and a wrong password. That test disables TLS only
on the disposable CI database; it does not prove the real environment's PKI nor
change its TLS options.

The AD primary group `Domain Users` is also returned when `primaryGroupID=513`,
even when it does not appear in `memberOf`. This lets the consumer preserve
direct entry without inventing groups from the OU. Other primary IDs are not
converted to `Domain Users`.