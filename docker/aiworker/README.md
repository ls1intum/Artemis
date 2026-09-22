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

See [AI Worker operations](../../documentation/docs/admin/aiworker.mdx) for complete
configuration, topology, storage durability, capacity, maintenance and canary checks.
See [module boundaries](../../documentation/docs/developer/hyperion/worker-modules.mdx)
for workload integration. Hyperion's qualified Java/Gradle sandbox recipe remains in
[`docker/hyperion`](../hyperion/README.md).
