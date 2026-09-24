# AI Worker transport

AI Worker is a separate Artemis feature package, not a Build Agent service. Its
supervisor and sandbox beans start only with the `aiworker` Spring profile. The
normal Artemis WAR contains them; there is no worker-only Gradle project or
second application artifact. The workload layer packages that WAR in an isolated
worker container image.

Build Agents receive LocalCI work through the application distributed-data
provider. AI Workers do not join that provider or receive its credentials. They
use a separate broker with worker-scoped command and event queues instead.

`broker.xml` defines the private TLS CORE listener, exact per-worker ACLs,
persistent queues, and bounded redelivery for `worker-1`. Provision credentials
and certificate trust separately. Do not reuse the browser STOMP broker or grant
queue-management permissions to workloads.

The core role is `aiworker-core`; the sample worker role is `aiworker-1`. Broker
JVM properties are `aiworker.keyStorePath` and `aiworker.keyStorePassword`.
Add worker addresses and roles explicitly, never through wildcard permissions.
The file is a one-worker example, not a dynamic broker configuration generator.

The generic worker has no workload implementation. A workload must supply a
`WorkloadApi` bean. Production workers must keep Docker access in an isolated
worker environment, not in the core container.
