---
name: server-arch-gates
description: Check Artemis server code against the architectural rules the build enforces, before pushing. Use when writing or changing Java under src/main/java, adding a service, repository, REST resource, DTO, cache, or cross-node state, or when an ArchUnit test fails and the message does not make the rule obvious. Gives the rule, the reason, and the exact local command that proves it.
---

# Server architecture gates

Artemis enforces its server conventions with a large ArchUnit suite under `src/test/java`, most of
it module-scoped subclasses of a handful of abstract rule bases. They are not style preferences. Each one exists because the pattern it forbids
produced a production bug. The failure messages are often terse, so this skill maps a change to the
rules it is subject to and to the reason behind each.

## Run them locally

The whole architecture suite, which is what the Server Code Style job runs:

```bash
./gradlew test -DincludeTags='ArchitectureTest' -x webapp
```

This is much faster than the full server test suite. Run it before pushing any change to
`src/main/java`. A violation fails both Server Code Style and Server Tests, so it is worth catching
locally.

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

The detail for each, with the reason and the failing rule name, is in `reference/gates.md`. Read
it rather than guessing; several of these rules forbid something that looks completely reasonable.

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

Three classes sit on that rule's exception list, carrying a TODO to refactor them away. One of them
is `TitleCacheEvictionService`, which holds an `EntityManagerFactory` purely to reach the Hibernate
`EventListenerRegistry` and register itself as a listener. So when the caching section below calls
it the canonical eviction pattern, copy its eviction logic, not its constructor: a new class doing
the same thing fails the rule, because the list is grandfathering rather than permission. Raw JDBC
has no per-class exceptions at all; only `core.config` may hold a `DataSource`.

**Never touch Hazelcast or Redis directly.** All cross-node state goes through
`DistributedDataProvider` in
`src/main/java/de/tum/cit/aet/artemis/core/service/distributed/`. Enforced by
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/DistributedDataProviderArchitectureTest.java`.
The backend is configurable, so direct usage does not fail loudly, it silently loses the state.

**No Hibernate second-level cache.** No `@Cache` on entities or associations. Enforced by
`testNoHibernateSecondLevelCacheAnnotation` in `ArchitectureTest.java`. For DTO and projection
caching use Spring `@Cacheable`, always paired with explicit eviction.

**Reach optional modules through their API.** Use `Optional<*Api>`, never another module's
repository directly.

## Before adding a cache

The default answer is not to. The bar is a measured performance gain that justifies the
eviction-correctness work, because there is no service-level transaction boundary to coordinate
eviction within a request. See `documentation/docs/developer/guidelines/caching.mdx` for the full
rationale, and `reference/gates.md` for the pattern if you do proceed.

## Adding a capability to the distributed data layer

If `DistributedDataProvider` lacks what you need, add it there, implement it for all three backends
(Hazelcast, Redis, Local), and add a case to `AbstractDistributedDataTest`. That suite is what keeps
the backends in agreement. Request entry lifetimes at the call site with
`getExpiringMap(name, ttl)`; `getMap(name)` rejects a per-entry TTL deliberately, because a backend
map configuration only applies to that one backend. Full guidance:
`documentation/docs/developer/guidelines/distributed-data.mdx`.
