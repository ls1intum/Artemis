# Build agent repository access hardening — follow-up plan

Follow-up to [#13503](https://github.com/ls1intum/Artemis/pull/13503), which made
`artemis.version-control.build-agent-use-ssh` govern the core-node side and deprecated HTTPS basic
authentication for build agents.

Goal: stop authorising build agents for *every repository, forever, from anywhere*, and replace that with
three narrowing constraints — **where** the caller is, **which** repositories the current job needs, and
**how long** the credential lives.

---

## 1. Where we are today

Two mechanisms authenticate "a build agent"; both then authorise **read access to every repository in the
installation**.

| | Entry point | Check | What is skipped |
|---|---|---|---|
| HTTPS basic | `LocalVCServletService.authenticateAndAuthorizeGitRequest` (`:279-292`) | shared `build-agent-git-username`/`-password` | rate limit, repository authorization, VCS access log |
| SSH key | `GitPublickeyAuthenticatorService.authenticateBuildAgent` (`:143-157`) → `SshGitLocationResolverService` (`:77`) | any key matching *any* entry in the `buildAgentInformation` map | repository authorization, VCS access log |

Neither looks at the source address, neither looks at which repositories the agent actually has a job for,
and the SSH key never expires.

### Infrastructure we can build on

- **Generic distributed adapter** — `DistributedDataProvider` (`localci/service/distributed/api/`) with
  Hazelcast, Redisson and local implementations, plus the `DistributedDataAccessService` facade. Already has
  `getConnectedClientNames()`, connection and client-disconnection listeners, and per-key `lock`/`unlock`.
- **`buildAgentInformation` map** — agent short name → `BuildAgentInformation`, written by
  `BuildAgentInformationService` on the agent, read by core nodes; already carries `publicSshKey`.
- **`processingJobs` map** — build job id → `BuildJobQueueItem`, written by the agent the moment it claims a
  job (`SharedQueueProcessingService.addToProcessingJobs`, `:671`) and removed when the job ends.
  `BuildJobQueueItem.repositoryInfo()` already lists assignment, test, solution and auxiliary repository URIs.
- **`inet.ipaddr`** is already a dependency and already used on both git paths for rate limiting
  (`LocalVCServletService:298`, `GitPublickeyAuthenticatorService:92`).
- **`VcsAccessLogService`** for attribution, `AuthenticationMechanism` for labelling.
- Admin UI for agents under `src/main/webapp/app/localci/build-agent-{summary,details}`.

---

## 2. Constraints found while reading the code

These shape the design; several are the reason a naive implementation would be wrong or ineffective.

1. **`X-Forwarded-For` is trusted unconditionally.** `HttpRequestUtils.getIpStringFromRequest` returns the
   first XFF value if the header is present (`:32-41`). Any client can set it, so it must **not** be the basis
   of an allowlist. We need the TCP peer (`request.getRemoteAddr()`) and may consult XFF only when the peer is
   a configured trusted proxy. (The rate limiter uses the unsafe helper today — pre-existing, out of scope here,
   worth a separate issue.)
2. **SSH runs behind an nginx `stream { proxy_pass }`.** `docker/nginx/nginx.conf:44-52` and the nginx snippet
   documented in `security.mdx` forward port 7921 without PROXY protocol, so the MINA SSHD listener sees the
   load balancer, not the agent. An SSH IP check is therefore inert on any deployment where agents clone
   through the LB. Options: agents connect directly to a node's 7921; or `proxy_protocol on` plus custom PROXY
   support on the MINA SSHD listener (not built in, real work); or accept and document the gap.
   **This is the biggest practical caveat of the IP idea.**
3. **NAT and containers.** Several agents on one host share one source address. The registry is
   IP → set of agents, and the allowlist is a list of *hosts*, not of agents.
4. **A self-reported address is worthless.** `BuildAgentDTO.memberAddress` is what the agent believes its own
   socket to be (`HazelcastDistributedDataProviderService.getLocalMemberAddress`, `:196-225`) — pre-NAT and
   forgeable by a hostile agent. The authoritative value is the *observed* remote address of the agent's
   cluster connection: Hazelcast `ClientService.getConnectedClients()` → `Client.getSocketAddress()`, Redis
   `CLIENT LIST` → `RedisClientInfo.getAddressPort()`.
5. **`BuildAgentInformation` and `BuildJobQueueItem` are cross-node shared structures.** Both carry the NOTE
   that changing them requires migrating or clearing the distributed structures and a release note. Adding a
   *new* map avoids touching them; the per-job token (PR 3) does have to add a field to `BuildJobQueueItem`.
   There is already a "clear distributed data" admin action.
6. **Hazelcast OSS has no per-client permissions.** Any node that joins the cluster can read every map,
   including the build job queue and therefore every queued job's token. Per-job tokens consequently do **not**
   defend against a hostile cluster member — that is what the cluster password/TLS and the PR 2 allowlist are
   for. They defend against a leaked credential, a compromised build host, and they give attribution.
   Binding the token to the claiming agent (job must be in `processingJobs`, source IP must be that agent's)
   makes a stolen token useless without also claiming the job, which is destructive and visible.
7. **Jenkins-LocalVC uses the same credential pair and is not an Artemis build agent.** No key, no cluster
   membership, no entry in `processingJobs`. Per-job tokens cannot apply to it; the IP allowlist can, if the
   Jenkins host is listed.
8. **Token validity cannot start at enqueue.** Queue wait is unbounded during exam peaks. Derive the window
   from `jobTimingInfo.buildStartDate()`, which the agent sets when it claims the job, bounded by
   `artemis.continuous-integration.build-timeout-seconds.max` (240 s default) plus a grace period. A retry
   creates a new claim, so the window restarts by itself.
9. **`DistributedMap` has no TTL-aware `put`.** Store `expiresAt` inside the value and validate on read
   (provider-independent, and the local provider has no native TTL); add a Hazelcast `MapConfig` TTL purely as
   garbage collection. Note `HazelcastConfiguration:1366`: a `MapConfig` must be registered before the proxy is
   created or it is silently ignored.
10. **An empty allowlist must mean "no restriction", never "deny all"**, or every existing installation breaks
    on upgrade.

---

## 3. Proposed work — three PRs

Strictly, PR 3 does not depend on PR 1 and PR 2. But the IP binding is what makes a per-job token meaningfully
better than a shared secret, so this order is the right one. If a single PR is preferred, the three phases
become three commits.

Every enforcement point gets the same three-state switch, defaulting to `warn` in the release that introduces
it and flipped to `enforce` in a later one:

```yaml
artemis:
    continuous-integration:
        build-agent-network:
            allowed-ranges: []        # CIDRs or single addresses; empty = no restriction
            trusted-proxies: []       # CIDRs whose X-Forwarded-For may be believed
            enforcement: warn         # off | warn | enforce
```

### PR 1 — Build agents register their network address dynamically

**Goal:** every core node can answer "which source addresses do the currently registered build agents come
from?" from the distributed middleware, without a provider-specific call on the git hot path.

Server:

- `DistributedDataProvider`: add `Map<String, Set<String>> getConnectedClientAddresses()`, a sibling of the
  existing `getConnectedClientNames()`.
  - Hazelcast: `getClientService().getConnectedClients()` → name + `getSocketAddress()`; empty on a client.
  - Redisson: extend `RedisClientListResolver` to also return `RedisClientInfo.getAddressPort()` per name.
  - Local: empty map.
- `DistributedDataAccessService`: expose it and lazily create a new map `buildAgentAddresses`.
- New record `buildagent/dto/BuildAgentAddressInfo(String agentName, Set<String> addresses, ZonedDateTime observedAt, boolean allowed)`.
  A *new* record, so no migration of existing shared structures.
- New `BuildAgentAddressRegistryService` (`@Profile(PROFILE_LOCALCI)`, `@Lazy(false)`): reconciles observed
  addresses into the map on the client-connected event and on a `@Scheduled` refresh (~30 s); removes entries
  for agents that are gone, using the existing `addClientDisconnectionListener`. Writes are idempotent and
  guarded by `DistributedMap.lock(agentName)`, so several core nodes reconciling concurrently is harmless.
- Fast lookup for the hot path: a volatile local snapshot refreshed from the map via an entry listener, not a
  distributed read per git request.

No enforcement in this PR — observation and logging only.

Client (small): show the observed addresses on the build agent details page
(`build-agent-details.component`, `build-agent-information.model.ts`, plus the admin DTO).

Tests: provider-level tests for the new method (Hazelcast + local, under `localci/service/distributed/`);
registry unit tests with a mocked provider (connect → appears, disconnect → removed, two agents behind one IP);
DTO coverage in `LocalCIResourceIntegrationTest`.

### PR 2 — Optional admin-defined allowlist of build agent networks

- Parse `allowed-ranges` to `List<IPAddress>` once at startup with `inet.ipaddr`; fail startup on an unparsable
  entry, via a config validator in the style of `LocalVCBuildAgentCredentialsValidator`.
- New `HttpRequestUtils.getPeerIpAddress(request, trustedProxies)`: `getRemoteAddr()`, and only when that is
  inside `trusted-proxies` walk `X-Forwarded-For` right to left. Do **not** reuse `getIpStringFromRequest`.
- Enforcement points:
  1. **Registration** — `BuildAgentAddressRegistryService` marks an agent outside the allowlist as not allowed.
     This cannot stop it joining the cluster (that is the cluster password's job) but it stops it cloning.
  2. **HTTPS clone** — in `LocalVCServletService`, before the build-agent shortcut returns: peer must be inside
     `allowed-ranges` when configured, *and* be a registered build agent address from PR 1.
  3. **SSH clone** — the same check in `GitPublickeyAuthenticatorService.authenticateBuildAgent` using
     `session.getRemoteAddress()`.
- Startup diagnostics: log the effective mode and parsed ranges, and warn explicitly when `trusted-proxies` is
  non-empty that the SSH check will see the proxy rather than the agent (constraint 2).
- Docs: extend *Build Agent Authentication* in `security.mdx` with the nginx caveat and the
  `warn` → read logs → `enforce` rollout.

Tests: range parsing and matching (IPv4, IPv6, CIDR, single address, empty list); allowed vs disallowed peer in
`LocalVCIntegrationTest`/`LocalVCFetchAndPushIntegrationTest` and
`LocalVCSshIntegrationTest`/`BuildAgentSshAuthenticationIntegrationTest`; `warn` vs `enforce`; config validator test.

### PR 3 — Per-build-job, short-lived clone credentials

**HTTPS (the non-SSH case you described):**

- Mint at enqueue in `LocalCITriggerService.triggerBuild` (~`:240`): `SecureRandom`, same shape and length as
  the existing VCS access tokens (`TOKEN_PREFIX`, `VCS_ACCESS_TOKEN_LENGTH` in
  `LocalVCPersonalAccessTokenManagementService`) so the existing constant-time comparison and length guards apply.
- Store core-side in a new distributed map `buildJobCloneTokens`:
  `buildJobId -> BuildJobCloneToken(String tokenHash, Set<String> repositoryUris, ZonedDateTime issuedAt, ZonedDateTime expiresAt)`.
  SHA-256 hash only, so the map never holds the plaintext. `repositoryUris` = assignment + test + solution +
  auxiliary from `RepositoryInfo`, normalised the way `LocalVCRepositoryUri` normalises.
- Ship the plaintext to the agent as a new nullable field on `BuildJobQueueItem`. **This is the shared-structure
  change** — release note plus clear/migrate distributed data (constraint 5).
- Agent side: `BuildJobGitService.authenticate` uses `UsernamePasswordCredentialsProvider(buildJobId, token)`
  when the job carries a token, otherwise the configured pair. `BuildJobExecutionService.cloneRepository`
  (`:622-629`) already has `buildJob.id()` at the call site; thread the job (or a small credential record)
  through instead of only the id.
- Core-side validation, replacing the shortcut in `LocalVCServletService`:
  1. constant-time compare SHA-256 of the presented token against the stored hash,
  2. `repositoryAction == READ`,
  3. requested repository URI is in the job's `repositoryUris`,
  4. the job is currently in `processingJobs` (claimed and running),
  5. `now < buildStartDate + max build timeout + grace`,
  6. with PR 1/2 in place, the peer address is the claiming agent's registered address.

  Then allow — and **write a VCS access log entry** with a new `AuthenticationMechanism.BUILD_JOB_TOKEN`, which
  the current shortcut does not do.
- Revocation: delete the entry when the job finishes or is cancelled
  (`SharedQueueProcessingService`/`SharedQueueManagementService` already remove from `processingJobs` at every
  such point), plus a scheduled sweep and a Hazelcast `MapConfig` TTL as a backstop.

**SSH counterpart, token-free:** `SshGitLocationResolverService:77` currently means "build agent and READ →
anything". Replace with "the requested repository is in the union of `repositoryInfo` URIs of the jobs this
agent currently holds" (`DistributedDataAccessService.getProcessingJobsForAgentByName`). The agent identity is
already available in `authenticateBuildAgent` (`matchingAgent`); put it on the session as a new `SshConstants`
attribute. Same blast-radius reduction as the token, no token needed. *(Decision 4 below — natural completion,
but beyond the literal ask.)*

**Honest limits, to state in the PR description:**

- No defence against a hostile node that has already joined the cluster: it can read the queue (constraint 6).
- Jenkins-LocalVC keeps the shared credential pair; per-job tokens do not apply to it.
- The token never enters the build container — the agent clones on the host and copies the working tree in — so
  student build scripts cannot read it.

Tests: minting, expiry and scope unit tests; end-to-end in `LocalVCLocalCIIntegrationTest` (queued job → agent
clones with the job token → the same token against an unrelated repository is rejected → after the job ends the
token is rejected); `BuildJobGitServiceTest` for the agent-side credential selection; access-log assertions.

---

## 4. Decisions I need from you

| # | Question | My recommendation |
|---|---|---|
| 1 | Three PRs or one? | Three — each is independently reviewable and independently deployable. |
| 2 | Allowlist as an Ansible-owned config property, or admin-editable in the UI and DB? | Config property, shown read-only in the admin UI. The allowlist is what stops a rogue agent registering, so it should be owned by whoever owns the hosts, not by an account a phished admin controls. |
| 3 | `enforcement: warn` first, `enforce` in a later release? | Yes. TUM prod has agents we do not want to discover the hard way. |
| 4 | Should PR 3 also scope SSH reads to the agent's current jobs? | Yes — same benefit, no token, and it keeps the two mechanisms from diverging again. |
| 5 | SSH behind nginx (constraint 2): document that agents must connect directly for the IP check to work, or invest in PROXY protocol support on the MINA SSHD listener? | Document first, measure how many installations are affected, and only then decide on PROXY protocol. |
