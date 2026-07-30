# Artemis Helm Chart (multi-node, PostgreSQL + Hades)

A Helm chart that deploys a **multi-node [Artemis](https://github.com/ls1intum/Artemis)** instance on Kubernetes,
backed by **PostgreSQL** and the **[Hades](https://github.com/ls1intum/hades)** build system, and exposed through the
**Kubernetes Gateway API** (HTTP(S) + git-over-SSH).

It is designed as the foundation for spinning up a **fresh Artemis instance per pull request**. It deploys only the
components required to run a clustered Artemis; everything else (Hades itself, Keycloak, the AI services, ...) runs
outside the chart.

---

## Architecture

```
                          Gateway API Gateway
               ┌────────────────────┴─────────────────────┐
         HTTPRoute (HTTPS 443 → 8080)            TCPRoute (7921 → 7921)
               │                                           │
       Service: <release>-http (8080)          Service: <release>-ssh (7921)
               └─────────────────┬─────────────────────────┘
                                 ▼
   Artemis core pods  (StatefulSet, N replicas)
   profiles: prod,core,artemis,localvc,hades,docker   (+ scheduling on the leader only)
     ├── leader  (StatefulSet, always 1 replica, runs `scheduling`)
     ├── member  (StatefulSet, N-1 replicas, no `scheduling`)
     ├── Hazelcast (5701) members discovered via Eureka
     ├── shared RWX PVC mounted at /opt/artemis/data
     └── node-local emptyDir at /opt/artemis/local (repos-download, tmp, build-logs)
                                 │
   ┌───────────────┬────────────┴───────────┬────────────────────────────┐
 PostgreSQL   JHipster Registry          ActiveMQ broker         (EXTERNAL) Hades scheduler
 (StatefulSet  (Deployment, Eureka        (Deployment, STOMP      + hades-artemis-adapter
  + PVC)        discovery :8761)           relay :61613)           reachable via configured URLs
```

### Why each component exists

| Component | Deployed? | Why |
|-----------|-----------|-----|
| Artemis core (leader + member) | Yes | The application. Split so exactly one node runs `scheduling` (cron jobs must not double-fire). |
| PostgreSQL | Yes (toggle) | Primary database. Bundled by default; can point at an external DB. |
| JHipster Registry (Eureka) | Yes | **Mandatory** for multi-node. Artemis disables every other Hazelcast joiner and injects TCP-IP cluster members from Eureka metadata. |
| ActiveMQ broker | Yes (toggle) | STOMP relay that distributes WebSocket messages across nodes. Required once `replicaCount > 1`. |
| Gateway + HTTPRoute + TCPRoute | Yes | HTTP(S) ingress and git-over-SSH (port 7921). |
| Hades scheduler + adapter | **No (external)** | Runs builds. Artemis only triggers builds over HTTP and receives results back. |

### What is intentionally NOT included

Keycloak, Iris/Pyris, Athena, Hermes, Weaviate and the rest of the Eduteligence stack, the Hades scheduler/adapter, and
**MySQL** (PostgreSQL only). Configure any of these as external services via the Artemis config if you need them.

---

## Prerequisites

> **New cluster?** See **[CLUSTER-SETUP.md](./CLUSTER-SETUP.md)** for step-by-step instructions to install everything
> below (Gateway API CRDs, a Gateway controller such as Envoy Gateway, an RWX StorageClass, cert-manager, DNS).

- **Kubernetes ≥ 1.29** with the **[Gateway API](https://gateway-api.sigs.k8s.io/) CRDs** installed.
  - `HTTPRoute` is stable. **`TCPRoute` is part of the Gateway API _experimental channel_** and requires a controller
    that supports it (e.g. **Cilium, Istio, Envoy Gateway, NGINX Gateway Fabric**). If your controller does not support
    `TCPRoute`, set `gateway.ssh.mode=loadbalancer` or `nodeport` (see [Git-over-SSH](#git-over-ssh)).
- A **ReadWriteMany (RWX) StorageClass**. All Artemis nodes share the git repositories, so prefer a **performant backend**
  (CephFS, SSD-backed NFS, ...) - cheap cloud file storage bottlenecks on file-lock/metadata I/O under concurrent git load.
- An **Artemis image that contains the Hades integration** (the `hades` Spring profile). A plain `develop` image silently
  ignores the profile and builds will not run. See [Hades](#hades-external).
- A running **Hades scheduler** and **hades-artemis-adapter**, reachable from the cluster; the adapter must be able to
  reach back to Artemis' result-ingestion endpoint.
- Helm 3.8+ (OCI support) / Helm 4.

---

## Quick start

```bash
helm install artemis ./helm/artemis \
  --namespace artemis --create-namespace \
  --set gateway.hostname=artemis.example.com \
  --set gateway.className=cilium \
  --set artemis.config.admin.password='<admin-pw>' \
  --set artemis.config.jwtBase64Secret="$(openssl rand -base64 64 | tr -d '\n')" \
  --set artemis.config.versionControl.buildAgentGitPassword='<git-pw>' \
  --set artemis.config.hades.url='http://hades.hades-system.svc:8081' \
  --set artemis.config.hades.authKey='<hades-key>' \
  --set artemis.config.hades.adapterEndpoint='http://hades-adapter.hades-system.svc:8083/adapter/test-results' \
  --set postgresql.auth.password='<db-pw>' \
  --set registry.password='<registry-pw>' \
  --set broker.auth.password='<broker-pw>' \
  --set image.tag='pr-1234' \
  --wait --timeout 15m
```

Prefer a values file for anything beyond a quick test:

```bash
helm install artemis ./helm/artemis -n artemis --create-namespace -f my-values.yaml --wait --timeout 15m
```

First startup takes several minutes. Watch it:

```bash
kubectl -n artemis get pods -w
```

Upgrade / uninstall:

```bash
helm upgrade artemis ./helm/artemis -n artemis -f my-values.yaml --wait --timeout 15m
helm uninstall artemis -n artemis
# PVCs (shared data + postgres) are retained by design; delete them manually if desired:
kubectl -n artemis delete pvc -l app.kubernetes.io/instance=artemis
```

---

## Scaling (`replicaCount`)

`artemis.replicaCount` controls the total number of Artemis core nodes:

- `1` → the **leader** only.
- `N` → **leader (1)** + **members (N-1)**.

The leader always exists and is the **only** node with the `scheduling` profile, so scheduled jobs run exactly once
regardless of scale. Member pods wait for the leader to become ready before starting (so concurrent Liquibase migrations
on a fresh database don't race on the `artemis_version` primary key).

```bash
helm upgrade artemis ./helm/artemis -n artemis -f my-values.yaml --set artemis.replicaCount=3
```

---

## Hades (external)

Hades runs **outside** this chart. Artemis is configured with the `hades` + `localvc` profiles and points at your Hades
deployment via:

| Value | Maps to | Purpose |
|-------|---------|---------|
| `artemis.config.hades.url` | `artemis.continuous-integration.url` | Hades scheduler base URL (`POST /build`, `GET /ping`). |
| `artemis.config.hades.authKey` | `...hades.auth-key` | Basic-Auth password Artemis sends (username `hades`). |
| `artemis.config.hades.adapterEndpoint` | `...hades.adapter.endpoint` | Where the adapter is reachable from build containers; it forwards results back to Artemis. |
| `artemis.config.hades.artemisAuthenticationTokenValue` | `...artemis-authentication-token-value` | Token the adapter presents on Artemis' `new-result` callback. |
| `artemis.config.hades.cloneImage` / `resultParserImage` | `...hades.images.*` | Public Hades pipeline images (defaults are fine). |

Even with Hades, LocalVC still hosts the exercise repositories, so `artemis.config.versionControl.buildAgentGitUsername`
/ `buildAgentGitPassword` must be set - Hades uses them to clone. See `documentation/docs/admin/hades-setup.mdx`.

---

## Git-over-SSH

Git-over-SSH (port 7921) is load-balanced across all core pods, so every pod presents the **same host key** (shipped as a
Secret, generated once and preserved across upgrades) to avoid host-key mismatches.

`gateway.ssh.mode` selects how it is exposed:

| Mode | Behaviour |
|------|-----------|
| `tcproute` (default) | Gateway API `TCPRoute` on the Gateway's TCP/7921 listener. Needs an L4-capable controller. |
| `loadbalancer` | A dedicated `Service` of type `LoadBalancer` on 7921 (annotate via `gateway.ssh.loadBalancerAnnotations`). |
| `nodeport` | A dedicated `Service` of type `NodePort` on 7921 (`gateway.ssh.nodePort`). |
| `none` | SSH not exposed - HTTPS clone only. |

---

## TLS

Set `gateway.tls.secretName` to a pre-created `kubernetes.io/tls` Secret, **or** set
`gateway.tls.certManagerClusterIssuer` to have [cert-manager](https://cert-manager.io/) provision the certificate for the
HTTPS listener automatically.

---

## Using an existing Gateway

Set `gateway.create=false` and point the routes at an existing Gateway:

```yaml
gateway:
  create: false
  hostname: artemis.example.com
  parentRef:
    name: shared-gateway
    namespace: infra
    httpsSectionName: https   # optional: the HTTPS listener section
    sshSectionName: ssh       # optional: the TCP/7921 listener section
```

The existing Gateway must expose an HTTPS listener for the hostname and (for `ssh.mode=tcproute`) a TCP/7921 listener.

---

## Values reference

### Image

| Key | Default | Description |
|-----|---------|-------------|
| `image.repository` | `ghcr.io/ls1intum/artemis` | Artemis image repository. |
| `image.tag` | `""` (→ `Chart.appVersion`) | Image tag. **Must contain the Hades integration.** Use the PR tag for per-PR deploys. |
| `image.pullPolicy` | `IfNotPresent` | |
| `imagePullSecrets` | `[]` | Optional pull secrets. |

### Artemis core (`artemis.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `artemis.replicaCount` | `1` | Total core nodes (leader + members). |
| `artemis.extraProfiles` | `[]` | Extra Spring profiles appended to every node. |
| `artemis.resources` | 1-2 CPU / 2.5-5 Gi | Pod resource requests/limits. |
| `artemis.probes.*` | see `values.yaml` | Startup/readiness/liveness probe tuning. |
| `artemis.podSecurityContext` | `fsGroup: 1337` | Pod security context (image runs as uid/gid 1337). |
| `artemis.containerSecurityContext` | non-root | Container security context. |
| `artemis.podAnnotations` | Prometheus scrape | Pod annotations. |
| `artemis.nodeSelector` / `tolerations` / `affinity` | `{}` / `[]` / `{}` | Scheduling controls. |
| `artemis.config.serverUrl` | `""` (→ `https://<gateway.hostname>`) | Public base URL. |
| `artemis.config.operator.name` / `adminName` | example values | Imprint operator info. |
| `artemis.config.admin.username` / `password` | `artemis_admin` / **required** | Initial internal admin. |
| `artemis.config.jwtBase64Secret` | **required** | Base64 JWT signing secret (shared with the registry). |
| `artemis.config.versionControl.buildAgentGitUsername` / `buildAgentGitPassword` | `buildjob_user` / **required** | LocalVC git creds (Hades clones with these). |
| `artemis.config.hades.*` | see [Hades](#hades-external) | External Hades wiring. |

### Shared storage (`sharedStorage.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `sharedStorage.existingClaim` | `""` | Reuse an existing RWX PVC instead of creating one. |
| `sharedStorage.storageClassName` | `""` | RWX StorageClass (empty = cluster default). |
| `sharedStorage.accessMode` | `ReadWriteMany` | Must be RWX for multi-node. |
| `sharedStorage.size` | `8Gi` | Size of the shared data volume. |

### PostgreSQL (`postgresql.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `postgresql.deploy` | `true` | Deploy bundled PostgreSQL. `false` → use `postgresql.external.*`. |
| `postgresql.image.*` | `postgres:18.4-alpine` | Image. |
| `postgresql.auth.database` / `username` / `password` | `Artemis` / `Artemis` / **required** | DB name / user / password. |
| `postgresql.maxConnections` | `10000` | `max_connections` for CI load. |
| `postgresql.persistence.*` | RWO 10Gi | Postgres PVC. |
| `postgresql.resources` | see `values.yaml` | Postgres resources. |
| `postgresql.external.host` / `port` / `sslmode` | `""` / `5432` / `disable` | External DB (when `deploy=false`). |

### Registry (`registry.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `registry.image.*` | `jhipster/jhipster-registry:v7.5.0` | Image. |
| `registry.password` | **required** | Registry admin password (embedded in the Eureka URL). |
| `registry.service.port` | `8761` | Eureka port. |
| `registry.resources` | see `values.yaml` | Registry resources. |

### Broker (`broker.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `broker.deploy` | `true` | Deploy bundled ActiveMQ. `false` → use `broker.externalAddresses`. |
| `broker.image.*` | `apache/artemis:2.54.0-alpine` | Image. |
| `broker.auth.username` / `password` | `guest` / **required** | Broker credentials. |
| `broker.stompPort` | `61613` | STOMP acceptor port. |
| `broker.externalAddresses` | `""` | External STOMP address list (when `deploy=false`). |
| `broker.resources` | see `values.yaml` | Broker resources. |

### Gateway (`gateway.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `gateway.hostname` | **required** | Public hostname (drives HTTPRoute + `serverUrl`). |
| `gateway.create` | `true` | Create a Gateway. `false` → attach to `gateway.parentRef`. |
| `gateway.className` | `""` | `gatewayClassName` (required when `create=true`). |
| `gateway.parentRef.*` | `""` | Existing Gateway to attach to (when `create=false`). |
| `gateway.tls.secretName` | `""` | Existing TLS Secret for HTTPS. |
| `gateway.tls.certManagerClusterIssuer` | `""` | cert-manager ClusterIssuer to auto-provision TLS. |
| `gateway.ssh.mode` | `tcproute` | `tcproute` / `loadbalancer` / `nodeport` / `none`. |
| `gateway.ssh.port` | `7921` | git-SSH port. |
| `gateway.ssh.nodePort` | `""` | NodePort (when `mode=nodeport`). |
| `gateway.ssh.loadBalancerAnnotations` | `{}` | LB annotations (when `mode=loadbalancer`). |

### Service account (`serviceAccount.*`)

| Key | Default | Description |
|-----|---------|-------------|
| `serviceAccount.create` | `true` | Create a ServiceAccount. |
| `serviceAccount.name` | `""` | Name (defaults to the release fullname). |
| `serviceAccount.annotations` | `{}` | Annotations. |

Required values (no default; `helm install` fails fast without them): `artemis.config.admin.password`,
`artemis.config.jwtBase64Secret`, `artemis.config.versionControl.buildAgentGitPassword`, `artemis.config.hades.url`,
`artemis.config.hades.authKey`, `artemis.config.hades.adapterEndpoint`, `postgresql.auth.password`, `registry.password`,
`broker.auth.password`, `gateway.hostname`, and `gateway.className` (when `gateway.create=true`).

---

## Troubleshooting

- **Pods stuck in `Init`** - the wait-deps init container blocks until PostgreSQL and the JHipster Registry are reachable;
  member pods additionally wait for the leader's readiness. Check `kubectl logs <pod> -c init-wait-deps` /
  `-c init-wait-leader`.
- **Slow startup** - Artemis boots in several minutes; the startup probe allows ~10 min by default
  (`artemis.probes.startup`).
- **Cluster shows fewer members than expected** - Hazelcast discovery relies on Eureka; confirm the registry is healthy
  and pods registered (admin metrics page). Pod-IP churn during rolling updates is expected briefly; Eureka lease timers
  are lowered so dead pods are evicted quickly.
- **`PermissionDenied` writing to `/opt/artemis/data`** - the root `init-chown` container fixes ownership for uid 1337;
  ensure your RWX provisioner allows a root init container to `chown`.
- **Git SSH host-key mismatch** - all pods share one host key via the `<release>-ssh-hostkey` Secret; it is preserved
  across upgrades. A fresh key is generated only on a fresh install.
- **Builds never complete** - verify the image contains the Hades integration, that `artemis.config.hades.url` is
  reachable from the cluster, and that the adapter can reach Artemis' `new-result` endpoint.

---

## Validating the chart

```bash
helm lint helm/artemis -f my-values.yaml
helm template artemis helm/artemis -f my-values.yaml | \
  kubeconform -strict -ignore-missing-schemas \
    -schema-location default \
    -schema-location 'https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'
```
