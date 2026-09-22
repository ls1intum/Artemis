# Hyperion workload images

The generic execution supervisor is the **AI Worker**, not a Hyperion-specific
transport. Build and configure it through [docker/aiworker](../aiworker/README.md).
That directory owns the worker Dockerfile and protocol-4 TLS CORE broker configuration.
Use `AI_WORKER_*` environment variables and `artemis.aiworker.*` transport properties;
Hyperion feature settings are separate.

This directory owns Hyperion's language/toolchain sandbox recipes, not broker
credentials, worker identity or generic execution policy. A supervisor without an
installed workload advertises no useful generation capacity. A process starting
successfully is not a generation qualification test.

Use a dedicated worker VM for staging and production. The supervisor's Docker socket
is host-level authority; never share core's, the database's or an exam build agent's
daemon. Generated code runs with a read-only root filesystem and without networking.
Pin supervisor and sandbox images by digest and coordinate core, worker and broker
upgrades after draining active work. Worker process loss is not a durable local-outbox
guarantee: core must fence the lost incarnation using the evidence it already holds.

## Offline Java Gradle build image

Build the sandbox image from the repository root:

```sh
docker build -f docker/hyperion/gradle-sandbox.Dockerfile -t hyperion-gradle-sandbox:local .
docker image inspect hyperion-gradle-sandbox:local --format '{{.Id}}'
```

Use the emitted immutable image ID as `AI_WORKER_SANDBOX_IMAGE` on this host.
For another host, publish the image through the deployment's image registry,
preload it, and configure its registry digest. Do not use a mutable tag for worker
admission.

The image builds Artemis's canonical Gradle test harness with the trusted
readiness fixture online at image-build time, then repeats it offline. It retains
the canonical Teamscale plugin, wrapper, Java 17 toolchain and test dependency
versions. The supported fixture has no static code analysis, sequential tests or
private Maven Central mirror. A deployment with a private mirror needs its own
qualified cache; never copy repository credentials into a sandbox image.

Runtime builds execute as UID 1000 without network access. Each pristine build
copies the public image cache to bounded private tmpfs; no writable dependency
cache is shared with another execution. Authoring uses JUnit's `Simple`
display-name generator so Gradle report names match the method names used in task
bindings. The shared LocalCI report parser is not modified to rename tests.

Qualify the image against actual isolated builds:

```sh
HYPERION_GRADLE_TEST_IMAGE=$(docker image inspect hyperion-gradle-sandbox:local --format '{{.Id}}') \
  ./gradlew :aiworker:test --tests '*DockerGradleBuildTest' -x webapp
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
