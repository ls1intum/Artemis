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

The chart renders `Gateway`, `HTTPRoute` and (for git-over-SSH) `TCPRoute`. `TCPRoute` lives in the Gateway API
**experimental channel**, so the standard-channel CRDs are not enough.

```bash
# Installs BOTH standard (HTTPRoute, Gateway, GatewayClass) and experimental (TCPRoute, ...) CRDs.
kubectl apply -f https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/experimental-install.yaml
```

Verify:

```bash
kubectl get crd | grep gateway.networking.k8s.io
# expect: gateways, gatewayclasses, httproutes, tcproutes, ...
```

> Some controllers (Envoy Gateway, Cilium, Istio) bundle their own copy of the Gateway API CRDs. To avoid version
> conflicts, either install the CRDs first (as above) and tell the controller to skip them - for Envoy Gateway pass
> `--skip-crds` to its `helm install` (see [section 2](#2-gateway-controller-envoy-gateway)) - or rely on the
> controller's bundled set and make sure it includes the **experimental** channel (TCPRoute). Only one source of these
> CRDs should win.
>
> If your controller cannot do `TCPRoute`, skip it and use the SSH fallback (`gateway.ssh.mode=loadbalancer` or
> `nodeport`) - then only the standard-channel CRDs are required.

---

## 2. Gateway controller (Envoy Gateway)

You need a controller that reconciles the Gateway API resources and provisions an external load balancer. **Envoy
Gateway** is the recommended choice here because it supports `TCPRoute` (needed for git-over-SSH). Alternatives that also
support `TCPRoute`: **Cilium**, **Istio**, **NGINX Gateway Fabric**.

### Install Envoy Gateway

`--skip-crds` tells Envoy Gateway **not** to install its bundled (standard-channel) Gateway API CRDs, so the
experimental-channel CRDs from [section 1](#1-gateway-api-crds) (which include `TCPRoute`) remain the single source:

```bash
helm install envoy-gateway oci://docker.io/envoyproxy/gateway-helm \
  --version v1.8.3 \
  -n envoy-gateway-system --create-namespace \
  --skip-crds

kubectl -n envoy-gateway-system rollout status deploy/envoy-gateway
```

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

## 4. TLS with cert-manager (optional)

The Gateway's HTTPS listener needs a certificate. Two options:

**A. Bring your own** - create a `kubernetes.io/tls` Secret and reference it:

```bash
kubectl -n <ns> create secret tls artemis-tls --cert=fullchain.pem --key=privkey.pem
# then: --set gateway.tls.secretName=artemis-tls
```

**B. Let cert-manager issue it** - install cert-manager and a ClusterIssuer, then let the chart annotate the Gateway:

```bash
helm repo add jetstack https://charts.jetstack.io
helm install cert-manager jetstack/cert-manager \
  -n cert-manager --create-namespace \
  --version v1.16.2 --set crds.enabled=true
```

```yaml
# clusterissuer.yaml (HTTP-01 solver via the Gateway API)
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@example.com
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          gatewayHTTPRoute:
            parentRefs:
              - name: <release-name>-artemis   # the chart's Gateway
                namespace: <ns>
                kind: Gateway
```

```bash
kubectl apply -f cluster-setup/clusterissuer-letsencrypt.yaml
# then: --set gateway.tls.certManagerClusterIssuer=letsencrypt-prod
```

cert-manager writes the certificate into `gateway.tls.secretName` (defaults to `<release>-artemis-tls`), which the
Gateway's HTTPS listener references.

---

## 5. DNS

Find the external address the controller provisioned for the Gateway:

```bash
kubectl -n <ns> get gateway <release-name>-artemis \
  -o jsonpath='{.status.addresses[0].value}{"\n"}'
# or the controller's LB service:
kubectl -n envoy-gateway-system get svc -l gateway.envoyproxy.io/owning-gateway-namespace=<ns>
```

Point your `gateway.hostname` DNS record (A/AAAA or CNAME) at that address. Both HTTPS (443) and git-SSH (7921) are
served from the same address when using `ssh.mode=tcproute`.

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
    certManagerClusterIssuer: letsencrypt-prod   # section 4

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

# (optional) cert-manager up + issuer ready
kubectl -n cert-manager get pods
kubectl get clusterissuer
```
