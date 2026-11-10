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
helm upgrade --install artemis ./deployment/helm/artemis \
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

The installation metadata is required as well. The shared ConfigMap supplies it to every pod; core pods
refuse to start without meaningful values even when telemetry is disabled, unless they run as a test server
(`INFO_TESTSERVER=true`), and all three are shown on the About page. The chart refuses to render while one is empty:

- `artemis.config.operator.name`: the organization operating Artemis
- `artemis.config.operator.adminName`: the administrator's name
- `artemis.config.operator.universityName`: the university, school or institution using Artemis

Set `gateway.enabled=false` when using only `kubectl port-forward`. For a plain-HTTP local port-forward, also set `artemis.config.secureCookies=false`; the Docker Desktop values file already does this. Otherwise install the Gateway API CRDs/controller and configure the gateway values.

## Docker Desktop acceptance cluster

Use Docker Desktop 4.51 or newer, switch its managed Kubernetes provisioner to `kind`, select three nodes, and use the containerd image store. Then run:

```bash
./deployment/kubernetes/run-localci-kubernetes.sh all
```

The script validates the context and topology, labels workers, builds/imports the two local images, installs this chart with
`values-docker-desktop.yaml`, and verifies that both cores register, the build-agent controllers occupy distinct workers, and native build Jobs run on both workers. See
`CLUSTER-SETUP.md` for commands and diagnostics.

## Supported exercise flags

The MVP supports environment variables (`KEY=value`), CPU, memory, and `network=none`. Named Docker networks and custom memory swap are rejected with an
explicit build error. `network=none` is implemented by a short trusted init container with `NET_ADMIN`; the exercise and helper containers do not receive
that capability.

This MVP does not include runtime sandboxes such as gVisor/Kata, production secret management, highly available data services, object storage,
or named network profiles. Autoscaling of the build-agent controllers is optional, see below.

## Autoscaling the build agents

By default the number of build-agent controllers is `buildAgents.replicaCount`. With `buildAgents.autoscaling.enabled=true` the chart creates a
[KEDA](https://keda.sh) `ScaledObject` that scales the StatefulSet with the build queue, and stops setting `replicas` itself, so that `helm upgrade` does not reset the count.

Prerequisites: KEDA in the cluster, and a Prometheus that scrapes the core pods. They carry the `prometheus.io/*` annotations. The chart installs neither.

- **Scope the query.** `buildAgents.autoscaling.prometheus.selector` selects the metrics of this installation with label matchers. Without it, a Prometheus that scrapes several Artemis installations would
  scale these controllers with the backlog of another one. The chart refuses to render without it, unless you set your own `query`. The available labels depend on the scrape configuration of your Prometheus.
- **Allow the scrape.** `/management/prometheus` only answers requests from `spring.prometheus.monitoringIp`, which is `127.0.0.1` by default and is not changed by the chart. Set it through
  `artemis.extraEnv` to the source addresses of Prometheus, comma-separated, as single addresses or CIDR blocks such as `10.42.0.0/16`. Use a block that covers the pods of Prometheus, because
  the address of a pod changes when it is rescheduled. Otherwise the scrape is denied and KEDA has no metrics.

```yaml
artemis:
  extraEnv:
    - name: SPRING_PROMETHEUS_MONITORINGIP
      value: "10.42.0.0/16"
buildAgents:
  concurrentBuildsPerAgent: 2
  autoscaling:
    enabled: true
    minReplicas: 2
    maxReplicas: 10
    prometheus:
      serverAddress: http://prometheus-server.monitoring.svc:80
      selector: namespace="artemis"
```

The target is `ceil((queued + running builds) / concurrentBuildsPerAgent)`, read from the cluster-wide gauges `artemis.global.localci.queued` and `running`, limited to
`minReplicas..maxReplicas`. A missing series is an error and not zero, so a broken scrape does not scale the agents down while builds wait.
Scale-up is fast (4 controllers per minute by default) and scale-down is slow (one controller per five minutes after ten minutes below the target).

Scaling down removes the controller with the highest ordinal, busy or not. A build that it was running is put back into the queue and runs again on another agent, at most five times,
and is marked failed after that. The Kubernetes Job of the removed controller is **not** stopped: the other controllers only clean up the Jobs of their own name, so it runs until it finishes
or reaches its active deadline (the build timeout plus the pod start, result collection and grace budgets). A retry can therefore run next to it and use extra build capacity for that time.
A controller that starts again under the same name removes the Jobs it no longer tracks.
Make sure the build nodes can hold `maxReplicas × concurrentBuildsPerAgent` builds, otherwise the extra build Pods wait for resources.

**Enabling autoscaling on an existing release.** The StatefulSet stops setting `replicas`, and Helm can then remove the field, which makes Kubernetes reset the StatefulSet to one replica until KEDA
has reconciled it. The controllers above the first one are removed, and their running builds are interrupted. Enable autoscaling while no builds run: pause the build agents in Artemis and wait until
their builds have finished, as in the update procedure of the documentation, and set `minReplicas` to the capacity you want to keep.

**Paused controllers.** A paused controller counts as a replica but takes no builds, and the scaler adds no capacity for it. Pausing is a maintenance action, and a new controller would start unpaused
and override it. Builds queued while all controllers are paused can start on a new controller without a resume action: when the queue raises the target above the current replica count,
KEDA starts a controller, and it starts unpaused.
All values are documented in `values.yaml`.

## Checking the chart

`supporting_scripts/check_helm_chart.sh` lints the chart, renders it in the variants that matter (autoscaling off and on, scaling from zero, a missing autoscaling map, no build agents, the Gateway API
resources), checks what comes out, and checks that a configuration that cannot work is refused with a message that names the value. With [kubeconform](https://github.com/yannh/kubeconform) installed, it also
validates the objects against the Kubernetes schemas and, for KEDA and the Gateway API, against the schemas of the [CRDs catalog](https://github.com/datreeio/CRDs-catalog). It needs no cluster. The CI runs it when
something under `deployment/helm` changes. The values it renders with are in `ci/`, which is not part of a release of the chart.
