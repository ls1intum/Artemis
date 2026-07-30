# Cluster-setup manifests

Ready-to-apply manifests for the cluster-level prerequisites described in
[`../CLUSTER-SETUP.md`](../CLUSTER-SETUP.md). **Edit the `FIXME:` placeholders** (IP ranges, email, namespace, release
name) before applying.

```
cluster-setup/
├── gatewayclass.yaml                 # plain Envoy Gateway GatewayClass
├── clusterissuer-letsencrypt.yaml    # optional: cert-manager Let's Encrypt issuer
└── metallb-dualstack/                # MetalLB pool + dual-stack Envoy LB
    ├── ipaddresspool.yaml
    ├── envoyproxy.yaml
    └── gatewayclass.yaml             # GatewayClass wired to the EnvoyProxy
```

## What to apply

CRDs and controllers are installed with `helm`/`kubectl` per `../CLUSTER-SETUP.md` (Gateway API experimental CRDs,
Envoy Gateway, cert-manager, an RWX StorageClass). Once those are in place, apply the manifests here.

Pick **one** GatewayClass:

**A. Plain (no MetalLB customization):**

```bash
kubectl apply -f cluster-setup/gatewayclass.yaml
```

**B. MetalLB pool + dual-stack** (applies the pool, the EnvoyProxy, and a GatewayClass that references it):

```bash
kubectl apply -f cluster-setup/metallb-dualstack/
```

Optional TLS (after cert-manager is installed):

```bash
kubectl apply -f cluster-setup/clusterissuer-letsencrypt.yaml
```

All variants create a GatewayClass named `envoy`, so set `gateway.className=envoy` in your chart values.
