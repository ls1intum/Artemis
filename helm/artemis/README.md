# Artemis multi-node LocalCI chart

This MVP chart deploys a complete Artemis LocalCI installation in Kubernetes:

- one Artemis leader core with the `scheduling` profile;
- zero or more member cores without `scheduling`;
- PostgreSQL, JHipster Registry/Eureka, and ActiveMQ STOMP relay;
- standalone LocalCI build-agent controllers; and
- Kubernetes Jobs created directly for individual builds.

Docker execution remains the default outside the `k8s` profile. The controllers in this chart use
`prod,buildagent,k8s` and do not need a Docker socket.

## Execution flow

1. Core nodes place jobs in the existing distributed priority queue and calculate estimated start/completion times.
2. A controller claims work according to its configured concurrency.
3. The controller clones LocalVC repositories with the existing build-agent account.
4. The controller creates a Job in `buildAgents.namespace` with the exercise image and a trusted helper sidecar.
5. Repositories and the script are transferred through the Kubernetes exec API into an `emptyDir` workspace.
6. Build logs are streamed into the existing LocalCI log map. The helper returns the same tar result format used by Docker execution.
7. Existing XML/SARIF parsing, result queueing, cancellation, pause/resume, retry counts, statistics, and time estimates continue unchanged.

The workload service account has no API token and no permissions. The controller receives only the namespaced Job/Pod/log/exec permissions in
`templates/build-agents/serviceaccounts-rbac.yaml`.

## Storage and multi-core requirements

Every core must mount the same `/opt/artemis/data` filesystem. It contains LocalVC repositories, uploads, exports, and build logs. A real multi-node
cluster therefore needs `ReadWriteMany` storage. Docker Desktop has only local `ReadWriteOnce` storage, so
`values-docker-desktop.yaml` pins both core pods to one worker as an explicit local-only compromise.

Core nodes discover one another through Eureka and use ActiveMQ for cross-core WebSocket delivery. Exactly one core has `scheduling`.

## Install

Create a values file with real secrets and a resolvable server URL, then run:

```bash
helm upgrade --install artemis ./helm/artemis \
  --namespace artemis --create-namespace \
  --values my-values.yaml \
  --wait --timeout 20m
```

Required secret values are:

- `artemis.config.admin.password`
- `artemis.config.jwtBase64Secret`
- `postgresql.auth.password`
- `registry.password`
- `broker.auth.password`

Every node here runs under the `prod` profile, which refuses to start on a credential that Artemis publishes as an
example. `artemis.config.admin.password` must therefore not be `artemis_admin`.

Set `gateway.enabled=false` when using only `kubectl port-forward`. For a plain-HTTP local port-forward, also set `artemis.config.secureCookies=false`; the Docker Desktop values file already does this. Otherwise install the Gateway API CRDs/controller and configure the gateway values.

## Docker Desktop acceptance cluster

Use Docker Desktop 4.51 or newer, switch its managed Kubernetes provisioner to `kind`, select three nodes, and use the containerd image store. Then run:

```bash
./run-localci-kubernetes.sh all
```

The script validates the context and topology, labels workers, builds/imports the two local images, installs this chart with
`values-docker-desktop.yaml`, and verifies that both cores register, the build-agent controllers occupy distinct workers, and native build Jobs run on both workers. See
`CLUSTER-SETUP.md` for commands and diagnostics.

## Supported exercise flags

The MVP supports environment variables (`KEY=value`), CPU, memory, and `network=none`. Named Docker networks and custom memory swap are rejected with an
explicit build error. `network=none` is implemented by a short trusted init container with `NET_ADMIN`; the exercise and helper containers do not receive
that capability.

This MVP does not include runtime sandboxes such as gVisor/Kata, production secret management, autoscaling, highly available data services, object storage,
or named network profiles.
