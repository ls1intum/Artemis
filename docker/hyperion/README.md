# Hyperion worker transport

The worker is a separate, non-web Spring Boot executable. It has no database,
application-grid, Git-server or shared-repository dependency. Its supervisor has
Docker access on a **dedicated generation host**; generated commands run only in
network-isolated containers. Docker access grants host-level authority to the
supervisor, so do not place it on a core, database or exam build-agent host.

Build the executable and image from the repository root:

```sh
./gradlew :hyperion-worker:bootJar -x webapp
docker build -f docker/hyperion/worker.Dockerfile -t hyperion-worker:local .
```

The worker runs the Java Gradle generation engine. Configure exactly one Spring AI
OpenAI-compatible chat model through the worker's standard `spring.ai.openai.*`
settings. Provider credentials belong only to the worker process, never to the
build image or assignment payload. A successful process start alone is not a
generation smoke test.

Set `spring.ai.openai.timeout` on the worker to bound each provider request;
`spring.ai.openai.chat.timeout` overrides it for chat. The worker applies this
deadline to its per-request chat options as well as the HTTP client. This is
separate from the assignment's overall wall-time budget. Core-node model
configuration is not inherited by the worker.

## Verification and starter behavior

The completed solution must pass every test. The starter must run the same tests
and fail every assessed student-work test. It can retain working code when the
exercise asks learners to modify or extend it.

The generated `test-plan.json` distinguishes assessed work (the default
`ASSESSMENT` purpose) from `PRESERVATION` checks of supplied behavior. Preservation
checks must pass on both solution and starter. They are always visible, persist
with weight zero, and have no student-work seam, risk-partition claim, or task
binding. Every declared student-work seam still needs visible assessed evidence;
preservation checks cannot replace it. Existing plans without a purpose field
remain assessment-only.

These checks establish executable behavior, not teaching suitability. Review the
actual learner changes and any quality findings before releasing an exercise.

## Broker isolation

`broker.xml` is an Apache Artemis broker configuration for one worker named
`worker-1`. Use a dedicated broker instance, persistent broker storage and a
private listener; it is not a configuration for Artemis's browser STOMP relay.
Provision authenticated users through the broker's normal JAAS configuration:

- The core service account has **only** the `hyperion-core` role.
- Worker `worker-1` has **only** the `hyperion-worker-1` role.
- Never grant a worker an administrator role or share credentials between workers.

Supply the broker JVM properties `hyperion.keyStorePath` and
`hyperion.keyStorePassword` from deployment secrets. Mount the keystore read-only.
The sole listener uses TLS on port **61617** and the CORE protocol; no web console
or STOMP access is configured here. Use a certificate whose identity matches the
hostname in the client URL. Do not disable hostname verification or use
`trustAll=true`.

To add another worker, explicitly duplicate its two addresses and their security
settings with a distinct worker ID and role. Do not widen the permissions to a
worker wildcard. Workers can consume only their own commands and send only their
own events. Core can send commands and consume events. Neither can create or
delete queues, send management messages, or access unrelated addresses.

Commands are acknowledged after local admission. Duplicate execution identities
are not executed again within an incarnation. Invalid commands are retried five
times and then dead-lettered. Queues fail sends at their 256 MiB address limit
instead of growing without bound. Monitor `hyperion.dead-letter` and broker disk
capacity. Checkpoints and terminal events are retried while the worker is alive;
worker process loss is **not** an exactly-once or durable local-outbox guarantee.
Core must fence the lost incarnation and use the checkpoints it already received.

## Worker settings

Inject these values into the worker process using the deployment's secret and
configuration mechanism, not the container image:

| Environment variable       | Value                                                                                                  |
| -------------------------- | ------------------------------------------------------------------------------------------------------ |
| `HYPERION_WORKER_ID`       | `worker-1`, unique per running worker                                                                  |
| `HYPERION_SANDBOX_IMAGE`   | A locally available image pinned by SHA-256 digest                                                     |
| `HYPERION_BROKER_URL`      | `tcp://broker.example:61617?sslEnabled=true&verifyHost=true&callTimeout=5000&callFailoverTimeout=5000` |
| `HYPERION_BROKER_USER`     | Worker-specific service account                                                                        |
| `HYPERION_BROKER_PASSWORD` | Worker-specific secret                                                                                 |

Configure the JVM truststore for the broker certificate through the deployment's
standard Java TLS settings. No credentials, arbitrary image pulls or network
permissions are supplied by generation jobs. Preload the sandbox image on the
worker host. The worker image runs as UID 1000; grant only the supervisor access
to its dedicated host's Docker socket. Never mount that socket, secrets or host
repository directories inside a generated-code sandbox.

`artemis.hyperion.worker.runtime` defaults to `runc`; set it to an installed
sandbox runtime such as `runsc` when the deployment requires the additional
kernel isolation. Docker's resource and namespace restrictions are not a claim
that arbitrary code cannot exploit the host kernel.

## Transport verification

```sh
./gradlew hyperionCheck :hyperion-worker:bootJar -x webapp
HYPERION_TEST_IMAGE=sha256:<local-image-id> ./gradlew :hyperion-worker:test --rerun-tasks -x webapp
```

The regular tests load the actual broker permissions and exercise send/consume
authorization, rollback redelivery and dead-lettering using an in-process broker
(no TCP port). The optional Docker tests require a local image containing `sh`,
`id`, `sleep` and `cat`; they inspect the real container restrictions, exercise
binary copying and verify timeout/reset cleanup. They do not test LLM generation,
core persistence or exercise quality.

## Offline Java Gradle build image

Build the sandbox image from the repository root:

```sh
docker build -f docker/hyperion/gradle-sandbox.Dockerfile -t hyperion-gradle-sandbox:local .
docker image inspect hyperion-gradle-sandbox:local --format '{{.Id}}'
```

Use the emitted immutable image ID as `HYPERION_SANDBOX_IMAGE` on this host.
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
  ./gradlew :hyperion-worker:test --tests '*DockerGradleBuildTest' -x webapp
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
