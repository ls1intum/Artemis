# Server architecture gates in detail

Every rule here is enforced by a test. The reason matters as much as the rule: several of these
forbid something that looks perfectly reasonable, and knowing why stops you working around the
check instead of the problem.

## Transactions

**Rule.** No `@Transactional`, `TransactionTemplate`, or `PlatformTransactionManager` in services or
controllers. Transaction boundaries may only be defined inside repositories, typically for
modifying queries.

**Enforced by.** `testTransactional` in
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/module/AbstractModuleRepositoryArchitectureTest.java`,
which each module's `*RepositoryArchitectureTest` subclass runs.

**Consequence for you.** A REST call is not one transaction. Do not write code that assumes reads
later in the call see writes from earlier in the call rolled into one atomic unit, and do not
attempt to coordinate cache eviction across a request boundary that does not exist.

## Persistence access

**Rule.** No injected `EntityManager` or `EntityManagerFactory`. No `JdbcClient`, `JdbcTemplate`, or
`DataSource`. All persistence goes through Spring Data repositories. Where there is no entity to
name, write a `@Query` with `nativeQuery = true`.

**Enforced by.** `shouldNotUseEntityManagerDirectly` and `shouldNotUseRawJdbcDirectly` in
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/ArchitectureTest.java`. The raw JDBC rule
permits `core.config` only.

**The exception list is grandfathering, not permission.** `shouldNotUseEntityManagerDirectly`
excludes three classes and carries a TODO to refactor them away: `RepositoryImpl`,
`CustomPostRepositoryImpl`, and `TitleCacheEvictionService`. The last is the one you are most
likely to read, because it is also the canonical cache-eviction pattern below; it holds an
`EntityManagerFactory` only to reach the Hibernate `EventListenerRegistry` and register itself as a
`PostUpdateEventListener` / `PostDeleteEventListener`. Copy its eviction logic, not its
constructor. A new class taking an `EntityManagerFactory` fails the rule, and adding yourself to
the list is the wrong fix.

## Distributed data

**Rule.** Never use Hazelcast or Redis directly. Everything crossing a node boundary, including the
build job queue, feature toggles, scheduling messages, websocket broker status, LTI state, Pyris
jobs, and `@Cacheable` caches, goes through `DistributedDataProvider` in
`src/main/java/de/tum/cit/aet/artemis/core/service/distributed/`.

**Enforced by.**
`src/test/java/de/tum/cit/aet/artemis/shared/architecture/DistributedDataProviderArchitectureTest.java`,
which fails the build if a production class outside a small named set of backend adapters depends
on `com.hazelcast..`, `org.redisson..`, or `org.springframework.data.redis..`.

**Why it is not merely stylistic.** The backend is selected by `artemis.distributed-data.provider`.
With the Redis backend, no Hazelcast instance is created at all, so a direct Hazelcast call does not
throw. It silently writes state nowhere.

**Adding a capability.** Add it to `DistributedDataProvider`, implement it for Hazelcast, Redis, and
Local, and add a case to
`src/test/java/de/tum/cit/aet/artemis/core/service/distributed/AbstractDistributedDataTest.java`.
That suite is the only thing keeping the three backends in agreement.

**Entry lifetimes.** Request them at the call site with `getExpiringMap(name, ttl)`. `getMap(name)`
rejects a per-entry TTL on purpose: a backend map configuration only applies to that backend, so a
TTL configured there would silently not apply under a different provider.

Full guidance: `documentation/docs/developer/guidelines/distributed-data.mdx`.

## Caching

**Rule.** No `@Cache` (Hibernate second-level) annotations on entities or associations.

**Enforced by.** `testNoHibernateSecondLevelCacheAnnotation` in `ArchitectureTest.java`.

**Why.** The second-level cache is disabled cluster-wide. `@Modifying @Query` repository methods
bypass its invalidation, and because there is no service-level `@Transactional` there is no clean
place to coordinate eviction within a REST call. Both produced cross-node stale-read bugs.

**What to use instead.** Spring `@Cacheable`, which resolves against the `RoutingCacheManager` in
`src/main/java/de/tum/cit/aet/artemis/core/config/cache/CacheManagerConfiguration.java`. It routes
each cache to one of two managers:

- **Per-node Caffeine**, for the blob caches named in `BLOB_CACHE_NAMES`
  (`src/main/java/de/tum/cit/aet/artemis/core/config/cache/BlobCacheConfiguration.java`: `files`,
  `plantUmlPng`, `plantUmlSvg`) and the title caches named in `TITLE_CACHE_NAMES`
  (`src/main/java/de/tum/cit/aet/artemis/core/config/cache/TitleCacheConfiguration.java`).
- **The distributed data provider**, for everything else.

Every per-node cache also expires entries after a time-to-live. That TTL is the price of moving a
cache off the shared store, and it is the deciding question when you add one: if staleness would be
visible for long, the cache belongs in the distributed manager instead.

**Cache records, not entities.** A cached Hibernate entity carries its proxies and its association
graph with it. Cache a DTO or a projection.

**Always pair it with explicit eviction.** Either `@CacheEvict` on the writing service, or a
Hibernate `PostUpdateEventListener` / `PostDeleteEventListener`. The canonical patterns are
`src/main/java/de/tum/cit/aet/artemis/core/service/TitleCacheEvictionService.java` and, for
propagating the eviction of a per-node entry to every node,
`src/main/java/de/tum/cit/aet/artemis/core/service/cache/PerNodeCacheEvictionService.java`. The
latter broadcasts over a plain topic on purpose: a dropped broadcast self-corrects within the TTL,
so the retention cost of a reliable topic buys nothing.

Read `TitleCacheEvictionService` for the eviction logic, not for how it obtains its listener
registration: its `EntityManagerFactory` is a grandfathered exception, as described under
persistence access above.

**The bar.** A measured performance gain that justifies the eviction-correctness work. The default
answer is: do not cache. Full rationale and history:
`documentation/docs/developer/guidelines/caching.mdx`.

## DTOs

**Rule.** REST endpoints use DTOs, written as Java records. DTOs in a `dto` package need
`@JsonInclude(JsonInclude.Include.NON_EMPTY)`, not `NON_NULL`, and a name ending in `DTO`.

**Enforced by.** `testJsonIncludeNonEmpty` and `testNoClassFieldsInDtos` in `ArchitectureTest.java`.

**Two things that surprise people.** The rule scans test classes that live in `dto` packages too,
so a test helper placed there must also satisfy the naming rule. And the architecture thresholds
count violations, not DTOs, so the number in the test does not correspond to a DTO count.

## Module boundaries

**Rule.** Modules are packages within one source set, enforced only by ArchUnit. Reach an optional
module through `Optional<*Api>`, never through its repository.

**Watch out for.** Module-scoped rules only run where the corresponding `*ArchitectureTest`
subclass exists. Some modules have no subclass for some rule families, so the absence of a failure
does not prove compliance. There is also an ignore list that can hide an optional-module repository
leak.

## Case conversion

**Rule.** `String.toLowerCase()` and `String.toUpperCase()` may not be called without a locale, in
production or in test code. `Locale.ROOT` is the default choice, because it folds case the same way
everywhere, which is what a machine-facing value needs: identifiers, logins, emails, file names and
extensions, MIME types, header values, enum names, protocol tokens, URL segments, search
normalization. `Locale.ENGLISH` is for the places where the surrounding code already uses it for the
same kind of value, so that the two agree byte for byte, as `User.setLogin` and the two
authentication providers do for logins.

**Enforced by.** `testNoLocaleLessCaseConversion` in `ArchitectureTest.java`. It has two halves,
because ArchUnit models a method reference as a `JavaMethodReference` and not as a `JavaMethodCall`:
`callMethod` catches `value.toLowerCase()`, and a separate condition catches
`String::toLowerCase`. Naming no parameter types is what restricts the first half to the
locale-less overloads, so `toLowerCase(Locale.ROOT)` is not matched.

**Why it matters.** Under a Turkish locale the ASCII letter `I` lowercases to the dotless `ı`
rather than to `i`. That turned `System.getProperty("os.name").toLowerCase()` into `wındows` and
made the Windows branch in `WebConfigurer` stop matching, with nothing in the logs to say so.

**No exemptions.** Nothing in the codebase needs the default locale, so the rule has no exception
list. If you find a call that genuinely formats text for a user in their own language, keep the
default locale, say why in a comment, and add the class to a narrowly scoped exemption rather than
widening the rule.

## Counted gates

These compare a repository-wide count against a recorded limit that develop already sits at, so an
otherwise good change can fail them.

**Large classes.** `supporting_scripts/analyze_java_files.py` flags classes over 1000 lines and
compares the count against a maximum. Touching an already-oversized class can push the count up.
Extract a service rather than raising the limit.

**Bean instantiations.** `.github/workflows/ci-bean-instantiations.yml`. Adding a `@Configuration`
or a bean can trip it, and the failure surfaces in a step whose name does not mention counting.

**Query quality.** A new `@EntityGraph` with more fetch paths than the baseline allows fails the
Query Quality Check job in `.github/workflows/ci-quality.yml`. Reuse an existing counted method
where you can. Local check: `supporting_scripts/find_slow_queries.py`.

## Database

**No triggers and no stored routines.** The entity design is the place to express this instead.

**Adding a NOT NULL column to an existing table** needs the guarded migration pattern. See
`skills/liquibase-migration/SKILL.md`.
