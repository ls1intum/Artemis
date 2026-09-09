<!--
Review copy. Not intended to be committed: this is an internal operations runbook and
carries staging1 topology, Vault paths and proxy internals that do not belong in the
public Artemis documentation. Only step 4's *results* are meant to land in the repo, in
documentation/docs/admin/benchmarking-tool.mdx.
-->

# Staging1 benchmark runbook

Deploy `develop` to staging1, run the benchmark ladder from
`artemis-performance-test0`, watch staging1 while it runs, and publish the numbers.

**Status: not executed.** Everything below is prepared and verified against the
repositories; nothing has been run against staging1. The two findings that block a
usable number now have fixes open in review (see "Blockers").

---

## 0. What is already verified

Read out of `~/Projects/artemis-ansible`, `~/Projects/artemis-ansible-collection`,
`~/Projects/Artemis` and `~/Projects/Artemis-Benchmarking`, not assumed.

| Fact | Value | Source |
| --- | --- | --- |
| staging1 core nodes | `node[1:3].staging1.artemis.cit.tum.de` | `hosts:163` |
| staging1 build agents | `agent0[1:3].staging1.artemis.cit.tum.de` | `hosts:166` |
| `artemis_staging1_nodes` | core **+ agents** = 6 hosts | `hosts:169` |
| Supporting hosts | `db`, `broker`, `registry`, `storage`, `proxy` `.staging1…` | `hosts:173-186` |
| Database | MySQL, `innodb_buffer_pool_size 10G`, `max_connections 2000` | `group_vars/artemis_prod_like_mysql.yml` |
| CI/VC | Integrated Code Lifecycle (`localci` + `localvc`), not Jenkins | `group_vars/artemis_staging1.yml` |
| **Build capacity** | `concurrent_build_size: 2` on each of 3 agents = **6 concurrent builds** | `group_vars/artemis_staging1.yml:36` |
| Multi-node | `is_multinode_install: true`, ActiveMQ 2.40.0 broker + Eureka registry | `artemis_prod_like_common.yml` |
| JVM heap | `-Xmx` = 80 % of host RAM (`artemis_system_ram_proportion: 0.8`) | `roles/artemis/templates/artemis.service.j2:17` |
| Working dir | `/opt/artemis` | `roles/artemis/defaults/main.yml:62` |
| Admin account | `artemis_admin`, password in Vault `kv/data/artemis/test/staging1` → `internal_admin` | `artemis_prod_like_common.yml` |
| Benchmark host | `artemis-performance-test0` → **131.159.89.112** / `2a09:80c0:89::112` | `dig` |
| Git rate-limit exemption | `131.159.89.112` already exempt, comment "Benchmark server" | `group_vars/artemis_staging1.yml:78` |
| Tool's staging slot | `STAGING` → `https://staging1.artemis.cit.tum.de/`, `is-local: true`, `cleanup-enabled: false` | `Artemis-Benchmarking application.yml` |
| Artemis version on develop | `10.0` | `build.gradle:96` |
| Stale default | `group_vars/artemis_staging1.yml:5` still says `artemis_version: 7.10.5`. The playbook unsets it, so it is inert, but do not rely on it | `artemis_staging1.yml` |

---

## Status: ready to run

Verified end to end against staging1 on 2026-08-21 with a **20-student smoke run**, from the deployed
tool at `artemis-performance-test0`:

| | |
| --- | --- |
| Tool version | 0.4.4, commit `bac3312`, deployed and live |
| Passkey | Registered for `artemis_admin`, auto-approved, counter advancing |
| Rate limiting | **0** throttled requests during the run |
| Run | FINISHED, no errors, all 20 students in every category |
| Builds | 33 submitted, **all successful**, 14.1 s each, 14.5 per minute, queue drained |

Reference figures from that run, 20 students with 1 to 2 pushes, for comparison as the ladder scales:

| Action | n | avg |
| --- | --- | --- |
| Authentication | 20 | 363 ms |
| Get student exam | 20 | 27 ms |
| Start student exam | 20 | 60 ms |
| Submit exercise | 60 | 84 ms |
| Submit student exam | 20 | 30 ms |
| Clone | 20 | 869 ms |
| Push | 29 | 610 ms |
| Total | 709 | 81 ms |

B1, B2 and B4 are closed. What remains before the campaign is operational: warm the build images
(2.2.0), decide the maintenance window, and add tool rows for students beyond 500 (P3).

---

## Blockers to resolve before step 2

### B1 (fix open in review) The login rate limit throttles the benchmark to ~4 students

`nginx_proxy.conf.j2` rate-limits the exact endpoint the tool authenticates against:

```
limit_req_zone $binary_remote_addr zone=loginlimit:10m rate=30r/m;   # line 38
location /api/core/public/authenticate { limit_req zone=loginlimit burst=3 delay=2; }
```

The git zones are keyed by `$rate_limit_key`, so the benchmark IP escapes them. `loginlimit` was
keyed by `$binary_remote_addr` with no exemption path. Of ~100 near-simultaneous logins roughly 4
are admitted; the rest get 429, and `login()` uses `retrieve()`, so each 429 throws and that
student is skipped while the run still reports the configured count.

**Fix: artemis-ansible-collection#239** keys `loginlimit` off `$rate_limit_key` the way the git
zones already are. Rendered both ways: an environment with an empty exempt list (production,
staging2) is byte-identical to today. Account recovery stays limited for everyone. Merge it, then
run `playbooks/artemis-staging1/proxy.yml`.

**The tool's login cache is a real mitigation, but not a substitute.** With a valid cached token
`login()` returns early and only calls `api/core/public/account`, which is under `location /` and
not rate limited, so a warm-cache run never touches the limiter at all. Three things it does not
solve:

1. The cache has to be filled once, and that first pass over N fresh users is fully exposed.
2. A cached run records **zero** `AUTHENTICATION` stats, because `login()` returns an empty stat
   list. Login cost is then never measured, which makes "authentication degrades first" (one of
   the four bottleneck readings in the docs) impossible to observe.
3. If a cached token is rejected, `checkAccess()` fails and the student falls back to
   `authenticate`, re-entering the limiter for exactly the users whose runs are already unusual.

So: use the exemption to make runs possible and login measurable; the cache is what keeps repeat
runs cheap.

### B2 (fix open in review) Only the IPv4 address was exempt from the git rate limits

`proxy_rate_limit_exempt_ips` listed `131.159.89.112`. `artemis-performance-test0` is also
`2a09:80c0:89::112`, and nginx matches the exemption against the address it actually sees, so an
IPv6 run fell back to `gitlimit` (5 r/s) and would have shown up as clone/push latency that looks
like a storage problem.

**Fix: artemis-ansible#93** lists both families. Depends on #239. Confirm afterwards from the
proxy that `grep -c ' 429 ' /var/log/nginx/access.log` stays flat during a run.

### B4 (blocking for admin mode) Passkey is required for administrator features

Verified against staging1 on 2026-08-21: `artemis_admin` authenticates with its stored password
(HTTP 200) but **every** admin endpoint answers

```
403 error.passkeyAuth.notAuthenticatedWithPasskey
```

`group_vars/artemis_prod_like_core.yml` sets `artemis_passkey_require_for_administrator_features:
true`. `@EnforceAdmin` is `hasRole('ADMIN') and @passkeyAuthenticationService
.isAuthenticatedWithSuperAdminApprovedPasskey()`, so an admin call needs two things in the JWT:
`authenticationMethod == PASSKEY` and `is-passkey-super-admin-approved == true`. There is no
per-request WebAuthn ceremony; the gate is a **claim check on the token**.

This rules out `CREATE_COURSE_AND_EXAM`, which the plan was originally written around. It also
explains the run history: every large staging1 run used `EXISTING_COURSE_CREATE_EXAM`, and the one
admin-mode simulation last ran 2026-02-01.

`artemis_admin` also has **no passkey credential at all** on staging1, so it can never pass the gate
as things stand. The four registered credentials belong to human accounts; two are approved
(`ne23kow`, `ge28dih`), two are not.

**Option C (recommended): avoid admin entirely.** Switch the ladder to
`EXISTING_COURSE_CREATE_EXAM`. Everything the tool does per run is instructor-level, confirmed in the
source:

| Call | Annotation |
| --- | --- |
| `POST courses/{courseId}/exams` | `@EnforceAtLeastInstructor` |
| `POST .../generate-student-exams` | `@EnforceAtLeastInstructor` |
| `POST text-exercises` | `@EnforceAtLeastInstructor` |
| `POST modeling-exercises` | `@EnforceAtLeastEditor` |

Only `api/admin/courses` (create/delete) and `api/admin/cancel-all-*-jobs` are admin-gated, and
`CREATE_COURSE_AND_EXAM` is the only mode that needs them. Cleanup is already off for STAGING, so the
admin delete is never called either.

One-time manual setup, by someone with an approved passkey: create a course on staging1 and enrol
`artemis_test_user_1..2000`. Then store an **instructor** credential for that course in the tool. The
same course is reused for every rung, which also improves comparability.

**Option A (fallback): inject a passkey-issued JWT.** Because the gate is a claim check, a token
minted by a real passkey login satisfies it, and staging1 does not override
`token-validity-in-seconds-for-passkey`, so the shipped default of **180 days** applies. The tool
already skips `authenticate` whenever `artemis_user.jwt_token` is set and unexpired, so pasting such
a token in makes admin mode work unchanged. Caveats: the tool exposes `jwtToken` only on the entity,
not in its REST DTOs or client, so today this means writing the row directly in the tool's
PostgreSQL, or adding a small field to the tool; the token belongs to a human super-admin rather than
`artemis_admin`; and it is a long-lived bearer credential sitting in a plaintext database.

**Option B (chosen, implemented, verified): give the tool its own software authenticator.**
Artemis-Benchmarking#829. See B4.1.

**Option D (not recommended): set `require-for-administrator-features: false`** on staging1 via
`nodes-update-config.yml`. It makes a prod-like box diverge from the production security posture for
the sake of a test run.

### B4.1 Option B spec: a software authenticator in the benchmarking tool

Every parameter below was read off staging1, not assumed.

**Why the tool must register its own credential.** A passkey registered in a browser is useless here:
the private key never leaves the platform authenticator, so the tool could never sign an assertion.
The tool has to generate the keypair itself. That is possible because **registration only needs an
ordinary session**: with `artemis_admin` logged in by password, `POST /webauthn/register/options`
returns 200. Only the *approval* is manual.

**Parameters from staging1**

| | Value |
| --- | --- |
| `rp` | `{name: "Artemis", id: "staging1.artemis.cit.tum.de"}` |
| `user.id` (user handle) | `"MQ"` for `artemis_admin`, base64url |
| `pubKeyCredParams` | `-8` Ed25519, `-7` ES256, `-257` RS256. Use **ES256**: plain JCA, `secp256r1` + `SHA256withECDSA` |
| `authenticatorSelection` | `{residentKey: "required", userVerification: "preferred"}` |
| `attestation` | **`none`** |
| auth options | `{allowCredentials: [], challenge: <43-char b64url>, rpId: "staging1…", timeout: 300000, userVerification: "preferred"}` |

Two consequences worth noting: `attestation: none` means there is no attestation statement to
produce, and `userVerification: preferred` means the UV flag is optional rather than mandatory. Both
remove the fiddly parts. `residentKey: required` means the assertion must carry `userHandle`.

**Phase 1, once: register.** Password-login as the benchmark admin, `POST
/webauthn/register/options`, generate an EC P-256 keypair, build the attestation object, `POST
/webauthn/register`. Persist credential id, PKCS8 private key, user handle and a signature counter.
This is the only part needing CBOR, for the attestation object and the COSE-encoded public key:
`{fmt: "none", attStmt: {}, authData: …}`. Either add a small CBOR dependency or hand-roll the two
fixed shapes.

**Phase 2, usually nothing.** `@EnforceAdmin` wants an *approved* credential, and Artemis grants that
during registration when the account holds `ROLE_SUPER_ADMIN`
(`ArtemisUserCredentialRepository`). staging1's `artemis_admin` is `ROLE_SUPER_ADMIN`, so **no manual
approval step is needed there**. Only a merely `ROLE_ADMIN` account needs a super admin to approve
the credential once.

**Phase 3, every run: assert.** This is the part that matters, and it needs **no CBOR at all**.

1. `POST /webauthn/authenticate/options` to get the challenge.
2. `clientDataJSON` = `{"type":"webauthn.get","challenge":"<b64url challenge>","origin":"https://staging1.artemis.cit.tum.de","crossOrigin":false}`
3. `authenticatorData` = `SHA256(rpId)` (32 bytes) `|| flags` (1 byte, `0x01` UP, `0x05` with UV) `|| signCount` (4 bytes big-endian). No attested credential data on an assertion.
4. `signature` = `SHA256withECDSA` over `authenticatorData || SHA256(clientDataJSON)`.
5. `POST /login/webauthn` with exactly the shape the Artemis client sends
   (`SerializableLoginCredential`):

```json
{
  "type": "public-key",
  "id": "<b64url credentialId>",
  "rawId": "<b64url credentialId>",
  "authenticatorAttachment": "platform",
  "clientExtensionResults": {},
  "response": {
    "clientDataJSON": "<b64url>",
    "authenticatorData": "<b64url>",
    "signature": "<b64url>",
    "userHandle": "<b64url>"
  }
}
```

`ArtemisWebAuthnAuthenticationFilter` extends Spring Security's `WebAuthnAuthenticationFilter`, so
that shape is the standard one, and its success handler sets the JWT cookie through
`JWTCookieService`. The tool's existing `Set-Cookie` parsing in `SimulatedArtemisUser.login()`
therefore works unchanged: only the endpoint and body differ.

**Implementation risks**

- **Signature counter.** `passkey_credential.signature_count` is stored, and webauthn4j rejects a
  counter that fails to advance once the stored value is above zero. Persist it in the tool and
  increment on every assertion.
- **Origin and rpId must match exactly.** `origin` has to be `https://staging1.artemis.cit.tum.de`
  and the rpIdHash `SHA256("staging1.artemis.cit.tum.de")`, so the tool must authenticate **through
  the load balancer**, never against a node address.
- **Application-level rate limit.** `ArtemisWebAuthnAuthenticationProvider` calls
  `rateLimitService.enforcePerMinute(..., RateLimitType.AUTHENTICATION)`, which is `30` per minute.
  Irrelevant at one admin login per run, but it rules out passkey auth for the student population.
- Students keep using passwords. Only the admin or instructor identity needs this.

**Worth knowing:** a passkey JWT is valid for **180 days** on staging1 (the shipped
`token-validity-in-seconds-for-passkey: 15552000`, not overridden). So phase 3 is what makes the tool
self-sufficient, but a single assertion would in principle carry a whole benchmark campaign.

### B3 (expect it, do not fix it) Six concurrent builds

3 agents * `concurrent_build_size: 2`. A 2000-student run pushes several
thousand commits; the queue will be long and "builds per minute" will plateau.
That is the honest capacity answer for this hardware, not a defect, so record it
rather than tuning it away. It also means the CI status is still draining long
after the simulation reports "finished".

### B4.2 Verified end to end locally, and what that changed

Run on 2026-08-21 against `./run-e2e-tests-local-multinode-fast.sh` with
`ARTEMIS_USERMANAGEMENT_PASSKEY_REQUIREFORADMINISTRATORFEATURES=true`, which reproduces the staging1
condition exactly: a password login returns 200 and every admin endpoint returns
`403 error.passkeyAuth.notAuthenticatedWithPasskey`.

The full cycle works: register a passkey, authenticate with it, create the course, exam, exercise
groups and all four exercises, register and prepare students, run the exam. **Five consecutive
four-student runs finished clean**, 136 requests each with every category populated, no 4xx or 5xx at
the load balancer. Three forced passkey logins advanced the signature counter 2 to 5, with Artemis
agreeing each time.

Course entry uses the per-tab endpoints: `available-tabs`, `for-overview` and
`exercises-for-overview` once per student, and the deprecated `courses/{courseId}/for-dashboard`
never. The hits on `courses/for-dashboard` are the plural course-list endpoint, which is not
deprecated.

**Findings that change how the staging1 run is set up:**

| Finding | Effect on this plan |
| --- | --- |
| **Users created through the tool had no password.** The payload never set `internal`, and Artemis only stores a hash for internal users, so every account came out externally managed with none. Students were skipped one at a time and the run reported FINISHED with **zero requests in every category**. | Fixed in #829. It also means: **verify a sample login before every campaign.** A green run that measured nothing is the worst failure mode a benchmark has, and nothing in the report says so. staging1's existing 2000 accounts are internal and were confirmed to authenticate, so they are unaffected. |
| The WebAuthn origin must carry a non-default port, and must be the load-balancer URL. | The tool must point at `https://staging1.artemis.cit.tum.de`, never a node address. Already the case in P2. |
| Text exercise creation now takes a flat `exerciseGroupId`; modeling and file upload still take a nested `exerciseGroup`. | Fixed in #829. Worth re-running the smoke test after any Artemis upgrade, since this is exactly the kind of drift that only shows up against a live server. |
| Artemis auto-approves a passkey registered by a `ROLE_SUPER_ADMIN` account. | No manual approval step on staging1 (see B4.1 phase 2). |

**Reproducing the smoke test** (worth doing before each campaign, and after any Artemis upgrade):

```bash
# 1. Artemis: 3 nodes + nginx LB, with the staging1 passkey condition
cd ~/Projects/Artemis
export ARTEMIS_USERMANAGEMENT_PASSKEY_REQUIREFORADMINISTRATORFEATURES=true
export ARTEMIS_USERMANAGEMENT_PASSKEY_ADDITIONALALLOWEDORIGINS="http://localhost:54321,https://localhost"
./run-e2e-tests-local-multinode-fast.sh --skip-build --filter "NoSuchTestZZZ"   # stack stays up

# 2. The tool, on ports that do not collide with the Artemis stack
docker run -d --name benchmarking-postgres-smoke -e POSTGRES_DB=benchmarking \
  -e POSTGRES_USER=benchmarking -e POSTGRES_PASSWORD=benchmarking \
  -p 127.0.0.1:5433:5432 postgres:18-alpine
cd ~/Projects/Artemis-Benchmarking
# src/main/resources/config/application-local.yml (gitignored): server.port 9500,
# datasource on 5433, artemis.local.url http://localhost:54321/
./gradlew bootRun -x webapp --args='--spring.profiles.active=dev,api-docs,local'
```

Then: store the admin, `POST /api/artemis-users/{id}/passkey`, create students with
*Create users on Artemis*, **log one of them in to check the password took**, and run a four-student
`CREATE_COURSE_AND_EXAM`. Two notes that cost time: `bootRun` passes
`--spring.profiles.active` itself, so the `local` profile has to come through `--args`, not the
environment; and the tool's run list is not ordered by id, so take the max rather than the last
element when reading a result.

---

### B5 (resolved) node2 and node3 were wedged after the NFS outage

`nfs-server` on storage was started 2026-08-21 00:14:58 and node1 restarted at 00:17:42, but node2
and node3 kept the JVMs wedged since 2026-08-20 20:59:18 and listened on nothing, so nginx
round-robined between a working node and two dead upstreams and the site alternated 200 with the 503
maintenance page.

Restarted both on 2026-08-21 after confirming `/mnt/storage` responded on each. All three nodes now
serve, and 10 of 10 requests through the load balancer return 200.

**Still open:** the underlying boot race. The export is IPv6-only (`/srv/artemis fcfe::/96`) and
`nfs-server` is ordered only `After=network-online.target`, which does not wait for WireGuard, so
nfsd loses the race and exits at every boot. It has already happened three times. The fix is a
systemd drop-in ordering it after `wg-quick@wg0.service`.

---

## Prerequisites

Things only you can supply or decide:

| # | Item | Notes |
| --- | --- | --- |
| P1 | **Password for the tool's `admin` account** | Account chosen. The documented default `admin`/`admin` returns **401** against `POST /api/authenticate` on the VM, so it has been changed. SSH gives me the database but the hash is bcrypt, so the password itself is still needed. |
| P2 | **Restart Artemis on node2 and node3** | staging1 is only two-thirds up. node1 was restarted 00:17:42 after `nfs-server` came back at 00:14:58; node2 and node3 still run the JVMs wedged since 20:59:18 and listen on nothing, so nginx alternates 200 and the 503 maintenance page. See B5. |
| P3 | **Add tool rows for students 501..2000** | staging1 already has `artemis_test_user_1..2000`, all activated, all internal, verified 2026-08-21; `artemis_test_user_1` and `_500` both authenticate. The tool holds only 500 of them. The stored passwords are exactly `<23-char fixed prefix>` + N (one distinct prefix, no suffix, checked across all 500), so add-by-pattern reproduces them. **Do not tick *Create users on Artemis***: the accounts exist, and that path needs admin, which B4 blocks. |
| P4 | ~~Which WAR~~ **Done** | Latest develop: run `32382103630`, SHA `0a23256e02`, `Artemis-10.0.war`, sha256 `5b09f42d8f…`. `Build .war artifact` succeeded (the run's overall red is the develop-wide E2E gate). Verified locally: 9266 entries, client `index.html` and one `main-*.js` bundle present. |
| P5 | Merge and deploy the B1/B2 fixes | artemis-ansible-collection#239 then artemis-ansible#93, then run `playbooks/artemis-staging1/proxy.yml`. |
| P6 | Maintenance window | The version update is a **full outage**, not a rolling restart (see step 1). Budget the deploy plus the ladder plus build drain. |
| P7 | Does staging1 need `nodes-update-config.yml` first? | Only you know whether develop added config keys staging1 lacks. |
| P11 | **A prepared course, if going with B4 Option C** | Someone with an approved passkey creates one course on staging1 and enrols `artemis_test_user_1..2000`, once. Then store an instructor credential for it in the tool. |
| P8 | ~~Prometheus~~ **Not available** | The auth token is not known, so `artemis.staging.prometheus-instances.*` stays empty and the tool will report no server-side workload data. Step 3 falls back to SSH sampling, see 3.1. Not a blocker. |
| P9 | Confirm `cleanup-enabled: false` stays false | Deleting a large course is itself heavy load and would corrupt the next rung's baseline. Also required for the CI status to be fetchable. |

**No longer needed, verified rather than assumed:**

| Was listed as | Finding |
| --- | --- |
| Vault access | **Not required for a version update.** The restart-only path resolves just `artemis_version`, `artemis_working_directory`, `artemis_server_url`, `artemis_user_name`, `artemis_user_group` and `artemis_deployment_user_name`. None is a `hashi_vault` lookup, and `artemis_llm_cost` (whose assert would pull one) is defined only for `artemistests_hyperion`, not staging1. Ansible resolves group_vars lazily, so the many vault lookups in `artemis_staging1.yml` and `artemis_prod_like_common.yml` are never evaluated. Vault is only needed for `nodes.yml` / `nodes-update-config.yml`. |
| SSH to the six staging1 hosts | Present in `~/.ssh/config` as `staging1node1..3`, `staging1agent01..03`, plus `staging1db`, `staging1proxy`, `staging1broker`, `staging1registry`, `staging1storage`. |
| staging1 admin credentials | **Already configured** in the tool: `artemis_admin` at `server_wide_id = 0` for server `STAGING`, with a stored password. |
| Benchmark host vCPU count | **8 vCPUs, 7 GB RAM**, and the app container has no CPU quota (`NanoCpus=0`, `CpuQuota=0`), so the JVM sees all 8. `threadCount = min(80, N)`. |
| Hardware inventory of staging1 | I can gather it over SSH (step 1.3). |

Student accounts are **not** a prerequisite: the tool creates them itself.
See step 2.1.

---

## Step 1: deploy develop to staging1

### 1.1 Get the WAR

Two routes. Prefer the CI artifact: it is the exact bits CI validated, and it
avoids a 20-minute local build.

```bash
# a) From CI (preferred). Every ci-build run uploads an "Artemis.war" artifact
gh run list --repo ls1intum/Artemis --branch develop --workflow ci.yml --limit 5 \
  --json databaseId,headSha,conclusion,createdAt
gh run download <RUN_ID> --repo ls1intum/Artemis -n Artemis.war -D /tmp/artemis-war
ls /tmp/artemis-war            # Artemis-10.0.war

# b) Locally, if you want a specific working tree
cd ~/Projects/Artemis
./gradlew -Pprod -Pwar clean bootWar    # add -Psbom only for a release
ls build/libs/Artemis-10.0.war
```

Note the commit SHA. It goes into the results table; "develop" is not a version.

```bash
git -C ~/Projects/Artemis rev-parse --short develop
```

### 1.2 Record the baseline

Before overwriting anything (needs the VPN):

```bash
curl -s https://staging1.artemis.cit.tum.de/management/info | jq '{version: .build.version, git: .git.commit.id, profiles: .activeProfiles}'
ssh node1.staging1.artemis.cit.tum.de 'ls -l /opt/artemis/artemis.war*'
```

The role keeps the previous WAR at `/opt/artemis/artemis.war.old`
(`restart_artemis.yml`), which is your rollback.

### 1.3 Capture the hardware (P10)

```bash
cd ~/Projects/artemis-ansible
ansible artemis_staging1_nodes -m setup \
  -a 'filter=ansible_processor_vcpus,ansible_memtotal_mb,ansible_devices' \
  | tee /tmp/staging1-hardware.json
```

### 1.4 Understand what the playbook does before running it

`playbooks/artemis-staging1/nodes-version-update.yml` targets
`artemis_staging1_nodes` (**all 6 hosts**) with `download_artemis_application:
true` and `restart_artemis: true`. `setup_system` and `update_artemis_config`
stay `false`, so:

- **only the WAR is swapped, and `application-prod.yml` is not regenerated.** If
  develop needs a new config key, run `nodes-update-config.yml` first, or the
  nodes come up misconfigured.
- The service unit is not re-templated, which is why `gather_facts: false` is
  safe here.

Task order in `restart_artemis.yml` (ansible runs one task across all hosts
before moving to the next):

1. node1 receives `artemis.war.new` (rsync push from your laptop, because a
   filesystem path does not match the release regex `^\d+\.\d+(\.\d+)?$`).
2. "Stop Artemis" on node2, node3 and all three agents.
3. "Stop Artemis on node 1".
   → **the whole cluster is now down.** This is not a rolling restart.
4. node1 swaps the WAR and restarts, then polls `management/info` up to
   120 x 5 s = **10 minutes**.
5. Only then do the other five hosts receive the WAR and restart.

So plan for staging1 being unavailable for roughly the JVM start time plus the
copy, and for the agents to come back last.

Requirements: `rsync` on your laptop and on all six hosts
(`ansible.posix.synchronize`), and `artemis_version` must be a **string**, which the
role asserts (`roles/artemis/tasks/main.yml:4`). A path is fine; a bare
`10.0` would parse as a float.

### 1.5 Dry run, then deploy

```bash
cd ~/Projects/artemis-ansible
source set_vault.sh                       # P1

WAR=/tmp/artemis-war/Artemis-10.0.war     # absolute path, on this machine

# Dry run. Expect the copy/restart tasks to skip: several are guarded by
# `not ansible_check_mode`, so --check cannot fully model the swap.
ansible-playbook playbooks/artemis-staging1/nodes-version-update.yml \
  --diff --check -e artemis_version="$WAR"

# Real run
ansible-playbook playbooks/artemis-staging1/nodes-version-update.yml \
  --diff -e artemis_version="$WAR"
```

`run.sh` is a one-line wrapper that sources the vault script first:
`./run.sh playbooks/artemis-staging1/nodes-version-update.yml --diff -e artemis_version="$WAR"`.

### 1.6 Verify the deploy

The aggregate `/management/health` on staging1 reports **DOWN** because of a
pre-existing Iris token problem. Do not treat that as a failed deploy. Check
these instead:

```bash
curl -s https://staging1.artemis.cit.tum.de/management/info | jq '.build.version, .git.commit.id'
curl -s -o /dev/null -w '%{http_code}\n' https://staging1.artemis.cit.tum.de/management/health/readiness
curl -s -o /dev/null -w '%{http_code}\n' https://staging1.artemis.cit.tum.de/management/health/liveness

for h in node1 node2 node3 agent01 agent02 agent03; do
  echo "--- $h"
  ssh $h.staging1.artemis.cit.tum.de \
    'grep -c "Started ArtemisApp" /opt/artemis/*.log 2>/dev/null; systemctl is-active artemis'
done
```

All three core nodes must be registered with Eureka and all three agents must
appear in the Artemis admin build-agent view before starting a run. A run
against two-thirds of the cluster is not the run you meant to measure.

---

## Step 2: run the ladder from artemis-performance-test0

### 2.1 One-time setup in the tool

Verified on the VM, so most of this is already done:

1. **Admin credentials for STAGING: already configured.** `artemis_admin` sits at
   `server_wide_id = 0` for server `STAGING` with a stored password, which is what
   `CREATE_COURSE_AND_EXAM` needs. The tool stores it in plaintext in its own PostgreSQL
   database, which is documented behaviour, so keep that database restricted.
2. **Students: 500 exist, more are needed above the 500 rung.** `artemis_test_user_1`
   through `artemis_test_user_500`. A run of N uses ids 1..N, so the rungs nest and the
   100, 200 and 500 runs need nothing. For 1000, add 500 more; for the 2000 extension,
   add 1500. Artemis Users → add by pattern, tick *Create users on Artemis*, continue the
   `artemis_test_user_N` pattern. staging1 has LDAP configured, so these must be
   Artemis-internal accounts, which is what this feature creates.
3. **Token cache is mostly cold.** Only 51 of the 501 STAGING rows hold a JWT, and those
   expired on 2026-03-03. So the first rung re-authenticates nearly every student, which
   is exactly the case B1 blocks. Do not start before #239 is deployed.
4. **Prometheus (P8/P9).** Fill `artemis.staging.prometheus-instances.{artemis,vcs,ci}`
   and `prometheus.auth-token`. Discover the labels with:
   ```bash
   curl -s -H "Authorization: Basic $TOKEN" \
     'https://artemis-test-prometheus.artemis.cit.tum.de/api/v1/label/instance/values' | jq
   ```
   For ICL, `vcs` and `ci` are the same hosts as Artemis; on the CI side the build agents
   are the interesting instances.

**There is prior art to compare against.** staging1 has run this ladder before: run 195
finished 2000 users on 2024-01-23, runs 154/155 finished 1005, and several finished 505.
Those used 6 pushes per student and `EXISTING_COURSE_CREATE_EXAM`. The most recent activity
is simulation 136, 50 users with 3 to 5 pushes, 17 runs, last on 2026-02-01. Pull those
percentiles before the new ladder: a 2024 comparison on the same hardware is worth having,
even though the Artemis version and the push count differ.

### 2.2 How intense this actually is

The simulation is **not** a scaled-down exam. It is a closed-loop maximum-throughput test, and the
difference decides which student counts are meaningful.

**There is no think time.** The only pause anywhere inside a student's exam is `sleep(100)` before
each push (`SimulatedArtemisStudent:802`). Everything else is issued as fast as the server answers.
A real 90-minute exam spreads the same work over 5400 seconds.

**The default push count is high.** The form defaults to `numberOfCommitsAndPushesFrom = 8`,
`numberOfCommitsAndPushesTo = 15`, and the draw is `nextInt(from, to)` with an exclusive upper
bound, so **8 to 14 pushes per student, averaging 11**. Every push is one build.

Rate comparison, same population either way:

| | Pushes | Spread over | Push rate |
| --- | --- | --- | --- |
| Real 2000-student exam | ~22,000 | 90 min | ~4 /s |
| Simulation, 80 threads, ~500 ms round trip + 100 ms sleep | as configured | as fast as accepted | ~130 /s |

So roughly **30x the push pressure of a real exam of the same size**. Reading a simulated count as
a real headcount understates the platform by more than an order of magnitude.

**The build queue, not Artemis, is the long pole.** staging1 has 6 concurrent build slots (B3).
Measured on 2026-08-21 with a 20-student run: **14.1 s per build** with warm images, 33 builds draining
in 93 s, which the tool reported as **14.5 builds per minute**. Drain is about
`students * pushes / 6 * 14.1 s`:

| Students | Pushes each | Builds | Drain |
| --- | --- | --- | --- |
| 100 | 1 to 2 | ~150 | ~6 min |
| 200 | 1 to 2 | ~300 | ~12 min |
| 500 | 1 to 2 | ~750 | ~29 min |
| 1000 | 1 to 2 | ~1500 | ~59 min |
| 2000 | 1 to 2 | ~3000 | ~2 h |
| 1000 | 8 to 14 (default) | ~11,000 | **~7 h** |

The default push range is still unusable at these sizes, but the ladder itself is comfortable.

**You cannot configure zero pushes.** The form requires `from > 0` and `to > from`, so the floor is
`from = 1, to = 2`, meaning exactly one push per student. To take the build agents out of the
picture entirely, use the no-programming-exercise variation instead.

### 2.2.0 Warm the build image on every agent first

Do this before every campaign. It is not optional and it is easy to miss, because the failure looks
like a benchmark result rather than a setup problem.

staging1 expires build images after 7 days (`image_cleanup.expiry_days: 7`, daily at 03:00), so the
agents routinely start a run with nothing cached. The first run of 2026-08-21 produced **33 builds,
all FAILED**, averaging 8.6 s: 29 pushes arrived at once, every job tried to pull the same 1 GB image
simultaneously, and the pulls were killed. The log says `Could not pull Docker image ... Status 404`,
which reads like a missing tag but is not: the tag exists, a manual pull takes 7 s, and Docker Hub is
reachable from the agents.

Pull it on all three agents first:

```bash
for a in staging1agent01 staging1agent02 staging1agent03; do
  ssh "$a" 'sudo docker pull ls1tum/artemis-maven-template:java17-25'
done
```

After warming, the identical run produced **33 builds, all SUCCESSFUL**, averaging 14.1 s. That is the
difference between a build metric that means something and one that measures a failing image pull.
Check the exercise's actual image if the benchmark's programming exercise ever changes language or
project type.

### 2.2.1 The concurrency ceiling, and what the ladder therefore varies

`threadCount = min(availableProcessors * 10, numberOfUsers)`, computed once for the whole run
(`SimulationExecutionService:251`), on the host running the tool. If
`artemis-performance-test0` has 8 vCPUs, that is 80.

**Above `vCPU * 10` students, adding students does not raise the instantaneous load.** It makes each
phase longer. On an 8-vCPU benchmark host, 100, 200, 500 and 1000 students all drive the same
80-wide concurrency; what changes is duration, accumulated database and repository state, and the
number of queued builds.

That is worth knowing before reading the results, because it changes what a degrading percentile
means. Across this ladder it points at accumulation (table sizes, queue depth, NFS contention),
not at more simultaneous users. Two consequences:

- **Get the benchmark host's vCPU count first.** It sets the peak concurrency, so a run on a
  differently-sized benchmark host is not comparable. If the tool runs in a container with a CPU
  quota, `availableProcessors` reflects the quota, not the host.
- **To raise peak concurrency you have to change the benchmark host**, not the student count. More
  vCPUs, or several benchmark hosts driving one exam.

What *does* scale with the student count regardless: the admin preparation phase (generating N
student exams and preparing N participations), the final submit phase, and total volume.

### 2.2.2 Other mechanics worth knowing

- The phases are barriers: all students log in, then all perform initial calls, then all start the
  exam, then all participate, then all submit. There is no ramp-up and no arrival-rate model, so
  each phase begins as a thundering herd.
- Each of the modeling, text and quiz exercises is submitted **once** per student
  (`handleExercises` iterates the exam's exercises a single time). Only the programming exercise
  repeats, `n` times. The README's "submits each exercise several times" no longer matches the code.
- Repository access is drawn per student from four percentages that must sum to 100: online IDE,
  password, participation token, SSH (`SimulationExecutionService:813`). The form defaults to
  `password 100`. `ideType` still exists on the entity but no longer drives the choice.
- SSH clones go to port 7921 and bypass nginx's HTTP rate limits entirely; password and token
  clones go through `/git/` and do not. An all-SSH mix would hide B2 rather than fix it.

### 2.3 The ladder

Mode for every rung: **`EXISTING_COURSE_CREATE_EXAM`** against the prepared course, not
`CREATE_COURSE_AND_EXAM` (see B4).

Pre-flight, not a data point: **10 students, 1 push each.** Proves the users, admin rights, build
agents and git path work, and gives the first real measurement of `B`, the per-build time, which
every duration estimate above depends on. Do not report it.

Then, holding the exam shape and the repository-access mix fixed and changing only the student
count:

| # | Students | Pushes per student | Purpose |
| --- | --- | --- | --- |
| S1 | 100 | 1 to 2 | Baseline. Every later run is compared against these percentiles. |
| S2 | 200 | 1 to 2 | First check that nothing degrades with twice the volume. |
| S3 | 500 | 1 to 2 | Where queueing usually becomes visible. |
| S4 | 1000 | 1 to 2 | The decision point: read the metrics here before going further. |

Then, **only if S4 leaves headroom**, extend to 1500 and 2000. Decide from S4's numbers rather than
planning it in advance: if p95 is already bending or the build queue has not drained within the
window, a larger run adds hours and no information.

Set the push range as `from = 1, to = 3` for "1 to 2 pushes", or `from = 1, to = 2` for exactly one.
Keep it identical across every rung, or the build load is not comparable.

Because of the concurrency ceiling in 2.2.1, treat these rungs as varying **volume and duration**,
not peak concurrency. A simulated 1000 is not a real 1000; on this tool it is considerably more
demanding than a real exam of that size.

Two variations at whichever rung turned out interesting:

| Variation | Change | Isolates |
| --- | --- | --- |
| Online editor | Online IDE share set to 100% | Moves programming traffic off git and onto REST/WebSocket, separating version control cost from application cost. |
| No programming exercise | Exam with only modeling, quiz, text | Removes build agents and the git server, so the remaining latency belongs to Artemis and MySQL. This is also the only way to get to zero builds. |

**Stop rules.** Abort and fix rather than continuing up the ladder if: the
error rate exceeds a few percent; any `429` appears (that is B1 or B2, not a
result); or fewer students complete than were configured. A run with skipped
students silently reports the latency of the survivors.

Between runs, let the build queue drain completely. The CI status keeps counting
after the simulation reports "finished", and starting the next rung on top of the
previous backlog measures the backlog. With 6 slots this is the longest part of a
run, so budget it from the table in 2.2 rather than guessing.

### 2.4 What a run leaves behind

`cleanup-enabled: false` means every run leaves a course, an exam and up to
2000 participations with repositories on staging1's NFS. Six runs is a lot of
storage and a slower database for the next run. Plan a manual cleanup after
the whole ladder, not between runs, and check
`/mnt/storage/local-vcs-repos` for space before starting.

---

## Step 3: monitor staging1 during the runs

Three vantage points; use all three, because each misses something.

**a) The tool.** Live log lines, per-minute response times per action, and
after the run the CI status (build count and builds/min, available because
`is-local: true` and cleanup is off).

**b) SSH sampling, in place of Prometheus.** The Prometheus token is not available, so the tool
will show no server-side workload data. Sample the hosts directly instead. Run this from the laptop
before starting a rung and leave it going; one CSV per host, joined on the timestamp afterwards:

```bash
# 5-second samples of load, memory and JVM GC on each core node + agent
for h in staging1node1 staging1node2 staging1node3 staging1agent01 staging1agent02 staging1agent03; do
  ssh -o BatchMode=yes "$h" 'while true; do
      printf "%s,%s,%s\n" "$(date -u +%FT%TZ)" \
        "$(cut -d" " -f1-3 /proc/loadavg | tr " " "/")" \
        "$(free -m | awk "/^Mem:/{print \$3\"/\"\$2}")"
      sleep 5
    done' > "sample-$h.csv" 2>/dev/null &
done

# JVM heap and GC on a core node, once a run is under way
ssh staging1node1 'sudo -n jcmd $(pgrep -f artemis.war) GC.heap_info'

# MySQL connections against the 2000 ceiling, plus the slow log
ssh staging1db "mysql -e \"SHOW GLOBAL STATUS LIKE 'Threads_connected'; SHOW GLOBAL STATUS LIKE 'Max_used_connections'; SHOW GLOBAL STATUS LIKE 'Slow_queries';\""
ssh staging1db 'sudo -n tail -F /var/lib/mysql/artemis-production-db-slow.log'
```

Take a `Max_used_connections` reading before each rung and after, since it is a high-water mark and
does not reset on its own (`FLUSH STATUS` resets it if you want a per-rung figure).

**c) The hosts, live.** One terminal per role:

```bash
# Application log on each core node
ssh staging1node1 'tail -F /opt/artemis/artemis.log'

# 429s at the proxy. Must stay flat; anything here invalidates the run (B1/B2)
ssh staging1proxy "tail -F /var/log/nginx/access.log | grep --line-buffered ' 429 '"

# Build agents: container churn and queue behaviour
ssh staging1agent01 'sudo -n docker ps | wc -l; tail -F /opt/artemis/artemis.log'
```

Watch for, in rough order of likelihood:

| Symptom | Reading |
| --- | --- |
| `429` at the proxy | B1 or B2. The run is invalid; stop. |
| `Max_used_connections` approaching 2000 | Pool exhaustion; authentication and submit latency will rise together. |
| Builds/min flat while push latency stays flat | Build agent capacity (B3). Expected: a capacity number, not a defect. |
| Clone/push degrading, application calls flat | The git server or NFS storage. |
| One node's latency diverging from the others | Load balancer distribution or a single-node problem; check Eureka registration. |
| Heap climbing to the `-Xmx` ceiling with rising GC | Note the host RAM, since `-Xmx` is 80 % of it. |

---

## Step 4: publish the numbers

Target: `documentation/docs/admin/benchmarking-tool.mdx`. The page already has
the "Running a comparable scale test" table defining what a result must carry,
the ladder, and a `TODO(maintainers)` comment (line ~103) reserving the spot
for the TUM reference run. Replace that comment with a real section.

Record per run:

- Artemis version **and commit SHA** (`10.0`, plus the develop SHA from 1.1)
- Topology: 3 core nodes, 3 build agents, nginx LB, ActiveMQ broker, Eureka
  registry, NFS storage, MySQL on its own host
- Hardware per host from `/tmp/staging1-hardware.json` (1.3), and `-Xmx` = 80 %
  of RAM
- Build capacity: 6 concurrent (3 × 2)
- Database: MySQL, version, `innodb_buffer_pool_size 10G`,
  `max_connections 2000`
- Scenario: student count, `CREATE_COURSE_AND_EXAM`, the four percentages, the
  commits-and-pushes range, and the benchmark host's vCPU count (which sets the
  concurrency)
- Results: median/p95/p99 per action (authentication, get student exam, start
  student exam, submit exercise, submit exam, clone, push), error rate, and
  builds/min
- The observed bottleneck and at which rung a percentile started to bend

The interesting sentence is not "2000 students worked". It is "at N students,
p95 of <action> went from X to Y because <resource> saturated". Write that one
explicitly.

Once the run is published, the "more than 2,000 students" claim in `README.md`,
the landing page and the platform comparison can link to it, which is the whole
point of the exercise.

---

## Rollback

The role saves the previous WAR:

```bash
cd ~/Projects/artemis-ansible && source set_vault.sh
# Either re-run the playbook pinned to the previous release tag (quoted!)
ansible-playbook playbooks/artemis-staging1/nodes-version-update.yml \
  --diff -e artemis_version="9.7.1"
# or, per host, restore /opt/artemis/artemis.war.old and restart:
ssh node1.staging1.artemis.cit.tum.de \
  'sudo cp /opt/artemis/artemis.war.old /opt/artemis/artemis.war && sudo systemctl restart artemis'
```

A release tag must be quoted, because the role asserts it is a string and
unquoted `9.10` becomes the float `9.1`.

If develop applied Liquibase changes that the previous version cannot read, a
WAR rollback alone is not enough; restore the database from the `db_backup`
role's snapshot as well. Confirm before the deploy whether develop carries
migrations relative to what staging1 runs today (1.2 gives you the from-version).

---

## Open questions for you

1. Review and merge artemis-ansible-collection#239 and artemis-ansible#93 (B1 and B2).
2. Note for whoever merges second in the collection: artemis-ansible-collection#238 adds a
   `loginoptionslimit` zone keyed by `$binary_remote_addr`. The client calls that endpoint once per
   login attempt, right before `POST authenticate`, so it needs `$rate_limit_key` too or it just
   becomes the new bottleneck.
3. Does staging1 need `nodes-update-config.yml` before the version update, i.e.
   does develop introduce config keys staging1 does not have yet?
4. Which release is staging1 on today (rollback target and migration question)?
5. Should the runbook live in `artemis-ansible` rather than a scratchpad? The
   staging1 topology, Vault paths and rate-limit internals do not belong in the
   public Artemis docs.
