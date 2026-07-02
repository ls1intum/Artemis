# Hyperion Agentic Exercise Generation — Productionization (clean build from develop)

Branch: `hyperion-agentic-production-design` (clean at develop HEAD).
Prototype `/root/jean/Artemis/peach-toucan` (PR #12870) = **reference only**, never merged.
Verified via 4 principal-engineer subagents (live-UX, server-arch, adapt+scope, red-team).

## North star
Fully switch Hyperion to the agentic flow. Exercise created normally → problem statement + metadata
ARE the plan → an LLM agent fills+validates the repos in a hardened sandbox → a **differential oracle**
(reusing LocalCI production parsers) is the sole acceptance truth. Wired to the **editor** view only.
Java-only slice; architecture generalized (each future language = one profile arm). One PR.

## Key architecture decisions (from the review)
- **Live streaming unlock:** `write_file`/`edit_file` tool args arrive IN the loop-node JVM — the full
  file content is already in memory. Emit a `FILE_SNAPSHOT` (whole-file, NOT deltas) over the existing
  STOMP `HyperionWebsocketService` (already crosses nodes for per-user delivery). No new relay, no
  read-back from the sandbox. Owner-only stream.
- **UX:** right-hand "Generation activity" drawer in the editor container + read-only, auto-following
  **Monaco live preview** (text-only sink) + file tree; terminal verdict as PrimeNG chips; cancel.
  Reattach via a capped `jobId → {path → latestSnapshot}` map (latest-per-file only; bounded).
- **Delete `GpuEndpointChatModel`** → Spring AI OpenAI starter (`spring.ai.openai.base-url=.../logos/v1`,
  model `openai/gpt-oss-120b`) + a ~15-line harmony/reasoning-token scrubber decorator. Endpoint
  confirmed live & OpenAI-compatible (clean `content`, reasoning in separate field).
- **Keep the ToolCallingManager loop driver** (turn budget, cancel, streaming, compaction, failure breaker).
- **Differential oracle = crown jewel; re-author faithfully** (same fail-closed gates, same production
  parsers `TestResultXmlParser` + SCA `ReportParser`). Lock every gate with committed NON-LLM fixture tests.
- **Clean sub-packaging:** `hyperion/exercisegeneration/{agent,verification,critic,orchestration,persistence,profile}`
  + `buildagent/service` for the sandbox primitive/relay.
- **Adapt-with-feedback is 70% pre-wired on develop** (review-comment `selectedFeedbackThreadIds` store,
  per-thread toggle, endpoint field, server renderer). Repoint it from the legacy loop to the agentic
  engine. **Explicit `mode` (GENERATE|ADAPT)**, not a heuristic. One endpoint, one engine, one job model;
  branch only at seed + prompt + one gate (relax harness-immutability for the tests repo in ADAPT).
  Revert affordance (capture pre-run SHA) instead of a staging workflow; draft branch only for rejected.

## Mandatory production fixes (red-team)
- **R1 sandbox hardening (CRITICAL, blocker):** cap-drop ALL, non-root user, read-only rootfs + tmpfs
  `/workspace`+`/tmp`, `no-new-privileges` + tightened seccomp, hard non-zero pids/memory/nofile that
  fail closed if flags missing, block link-local/metadata. **Network: `--network none` + pre-baked offline
  Maven cache** (Java-only makes the dep set finite) — hardens AND makes the oracle reproducible.
- **Atomic persist:** compensation/finalize + explicit "generation incomplete" state the UI won't publish.
- **Executor saturation:** `AbortPolicy` → 429/Conflict, not `CallerRunsPolicy` (thread-pool DoS).
- **Multi-node:** lease-based TTL slot/lock; release permit via reaper; reclaim-stale-job path.
- **Observability:** Micrometer per-phase timers/counters (turns, tool latency, verify, accept/reject, tokens), MDC by jobId.
- **F1** prose-gate false-reject fix; **F2** empty-seed fail-closed fix.

## Verification
- Authentic GPU E2E (endpoint above) — **stays UNCOMMITTED**, qualitative eval only. Must land Java
  behaviour / structural(Ares) / SCA at solution 100% / template 0%, + reattach, 409, feature-off, adapt, revert.
- Committed NON-LLM tests: oracle fail-closed gates via real JUnit/SARIF fixtures through production parsers;
  sandbox round-trip; streaming protocol bounds; adapt tests-repo gate relaxation.

## IN SCOPE (one PR, Java only)
- Hardened interactive sandbox + multi-node relay
- Delete legacy multi-step codegen impl (keep/repoint endpoint contract where reused)
- Agentic engine: orchestration + ToolCallingManager loop + tools + system prompt
- Differential oracle + verifier + integrity gates + SCA parity + Ares seeding + spec-critic (non-blocking) + in-loop verify; F1/F2
- Persistence through the normal exercise create/update path (no second grading path)
- Single endpoint + explicit `mode` + one Hazelcast job model
- Editor-only live-streaming UI (`FILE_SNAPSHOT` + read-only Monaco) + verdict + cancel + reattach
- Adapt-with-feedback (multi-select + free-form, dialog, adapt seeding, tests-repo gate relaxation, revert)
- Java `{JAVA}` gate; extensible profile arms retained behind it
- Spring AI OpenAI starter swap (delete GpuEndpointChatModel)
- Micrometer observability, atomic-persist finalize, AbortPolicy, lease-based multi-node locks

## OUT OF SCOPE
- Create-wizard / detail-page UI (editor view is the ONLY surface)
- Non-Java languages (gated off; each = later one-flag PR)
- Backward-compat / legacy job migration
- Committed live-LLM E2E tests; durable JPA job/audit history
- Spec-critic as a blocking gate (stays advisory)

## Internal commit sequence (lands as one PR; each commit compiles)
1. Hardened sandbox primitive + relay + reaper (self-contained, security-reviewable in isolation)
2. Differential oracle + verifier + integrity gates + F1/F2 + fixture tests
3. Agentic engine core (orchestration, loop, tools, system prompt, Hazelcast job model, STOMP events)
4. Persistence + seeding (empty vs current-contents; orphan-delete; build-gate zero-weight; draft-on-reject)
5. Endpoint + `mode` + OpenAI-starter swap + legacy deletion + Java gate
6. Adapt correctness (mode prompt branch, tests-repo gate relaxation, pre-run SHA + revert)
7. Editor UI: live streaming (`FILE_SNAPSHOT` subscriber, read-only Monaco, run card — shared by both modes)
8. Editor UI: Adapt-with-feedback (AI-menu item, reactive label, reuse selection store, dialog, revert)
9. Observability + docs + test sweep

## Egress decision (RESOLVED by user)
- **Network egress ALLOWED (CI-parity).** Instructors are trusted; nothing hidden. Keep default bridge
  network like the existing student-submission sandbox. Still apply the cheap non-network hardening
  (cap-drop ALL + add-back, non-root user, ro-rootfs + tmpfs, ulimits, no-new-privileges) since it does
  not break Maven and is good hygiene. Accept the oracle-reproducibility caveat (external dep flakiness).
