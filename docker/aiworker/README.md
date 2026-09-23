# AI Worker execution infrastructure

Build from the repository root:

```sh
./gradlew :aiworker:bootJar :aiworker:cyclonedxDirectBom -x webapp
docker build -f docker/aiworker/worker.Dockerfile -t aiworker:local .
```

`worker.Dockerfile` packages the outbound-only executable. `broker.xml` defines the
private TLS CORE listener, exact per-worker ACLs, persistent queues and bounded
redelivery/dead-letter policy for `worker-1`. Provision credentials and certificate
trust separately; do not reuse the browser STOMP broker or grant queue-management
permissions to workloads.

The core role is `aiworker-core`; the sample worker role is `aiworker-1`. Broker JVM
properties are `aiworker.keyStorePath` and `aiworker.keyStorePassword`. Supply them
through the deployment secret mechanism. Add worker addresses and roles explicitly,
never through wildcard worker permissions.

The generic worker has no workload implementation. A workload distribution must
supply a `WorkloadApi` bean and its dependencies. The contracts are in
[`modules/aiworker/api`](../../modules/aiworker/api), and the supervisor configuration
is in [`modules/aiworker`](../../modules/aiworker). Set broker credentials through
`SPRING_ARTEMIS_USER` and `SPRING_ARTEMIS_PASSWORD`, and configure verified TLS through
`AI_WORKER_BROKER_URL`. Worker settings use the `artemis.aiworker` namespace.

Hyperion's sandbox recipe is in [`docker/hyperion`](../hyperion/README.md).
