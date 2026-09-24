# AI Worker transport

AI Worker is a separate Artemis feature package, not a Build Agent service. Its
supervisor and sandbox beans start only with the `aiworker` Spring profile. The
normal Artemis WAR contains them; there is no worker-only Gradle project. The
worker image runs an isolated entry point from that same WAR:

```sh
./gradlew -Pprod -Pwar bootWar -x webapp
docker build -f docker/aiworker/worker.Dockerfile \
  --build-arg ARTEMIS_WAR=build/libs/Artemis-10.1.war -t artemis-aiworker:local .
```

Use the exact WAR path produced by the build. The worker entry point rejects
server and build-agent profiles. Use `aiworker` for a combined test node, but
do not give the core container access to the host Docker socket.

`broker.xml` defines the private TLS CORE listener, exact per-worker ACLs,
persistent queues, and bounded redelivery for `worker-1`. Provision credentials
and certificate trust separately. Do not reuse the browser STOMP broker or grant
queue-management permissions to workloads.

The core role is `aiworker-core`; the sample worker role is `aiworker-1`. Broker
JVM properties are `aiworker.keyStorePath` and `aiworker.keyStorePassword`.
Add worker addresses and roles explicitly, never through wildcard permissions.

The generic worker has no workload implementation. A workload must supply a
`WorkloadApi` bean. Production workers must keep Docker access in an isolated
worker environment, not in the core container.
