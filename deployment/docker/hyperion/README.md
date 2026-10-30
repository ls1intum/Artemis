# Hyperion workload images

The generic execution supervisor is the **AI Worker**, not a Hyperion-specific
transport. Its profile-gated code uses the Artemis WAR. The
[AI Worker operations](../../../documentation/docs/admin/aiworker.mdx) describe the
shared distributed-data provider transport. Hyperion feature settings are separate.

This directory owns Hyperion's language/toolchain sandbox recipes, not provider
credentials, worker identity or generic execution policy. A supervisor without an
installed workload advertises no useful generation capacity. A process starting
successfully is not a generation qualification test.

Use a dedicated worker VM for staging and production. The supervisor's Docker socket
is host-level authority; never share core's, the database's or an exam build agent's
daemon. Generated code runs with a read-only root filesystem and without networking.
Pin the Artemis and sandbox images by digest and coordinate core and worker
upgrades after draining active work. Worker process loss is not a durable local-outbox
guarantee: core must fence the lost incarnation using the evidence it already holds.

## Hyperion workload

The normal Artemis WAR contains the generic AI Worker and Hyperion's workload.
The worker runs the normal Artemis image with the `prod,aiworker` profiles. Its
workload defaults to `hyperion-generation`; the Hyperion adapter stays inactive
for other workloads. Keep the worker process in an isolated VM.

## Offline Java build image (Gradle and Maven)

This image has an independent toolchain version, like the build agents' build images.
The worker's shipped default is `ghcr.io/ls1intum/artemis-hyperion-sandbox:java25-1`.
It reuses a cached image or pulls it when missing, then uses the immutable local image ID.
Normal Artemis upgrades and PR builds use the same default; no per-commit image is required.

Maintainers publish a new toolchain version through the **Publish Hyperion Sandbox Image**
workflow (`hyperion-sandbox-image.yml`) on a reviewed source revision. The workflow reads the tag
from that revision's default in `application-aiworker.yml`; there is no version input. For a new
toolchain, change the default to a new version such as `java25-2` in the reviewed branch. The workflow builds both amd64 and arm64 using the same shared publisher
as Artemis. Always use a new version; published tags must not be overwritten. Publish and qualify the branch's new version before merging its default change. The first `java25-1` publication must complete before
using the default in a deployment. Check that the GHCR package is publicly readable.

The sandbox has a separate release lifecycle: ordinary Artemis releases do not publish it.
When the canonical harness, JDK or dependencies change, build and test a new sandbox version and
update the shipped default in the same change. Retain previous versions for rollback.

For a custom sandbox, build from the repository root:

```sh
docker build -f deployment/docker/hyperion/sandbox.Dockerfile -t hyperion-sandbox:local .
docker image inspect hyperion-sandbox:local --format '{{.Id}}'
```

Use the emitted immutable image ID as `AI_WORKER_SANDBOX_IMAGE` on this host.
For another host, publish the image through the deployment's image registry,
preload it, and configure its registry digest. Do not use a mutable tag for worker
admission.

The image builds Artemis's canonical Gradle test harness and both canonical Maven
test harnesses (`PLAIN_MAVEN`, `MAVEN_MAVEN`) with the trusted readiness fixture
online at image-build time, then repeats each build offline. Maven is a pinned
Apache distribution verified by checksum. It retains
the canonical Teamscale plugin, wrapper, Java 25 toolchain, Ares 2 policy and test dependency
versions. The supported fixture has no static code analysis, sequential tests or
private Maven Central mirror. A deployment with a private mirror needs its own
qualified cache; never copy repository credentials into a sandbox image.

Runtime builds execute as UID 1000 without network access. Each pristine Gradle build
copies the public image cache to bounded private tmpfs; each Maven build runs offline
with a fresh private local repository on tmpfs and the image repository chained behind
it as a read-only tail (`maven.repo.local.tail`); no writable dependency
cache is shared with another execution. Authoring uses JUnit's `Simple`
display-name generator so Gradle and Surefire report names match the method names used in task
bindings. The shared LocalCI report parser is not modified to rename tests.

Qualify the image against actual isolated builds:

```sh
HYPERION_GRADLE_TEST_IMAGE=$(docker image inspect hyperion-sandbox:local --format '{{.Id}}') \
  ./gradlew test --tests '*DockerGradleBuildTest' -x webapp
```

This test builds a solution and incomplete template, parses their real reports,
compares the verdicts and names with direct canonical LocalCI build phases, and
checks that a subsequent solution build cannot reuse failed-template output.
It needs Docker but no application URL or HTTP port. This is build parity, not an
LLM-generation or exercise-quality claim.

## Worker telemetry

The worker emits Spring AI observations inside an assignment-scoped
`hyperion.generation` observation. Its metadata identifies the job, execution,
exercise and effective effort profile; credentials and the exercise brief are
not observation tags. The worker does not inherit the core node's telemetry
configuration or require an incoming telemetry port.

Trace export is disabled by default. To use an approved OTLP collector, configure
these Spring properties on the worker:

```yaml
management:
    tracing:
        export:
            otlp:
                enabled: true
    opentelemetry:
        tracing:
            export:
                otlp:
                    endpoint: https://collector.example.org/v1/traces
artemis:
    telemetry:
        gen-ai:
            capture-content: false
            max-attribute-bytes: 2000000
```

Authentication headers belong in the deployment's secret configuration, not the
sandbox image. Trace sampling defaults to 100% for these low-volume generation
jobs; reducing it makes traces unsuitable for exact usage reconciliation.
Metrics and log export are independently disabled by default.

Set `capture-content` to `true` only for an access-controlled collector approved
to receive prompts, completions, reasoning and tool inputs/outputs. The filter
omits oversized UTF-8 attributes rather than exporting invalid truncated JSON
and marks `artemis.gen_ai.content.complete=false`. Without this opt-in, only
metadata is added. Content capture is a worker-side setting; enabling it on the
core does not enable it on workers. Export delivery and independent usage
reconciliation must be checked on the deployed stack before claiming complete
benchmark evidence.

## Adding a language or build system

The Hyperion feature owns typed authoring payloads and language-specific adapters.
The AI Worker feature owns transport, scheduling and sandbox isolation.
Its shared `session`, `messaging`, `sandbox` and `config` packages cannot depend on an adapter;
`ToolchainArchitectureTest` enforces that direction. Java/Gradle-specific prompts, inspectors,
workspace preparation, grading and repair policy live under `toolchain/javagradle`.

To qualify another toolchain:

1. Implement `ToolchainGenerationAdapter` with a distinct `GenerationToolchain` identifier.
   Keep language syntax checks in that adapter, not in the shared exercise brief. Provide
   namespace, workspace, trusted-harness, report parsing and grading behavior for the target.
2. Preserve differential verification, integrity checks, frozen-candidate identity, cancellation,
   accounting and checkpoint guarantees. Never substitute an unchecked result or Java fallback.
3. Build a digest-pinned offline sandbox image and test both verification lanes against the
   corresponding ordinary Artemis build. Qualify isolation and resource bounds too.
4. Register the supported language/project-type pair in core's `LanguageGenerationProfile` only
   after its seed, result interpretation and guarded persistence paths are qualified. A worker
   advertisement alone cannot expand supported product configurations.
5. Deploy a worker with that adapter, image and toolchain ID. Core reserves only matching capacity;
   both ends check toolchain and image identity. Missing or duplicate adapters prevent startup.

The non-Java fake adapter in `DefaultGenerationEngineTest` verifies the dispatch boundary, not
production Python support. Java Gradle remains the only qualified production combination.
