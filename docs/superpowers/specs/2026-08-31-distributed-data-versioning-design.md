# Distributed data versioning and migration

- Date: 2026-08-31
- Status: proposed, not approved
- Issue: [#12137](https://github.com/ls1intum/Artemis/issues/12137)
- Scope: the Redis backend of `DistributedDataProvider`; Hazelcast and Local are affected only where noted

## Problem

Redis and Valkey keep their data across an Artemis release. That is the property operators want, because a long
build queue survives a deploy instead of vanishing the way it does with Hazelcast. It also means a new release reads
bytes an older release wrote, with nothing in between to check whether it still can.

Issue #12137 is what that looks like in production. Upgrading 8.7.3 to 8.8.1 produced:

```
com.esotericsoftware.kryo.io.KryoBufferUnderflowException: Buffer underflow.
Serialization trace:
lastBuildDate (de.tum.cit.aet.artemis.buildagent.dto.BuildAgentDetailsDTO)
buildAgentDetails (de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation)
```

and, after the store was cleared by hand, `Invalid value for HourOfDay (valid values 0 - 23): 36`, because the build
agent was still running and had already written the old layout back into the fresh store.

Two things are missing. There is no way for a release to know whether the data it is about to read was written by a
compatible build, and there is no way to discard one problematic structure without flushing everything.

## What exists today

- **No namespacing.** `RedissonDistributedDataProviderService` passes logical names straight to Redisson
  (`redissonClient.getMap(name)`), so every release that ever ran shares one keyspace.
- **Encodings differ by structure.** `BackwardCompatibleSerializationCodec` routes map *values* through JDK
  `SerializationCodec` while map keys, queue entries, set elements and topic messages stay on `Kryo5Codec`. Kryo
  writes fields positionally with no schema and no version check, which is why #12137 surfaced as a buffer underflow
  rather than a clean error. The structure operators most want to preserve, `buildJobQueue`, is on the Kryo path.
- **`serialVersionUID` is used inconsistently.** `BuildAgentDetailsDTO` is at `2L`, the other build agent DTOs at `1L`.
  On the JDK path a changed value turns old data into `InvalidClassException`, which is loud but unguided; on the Kryo
  path it is ignored entirely.
- **One-way compatibility is documented but not enforced.** `RedissonCodecConfiguration` tells operators to roll a
  Redis deployment forward rather than back. Nothing stops a rollback.
- **A precedent exists for the shape of the answer.** `DatabaseMigration` records the previous version in
  `artemis_version`, declares ordered `MigrationPath`s, and refuses to start (`System.exit(15)`) when the upgrade path
  was not followed.
- **The developer guideline is silent.** `documentation/docs/developer/guidelines/distributed-data.mdx` covers the API,
  backend selection, caches, adding a capability and testing. It has no section on evolution or upgrades.

## Goals

1. A release never reads distributed data written by an incompatible build.
2. Structures that are expensive to lose survive a release; structures that are cheap to lose may be discarded.
3. Discarding a problematic structure is a reviewed code change, not an operator typing into `redis-cli` during a deploy.
4. Forgetting to declare an incompatible change fails in CI, not in production.

## Non-goals

- Bidirectional compatibility between mixed-version nodes. Forward-only stays the rule, and this design makes it
  enforceable rather than advisory.
- Migrating Hazelcast. Hazelcast discards its data when the cluster restarts, so it has no upgrade-time problem. The
  version constant is shared so the fingerprint gate applies to both.
- Replacing Kryo across the board. That is a separate change, recommended below but not specified here.

## Design

### 1. A distributed data schema version

A single integer constant in code, for example `DistributedDataSchema.VERSION`. It is owned by developers and bumped
when any type stored in a distributed structure changes incompatibly. It is deliberately **not** the Artemis release
version: most releases change nothing about distributed data, and tying the two would discard the build queue on every
deploy, which is the behaviour this design exists to avoid.

### 2. Version-namespaced keys

`RedissonDistributedDataProviderService` prefixes every name it passes to Redisson:

```
artemis:v7:buildJobQueue
artemis:v7:buildJobQueue:queue_notification
```

One unprefixed key, `artemis:distributed-data-schema`, records the current version and the release that wrote it. It is
the analogue of the `artemis_version` table.

The consequence is the important part: a new release cannot see an old release's keys at all. Incompatibility stops
being something to detect and becomes something that cannot happen.

Namespacing alone would also make rollback safe, since the previous release would still find its own keys. That is
deliberately given up: see the drain-and-delete decision in step 4, which trades it for flat memory and a store with
no accumulating dead namespaces. A rollback is still detected and refused rather than silently reading nothing.

### 3. Per-structure carry-over policy

Each structure declares whether it must survive a version bump. Proposed starting classification of the eighteen
structures currently in use:

| Structure | Policy | Reason |
|---|---|---|
| `buildJobQueue` | carry over | queued student builds, not reconstructible |
| `processingJobs` | carry over | in-flight builds; orphan re-queue reads this map |
| `buildResultQueue` | carry over | finished results not yet persisted; losing one loses student feedback |
| `features` | carry over | the yml defaults are re-seeded at startup anyway, but a toggle an admin flipped at runtime lives only here, and it should survive an upgrade without being promoted to the database |
| `pyris-job-map` | carry over | in-flight Iris jobs |
| `ltiJwkMap`, `ltiStateAuthorizationRequestStore` | discard | short-lived in-flight launches; a retry costs one redirect |
| `buildAgentInformation`, `buildAgentAddresses`, `buildAgentReportedAddresses` | discard | agents re-register on startup |
| `destinationTracker`, `lastActionTracker`, `lastTypingTracker` | discard | websocket presence, transient |
| `dockerImageCleanupInfo` | discard | recomputable |
| `iris-dashboard-schedule-state` | discard | recomputable |
| `canceledBuildJobsTopic`, `pauseBuildAgentTopic`, `resumeBuildAgentTopic` | discard | topic messages are transient |
| `@Cacheable` caches | discard | always |

Worth noting as validation: the DTO that caused #12137, `BuildAgentInformation`, lands in *discard*. This design would
have prevented that outage with no migration code at all.

### 4. Migration steps

A `DistributedDataMigration` declares `fromVersion`, `toVersion`, and the carry-over work. The default step moves the
structures marked carry over verbatim; a step only needs custom code when a carried-over type changed shape, in which
case it reads the old namespace with the old type and writes the new.

Steps run on one core node, under a distributed lock, exactly once, before the rest of startup proceeds. Completion
updates `artemis:distributed-data-schema`.

Carry-over **drains** rather than copies: each entry is moved from the old structure to the new one and removed from
the old, so peak memory stays flat even for a large `buildJobQueue`. The old namespace is deleted as soon as the step
completes, rather than being kept alive on a grace TTL.

Draining has to be crash-safe, because a node that dies mid-migration leaves entries split across two namespaces and
readers only ever look at the new one. Two properties make that safe:

- Each entry moves with an atomic pop-from-old, push-to-new, so an entry is never in both namespaces and never in
  neither.
- A step is idempotent and resumable. It runs before startup completes and under the lock, so a restart re-enters it
  and drains whatever is left. The version key is written only after the last entry has moved, so an interrupted
  migration is indistinguishable from one that never started.

The consequence of deleting immediately is that rolling back to the previous release gives that release an empty
namespace: its data is gone, not merely hidden. This is a deliberate choice and it matches the forward-only rule
`RedissonCodecConfiguration` already documents. It is worth stating in the release notes for the first release that
ships a version bump.

### 5. Fingerprint gate in CI

The weak point of everything above is human: someone changes a DTO and forgets to bump the version. A test computes a
fingerprint over the distributed type surface (class name, `serialVersionUID`, and the component list of every
`Serializable` type reachable from a distributed structure) and compares it to a checked-in expected value. Changing a
distributed DTO without bumping the version fails the build, with a message saying which type changed and what to do.

This is the analogue of a Liquibase checksum, and it is what makes the discipline survive contributor turnover. It
belongs with the existing ArchUnit rules.

## Startup behaviour and operator experience

| Situation | Behaviour |
|---|---|
| Stored version equals code version | normal start |
| Stored version lower, migration path declared | run steps under lock, log what was carried over and what was dropped, start |
| Stored version lower, no path declared | refuse to start with an actionable message naming the versions, following `DatabaseMigration` |
| Stored version higher (rollback) | refuse to start, pointing at the roll-forward rule; note the old namespace no longer exists |
| No stored version (fresh store) | write the current version, start |

A build agent that reaches Redis before the core node has migrated must not write into the new namespace prematurely.
It reads the same version key and waits, rather than registering. This is the failure #12137 describes in its final
paragraph.

## Testing

- Extend `AbstractDistributedDataTest` so all three backends agree on version handling.
- A migration test that seeds an old namespace, runs the step, and asserts the carried-over structures moved and the
  discarded ones did not.
- A rollback test asserting a higher stored version refuses to start.
- A crash-recovery test that interrupts a drain and re-runs it, asserting no entry is lost or duplicated.
- The fingerprint test itself, with a deliberate DTO change proving it fails.

## Rollout

1. Add the version constant, the key prefix and the fingerprint test, with the version at its initial value. No
   behaviour change for existing deployments beyond a one-time namespace move.
2. Add the migration runner and the carry-over declarations.
3. Document the rule in `distributed-data.mdx`: what to do when changing a distributed DTO.

## Recommended separately

Move queue, set and topic elements off raw Kryo, or make `BuildJobQueueItem` version-tolerant. Today the structure most
worth preserving has the weakest evolution guarantees, and the namespacing above protects it across versions without
making the encoding itself any safer within one.

## Noted while classifying

`FeatureToggleService` deliberately keeps feature toggles out of the database: the defaults come from yml and are
seeded at startup. The distributed map is therefore the only place a runtime change made through
`AdminFeatureToggleResource` exists. Carrying `features` over preserves those runtime changes across a version bump
without giving the toggles database backing, which is the intended arrangement rather than a gap to close.

## Decisions

- Carry-over drains entries rather than copying them, so peak memory stays flat.
- The old namespace is deleted as soon as the migration completes, with no grace TTL. Rollback therefore does not
  recover the previous release's distributed data, consistent with the existing forward-only rule.

## Open questions

None outstanding.
