---
name: server-arch-gates
description: Apply Artemis architecture rules when changing server Java code or diagnosing ArchUnit violations.
---

# Server architecture gates

Architecture rules are enforced under `src/test/java/de/tum/cit/aet/artemis/shared/architecture/`
and by module-scoped subclasses. Prefer constructor injection for Spring beans; follow
`documentation/docs/developer/guidelines/server-development.mdx` for server coding conventions.

## Run them locally

The whole architecture suite, which is what the Server Code Style job runs:

```bash
./gradlew test -DincludeTags='ArchitectureTest' -x webapp
```

Run this for changes to `src/main/java`. Architecture violations can fail both Server Code Style
and Server Tests.

A single class while iterating:

```bash
./gradlew test --tests ArchitectureTest -x webapp
```

## Which rules apply to what you changed

| You changed                            | Read                                                |
| -------------------------------------- | --------------------------------------------------- |
| A service or REST resource             | Transactions, persistence access, module boundaries |
| A repository                           | Transactions, raw JDBC                              |
| A DTO record                           | DTO conventions                                     |
| Anything holding state across requests | Caching, distributed data                           |
| An entity or an association            | Caching, entity conventions                         |
| Anything at all in a large file        | Counted gates                                       |
| Anything that lowercases or uppercases | Case conversion                                     |

Read the relevant section of `reference/gates.md` for exceptions and rule names.

## The rules most often broken

**No transaction boundaries in services or controllers.** `@Transactional`,
`TransactionTemplate`, and `PlatformTransactionManager` belong in repositories, typically on
modifying queries. Enforced by `testTransactional` in
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/module/AbstractModuleRepositoryArchitectureTest.java`.

**No direct persistence access.** No injected `EntityManager` or `EntityManagerFactory`, and no
`JdbcClient`, `JdbcTemplate`, or `DataSource`. Write the statement as a `@Query` on a repository,
with `nativeQuery = true` where there is no entity to name. Enforced by
`shouldNotUseEntityManagerDirectly` and `shouldNotUseRawJdbcDirectly` in
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/ArchitectureTest.java`.

Existing exceptions are listed in `ArchitectureTest.java`; do not copy their direct persistence
access into new classes. In particular, `TitleCacheEvictionService` is an eviction example, not
an example of permitted `EntityManagerFactory` injection. Only `core.config` may hold a `DataSource`.

**Never touch Hazelcast or Redis directly.** All cross-node state goes through
`DistributedDataProvider` in
`src/main/java/de/tum/cit/aet/artemis/core/service/distributed/`. Enforced by
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/DistributedDataProviderArchitectureTest.java`.
Direct backend access bypasses the configured provider.

**No Hibernate second-level cache.** No `@Cache` on entities or associations. Enforced by
`testNoHibernateSecondLevelCacheAnnotation` in `ArchitectureTest.java`. For DTO and projection
caching use Spring `@Cacheable`, always paired with explicit eviction.

**Reach optional modules through their API.** Use `Optional<*Api>`, never another module's
repository directly.

**Never fold case without a locale.** `String.toLowerCase()` and `String.toUpperCase()` use the JVM
default locale, so the same input gives a different answer depending on where the server runs. Pass
`Locale.ROOT` for machine-facing values and `Locale.ENGLISH` only where the surrounding code already
does for that kind of value. Enforced by `testNoLocaleLessCaseConversion` in `ArchitectureTest.java`,
over production and test classes both.

## Before adding a cache

The default answer is not to. The bar is a measured performance gain that justifies the
eviction-correctness work, because there is no service-level transaction boundary to coordinate
eviction within a request. See `documentation/docs/developer/guidelines/caching.mdx` for the full
rationale, and `reference/gates.md` for the pattern if you do proceed.

## Adding a capability to the distributed data layer

If `DistributedDataProvider` lacks what you need, add it there, implement it for each backend, and
add a case to `AbstractDistributedDataTest`. That suite is what keeps the backends in agreement.
Request entry lifetimes at the call site with
`getExpiringMap(name, ttl)`; `getMap(name)` rejects a per-entry TTL deliberately, because a backend
map configuration only applies to that one backend. Full guidance:
`documentation/docs/developer/guidelines/distributed-data.mdx`.
