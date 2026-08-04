# Cluster setup guide

This chart assumes a few cluster-level capabilities are already installed and configured. This guide walks through each
one, with concrete install commands, and ends with the `values.yaml` keys that tie the chart to what you set up.

> Version numbers below are examples - check each project for the current release and for its Kubernetes / Gateway API
> compatibility matrix before installing.
>
> Ready-to-apply copies of the manifests in this guide live in [`./cluster-setup/`](./cluster-setup/) - edit the
> `FIXME:` placeholders and `kubectl apply` them instead of copy-pasting.

Cluster prerequisites at a glance:

| Capability | Why the chart needs it | Section |
|------------|------------------------|---------|
| Gateway API CRDs (**experimental channel**) | `HTTPRoute` **and** `TCPRoute` (git-SSH) | [1](#1-gateway-api-crds) |
| A Gateway controller (e.g. **Envoy Gateway**) | Implements the Gateway / routes; provisions the external LB | [2](#2-gateway-controller-envoy-gateway) |
| A **ReadWriteMany** StorageClass | Shared git repos / uploads across all Artemis pods | [3](#3-readwritemany-storage) |
| cert-manager (optional) | Auto-provision the HTTPS certificate | [4](#4-tls-with-cert-manager-optional) |
| DNS → Gateway external IP | Reach the instance over its hostname; SSH on 7921 | [5](#5-dns) |
| External Hades (scheduler + adapter) | Runs the actual builds | [6](#6-external-hades) |

---

## 1. Gateway API CRDs

The chart renders `Gateway`, `HTTPRoute` and (for git-over-SSH) `TCPRoute`. `TCPRoute` (along with `TLSRoute` and
`UDPRoute`, which the controller also watches) lives **only in the Gateway API experimental channel**, so the
standard-channel install is not enough.

**Envoy Gateway v1.8.3 requires Gateway API v1.3.0 (experimental channel).** Install exactly that, and apply it with
`--server-side --force-conflicts` so it works whether or not the cluster already has Gateway API CRDs (see the callout
below):

```bash
kubectl apply --server-side --force-conflicts \
  -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.3.0/experimental-install.yaml
```

Verify the experimental kinds are present and serve `v1`:

```bash
kubectl get crd | grep gateway.networking.k8s.io
# expect: gateways, gatewayclasses, httproutes, tcproutes, tlsroutes, udproutes, ...
kubectl get crd tcproutes.gateway.networking.k8s.io \
  -o jsonpath='{range .spec.versions[*]}{.name}{" "}{end}{"\n"}'
```

> **Cluster already has Gateway API CRDs?** Then let this `kubectl apply --server-side --force-conflicts` be the single
> source and pass `--skip-crds` to the Envoy Gateway install ([section 2](#2-gateway-controller-envoy-gateway)). This
> avoids two failure modes:
> - **Helm CRD step conflicts** - if the existing CRDs were installed with `kubectl apply`, the Envoy Gateway chart's
>   server-side apply refuses to overwrite fields owned by another manager
>   (`conflict occurred while applying object /gatewayclasses...`). `--force-conflicts` on the command above takes
>   ownership and updates them in place.
> - **Controller crash-loop on stale CRDs** - if you `--skip-crds` but the pre-existing CRDs are older than v1.3.0, the
>   controller crash-loops with `no matches for kind "TLSRoute" in version "gateway.networking.k8s.io/v1"` and the
>   Deployment reports `exceeded its progress deadline`. Applying the v1.3.0 experimental CRDs (as above) and then
>   `kubectl -n envoy-gateway-system rollout restart deploy/envoy-gateway` fixes it.
>
> **Do NOT fix a conflict by deleting the CRDs.** Deleting a CRD deletes every custom resource of that kind in the
> cluster (all Gateways, HTTPRoutes, TCPRoutes, ...). Always update in place with `--server-side --force-conflicts`.
>
> If your controller cannot do `TCPRoute`, skip it and use the SSH fallback (`gateway.ssh.mode=loadbalancer` or
> `nodeport`) - then only the standard-channel CRDs are required.

---

## 2. Gateway controller (Envoy Gateway)

You need a controller that reconciles the Gateway API resources and provisions an external load balancer. **Envoy
Gateway** is the recommended choice here because it supports `TCPRoute` (needed for git-over-SSH). Alternatives that also
support `TCPRoute`: **Cilium**, **Istio**, **NGINX Gateway Fabric**.

### Install Envoy Gateway

Passing `--skip-crds` keeps the v1.3.0 Gateway API CRDs from [section 1](#1-gateway-api-crds) as the single source (and
avoids the field-ownership conflict). But `--skip-crds` skips **every** CRD the chart bundles - including **Envoy
Gateway's own CRDs** (`EnvoyProxy`, `SecurityPolicy`, `Backend`, ...). Install those from the chart bundle first,
otherwise `kubectl apply` of the `EnvoyProxy` in the MetalLB / dual-stack step below fails with
`no matches for kind "EnvoyProxy" in version "gateway.envoyproxy.io/v1alpha1"`:

```bash
helm pull oci://docker.io/envoyproxy/gateway-helm --version v1.8.3 --untar

# Envoy Gateway's OWN CRDs (EnvoyProxy et al.). NOT the Gateway API CRDs - those came from section 1.
kubectl apply --server-side --force-conflicts \
  -f gateway-helm/charts/crds/crds/generated/
```

Then install the controller with `--skip-crds`:

```bash
helm install envoy-gateway oci://docker.io/envoyproxy/gateway-helm \
  --version v1.8.3 \
  -n envoy-gateway-system --create-namespace \
  --skip-crds

kubectl -n envoy-gateway-system rollout status deploy/envoy-gateway
```

The same `helm pull` bundle also carries the Gateway API CRDs at
`gateway-helm/charts/crds/crds/gatewayapi-crds.yaml` (v1.3.0) if you prefer applying them from the chart instead of the
upstream URL in section 1.

> If the controller was installed before the correct CRDs and is crash-looping (`no matches for kind "TLSRoute" ...`),
> apply the section-1 CRDs, then restart it: `kubectl -n envoy-gateway-system rollout restart deploy/envoy-gateway`.

### Create a GatewayClass

The chart's `Gateway` references a `GatewayClass` by name (`gateway.className`). Create one that points at the Envoy
Gateway controller:

```yaml
# gatewayclass.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: envoy
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller
```

```bash
kubectl apply -f cluster-setup/gatewayclass.yaml
kubectl get gatewayclass envoy   # ACCEPTED should be True
```

Then set `--set gateway.className=envoy` when installing the chart.

### Optional: MetalLB address pool + dual-stack

If your cluster uses [MetalLB](https://metallb.universe.tf/) and you need the Envoy `LoadBalancer` service to (a) draw
its address from a **specific `IPAddressPool`** and (b) be **dual-stack** (IPv4 + IPv6), customize the service the
controller generates through an **`EnvoyProxy`** resource referenced from the `GatewayClass`.

Prerequisites:

- The cluster is dual-stack (kube-apiserver / kube-proxy configured with both IPv4 and IPv6 `--service-cluster-ip-range`).
- You already have a MetalLB `IPAddressPool` that contains **both** an IPv4 and an IPv6 range, so MetalLB can hand out
  one of each. This guide references that pool by name; it does **not** create one (most clusters already have a pool,
  and adding another risks conflicts). Check what you have with `kubectl -n metallb-system get ipaddresspools`.

Create an `EnvoyProxy` in the Envoy Gateway namespace. It pins the service to your existing MetalLB pool and patches it
to request dual-stack (there is no dedicated field for `ipFamilies`, so use a strategic-merge `patch`):

```yaml
# envoyproxy.yaml
apiVersion: gateway.envoyproxy.io/v1alpha1
kind: EnvoyProxy
metadata:
  name: artemis-envoy-proxy
  namespace: envoy-gateway-system      # must live in the Envoy Gateway namespace
spec:
  provider:
    type: Kubernetes
    kubernetes:
      envoyService:
        annotations:
          # Pin the LB address to one of your EXISTING MetalLB pools.
          metallb.universe.tf/address-pool: <your-metallb-pool>   # FIXME
          # Optional: request specific IPs from that pool (comma-separated, one per family).
          # metallb.universe.tf/loadBalancerIPs: 192.0.2.240,2001:db8:42::1
        patch:
          type: StrategicMerge
          value:
            spec:
              ipFamilyPolicy: RequireDualStack
              ipFamilies:
                - IPv4
                - IPv6
```

Point the `GatewayClass` at it via `parametersRef` (this replaces the plain GatewayClass from the previous step):

```yaml
# gatewayclass.yaml
apiVersion: gateway.networking.k8s.io/v1
kind: GatewayClass
metadata:
  name: envoy
spec:
  controllerName: gateway.envoyproxy.io/gatewayclass-controller
  parametersRef:
    group: gateway.envoyproxy.io
    kind: EnvoyProxy
    name: artemis-envoy-proxy
    namespace: envoy-gateway-system
```

```bash
# Set the pool name in cluster-setup/metallb-dualstack/envoyproxy.yaml first.
# Applies the EnvoyProxy and the GatewayClass wired to it.
kubectl apply -f cluster-setup/metallb-dualstack/
```

Verify the Envoy service picked up the pool and both families after deploying the chart:

```bash
# The Envoy service is created in the Envoy Gateway namespace, named after the owning Gateway.
kubectl -n envoy-gateway-system get svc \
  -l gateway.envoyproxy.io/owning-gateway-namespace=<ns> \
  -o custom-columns='NAME:.metadata.name,IP-FAMILIES:.spec.ipFamilies,EXTERNAL-IP:.status.loadBalancer.ingress[*].ip'
```

You should see both an IPv4 and an IPv6 external address from your pool. Create DNS A **and** AAAA records for
`gateway.hostname` pointing at them (see [section 5](#5-dns)).

> `RequireDualStack` fails service creation if the cluster is not dual-stack; use `PreferDualStack` if you want it to
> fall back to single-stack gracefully. Changing `ipFamilyPolicy`/`ipFamilies` on an existing service is not always
> allowed - delete and recreate the Gateway (and thus its Envoy service) if a change is rejected.

### Enable git-SSH (TCPRoute) end-to-end

The chart's `Gateway` declares a `TCP` listener on port **7921** and a `TCPRoute` that binds to it. Envoy Gateway turns
that listener into a port on the Envoy `LoadBalancer` service automatically - no extra config needed. Just make sure
your cloud/LB provider allows exposing port 7921 (some managed LBs restrict non-standard ports).

If your controller does **not** support `TCPRoute`, set instead:

```yaml
gateway:
  ssh:
    mode: loadbalancer   # or nodeport
```

which creates a dedicated `Service` for port 7921 rather than routing it through the Gateway.

---

## 3. ReadWriteMany storage

All Artemis nodes share the git repositories, uploads and exports, so they mount **one `ReadWriteMany` (RWX) PVC**. Your
cluster must offer an RWX `StorageClass`. Prefer a **performant backend** (CephFS, SSD-backed NFS, ...) - cheap file
storage bottlenecks on file-lock / metadata I/O under concurrent git load.

Managed options: **AWS EFS CSI**, **Azure Files CSI**, **GCP Filestore CSI**, **CephFS (Rook)**, **Longhorn (RWX)**.

### Example: NFS subdir provisioner (self-managed)

If you already have an NFS server, the `nfs-subdir-external-provisioner` gives you a dynamic RWX StorageClass:

```bash
helm repo add nfs-subdir-external-provisioner \
  https://kubernetes-sigs.github.io/nfs-subdir-external-provisioner/

helm install nfs-provisioner nfs-subdir-external-provisioner/nfs-subdir-external-provisioner \
  -n nfs-provisioner --create-namespace \
  --set nfs.server=<NFS_SERVER_IP> \
  --set nfs.path=/exported/path \
  --set storageClass.name=nfs-rwx \
  --set storageClass.accessModes='{ReadWriteMany}'
```

Verify:

```bash
kubectl get storageclass nfs-rwx
```

Then set `--set sharedStorage.storageClassName=nfs-rwx`.

> The root `init-chown` container `chown`s the mounted volume to uid/gid 1337. Make sure your provisioner permits a root
> init container to change ownership (NFS `no_root_squash` or an equivalent). If it does not, pre-create the volume with
> the right ownership or use a provisioner that honours `fsGroup`.

The bundled PostgreSQL uses a normal `ReadWriteOnce` volume from `postgresql.persistence.storageClassName` (or the
cluster default) - it does **not** need RWX.

---

## 4. TLS with cert-manager + Envoy Gateway

The Gateway's HTTPS listener needs a certificate, or it stays un-programmed and the load balancer never comes up. The
chart can drive this end-to-end with cert-manager and Let's Encrypt - **by default it creates a `ClusterIssuer` and adds
an HTTP:80 listener for you** (`gateway.tls.certManagerIssuer.create=true`, `gateway.httpListener=true`). You just have
to make cert-manager Gateway-API-aware and point DNS at the Gateway.

### How the automatic path works

1. The chart renders a `ClusterIssuer` (`<release>-artemis-letsencrypt`) whose ACME **HTTP-01 solver uses the Gateway API**
   (`gatewayHTTPRoute`), and annotates the Gateway with `cert-manager.io/cluster-issuer`.
2. cert-manager's **gateway-shim** sees the annotation + the HTTPS listener's `certificateRefs` and auto-creates a
   `Certificate` for `<release>-artemis-tls`.
3. To solve the challenge it creates a temporary `HTTPRoute` on the Gateway's **HTTP:80 listener** plus a solver pod.
   Let's Encrypt fetches `http://<hostname>/.well-known/acme-challenge/...` → Envoy :80 → solver → validated.
4. The cert is written to `<release>-artemis-tls`, the HTTPS listener programs, and Envoy serves HTTPS.

### Step 1 - install cert-manager (if not already present)

```bash
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace \
  --version v1.21.1 --set crds.enabled=true \
  --set config.enableGatewayAPI=true
```

### Step 2 - enable Gateway API support (the easy-to-miss part)

cert-manager only creates `Certificate`s from Gateways and solves `gatewayHTTPRoute` challenges when **Gateway API
support is enabled**. On cert-manager **v1.15+ (incl. v1.21)** this is the `--set config.enableGatewayAPI=true` Helm value
- **not** the old `--feature-gates=ExperimentalGatewayAPISupport=true` feature gate, which is ineffective on current
versions. If cert-manager is already installed:

```bash
helm upgrade cert-manager jetstack/cert-manager -n cert-manager --reuse-values \
  --set config.enableGatewayAPI=true
```

Verify it took effect (the challenge otherwise fails with `gateway api is not enabled`):

```bash
# cert-manager >=1.15 uses a config file; confirm the setting is present:
kubectl -n cert-manager get cm cert-manager -o jsonpath='{.data}' 2>/dev/null | grep -o enableGatewayAPI
# and that the controller mounts --config=/var/cert-manager/config/config.yaml:
kubectl -n cert-manager get deploy cert-manager -o jsonpath='{.spec.template.spec.containers[0].args}'
```

Enabling it also needs the RBAC for cert-manager to watch Gateways/HTTPRoutes; the Helm chart adds it. If you turned the
flag on by hand-editing args instead of the Helm value, the RBAC (and the config file) may be missing - prefer the Helm
value.

### Step 3 - contact email

Set the ACME registration email (required by Let's Encrypt):

```bash
--set gateway.tls.certManagerIssuer.email=you@example.com
```

While testing, use the **staging** ACME endpoint to avoid the strict production rate limits, then switch to prod:

```bash
--set gateway.tls.certManagerIssuer.server=https://acme-staging-v02.api.letsencrypt.org/directory
```

Then continue with [DNS](#5-dns) (the challenge needs the hostname to resolve to the Envoy LB).

### Alternatives

- **Reference an existing issuer** instead of creating one:
  `--set gateway.tls.certManagerIssuer.create=false --set gateway.tls.certManagerClusterIssuer=<name>`. That issuer must
  also use a `gatewayHTTPRoute` solver (an ingress-nginx HTTP-01 solver won't validate traffic served by Envoy).
- **Bring your own certificate** - skip ACME entirely:
  ```bash
  kubectl -n <ns> create secret tls artemis-tls --cert=fullchain.pem --key=privkey.pem
  # --set gateway.tls.certManagerIssuer.create=false --set gateway.tls.secretName=artemis-tls
  ```

### Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| Challenge stuck, reason `gateway api is not enabled` | Gateway API support not on. Run Step 2 (`config.enableGatewayAPI=true`) and let cert-manager restart. |
| No `Certificate` ever appears for the Gateway | gateway-shim not running (Gateway API support off / missing RBAC), or the Gateway lacks the `cert-manager.io/cluster-issuer` annotation. |
| Solver pod `forbidden: exceeded quota` | The namespace/project `ResourceQuota` is full - the ACME solver pod needs ~100m CPU / 64Mi. Free headroom (fewer/smaller pods) or raise the quota. |
| Challenge `pending`, never validated | DNS for the hostname doesn't resolve to the **Envoy** LB, or the Gateway has no HTTP:80 listener (`gateway.httpListener=true`). |
| `too many certificates already issued` | Let's Encrypt **production** rate limit - use the staging server while iterating. |

---

## 5. DNS

The external IP lives on the Gateway's **data-plane** LoadBalancer, which Envoy Gateway provisions **per Gateway** once
the chart is installed. Don't confuse it with the `envoy-gateway` service in `envoy-gateway-system` - that is the
**control plane** (xDS/admin) and is intentionally `ClusterIP` with no external IP.

```bash
# Easiest - the Gateway's own status address (only populated after the chart is deployed):
kubectl -n <ns> get gateway <release-name>-artemis \
  -o jsonpath='{range .status.addresses[*]}{.value}{"\n"}{end}'

# Or the per-Gateway Envoy data-plane LoadBalancer service:
kubectl -n envoy-gateway-system get svc \
  -l gateway.envoyproxy.io/owning-gateway-namespace=<ns> \
  -o custom-columns='NAME:.metadata.name,IP-FAMILIES:.spec.ipFamilies,EXTERNAL-IP:.status.loadBalancer.ingress[*].ip'
```

Point your `gateway.hostname` DNS record at that address - an **A** record (and an **AAAA** record if you configured
dual-stack, see [section 2](#optional-metallb-address-pool--dual-stack)), or a CNAME to a name that resolves there. Both
HTTPS (443) and git-SSH (7921) are served from the same address when `ssh.mode=tcproute`.

> DNS must resolve **before** cert-manager can issue: the ACME HTTP-01 challenge fetches
> `http://<hostname>/.well-known/acme-challenge/...`, which has to reach the Envoy LB on port 80.

---

## 6. External Hades

The chart does **not** deploy Hades. Before builds can run you need, reachable from the cluster:

- the **Hades scheduler** (Artemis calls `POST {url}/build`, `GET {url}/ping`), and
- the **hades-artemis-adapter**, which must be able to reach back to Artemis' result endpoint
  (`POST .../programming-exercises/new-result`).

Deploy them per the [Hades](https://github.com/ls1intum/hades) and
[hades-artemis-adapter](https://github.com/ls1intum/hades-artemis-adapter) repositories (their own Helm charts / compose
files), then set `artemis.config.hades.url`, `artemis.config.hades.authKey`, and `artemis.config.hades.adapterEndpoint`.

Also remember: the **Artemis image must contain the Hades integration** (the `hades` Spring profile) - see the README
prerequisites.

---

## Putting it together

A minimal values overlay once the cluster pieces above exist:

```yaml
image:
  tag: "pr-1234"                       # a Hades-enabled Artemis image

gateway:
  hostname: pr1234.artemis-k8s.example.com
  className: envoy                     # the GatewayClass from section 2
  ssh:
    mode: tcproute                     # git-SSH via the Gateway (Envoy supports it)
  tls:
    certManagerIssuer:                 # section 4 - chart creates a LE issuer + HTTP-01 gateway solver
      create: true
      email: you@example.com

sharedStorage:
  storageClassName: nfs-rwx            # the RWX class from section 3
  size: 8Gi

artemis:
  replicaCount: 3
  config:
    admin:
      password: <admin-pw>
    jwtBase64Secret: <base64-secret>
    versionControl:
      buildAgentGitPassword: <git-pw>
    hades:                             # section 6
      url: http://hades.hades-system.svc:8081
      authKey: <hades-key>
      adapterEndpoint: http://hades-adapter.hades-system.svc:8083/adapter/test-results

postgresql:
  auth:
    password: <db-pw>
registry:
  password: <registry-pw>
broker:
  auth:
    password: <broker-pw>
```

```bash
helm install artemis ./helm/artemis -n artemis --create-namespace -f values.yaml --wait --timeout 15m
```

---

## Verify the cluster is ready

```bash
# Gateway API experimental CRDs (incl. TCPRoute)
kubectl get crd tcproutes.gateway.networking.k8s.io

# Gateway controller up + GatewayClass accepted
kubectl -n envoy-gateway-system get pods
kubectl get gatewayclass

# RWX StorageClass present
kubectl get storageclass

# (optional) cert-manager up + Gateway API support enabled
kubectl -n cert-manager get pods
kubectl -n cert-manager get cm cert-manager -o jsonpath='{.data}' | grep -o enableGatewayAPI   # want a match
kubectl get clusterissuer   # after install: <release>-artemis-letsencrypt should be Ready
```
