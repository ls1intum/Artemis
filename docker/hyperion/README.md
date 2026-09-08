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

The transport executable advertises no generation capacity unless a generation
engine is installed. A successful process start alone is not a generation smoke
test.

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
