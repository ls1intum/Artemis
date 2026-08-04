# Cluster-setup manifests

Ready-to-apply manifests for the cluster-level prerequisites described in
[`../CLUSTER-SETUP.md`](../CLUSTER-SETUP.md). **Edit the `FIXME:` placeholders** (IP ranges, email, namespace, release
name) before applying.

```
cluster-setup/
├── gatewayclass.yaml                 # plain Envoy Gateway GatewayClass
├── clusterissuer-letsencrypt.yaml    # rarely needed - the chart creates its own issuer by default
└── metallb-dualstack/                # dual-stack Envoy LB pinned to a MetalLB pool
    ├── envoyproxy.yaml               # references an EXISTING MetalLB pool by name
    └── gatewayclass.yaml             # GatewayClass wired to the EnvoyProxy
```

> These manifests reference an **existing** MetalLB `IPAddressPool` (by name, in `envoyproxy.yaml`); they do not create
> one. The pool must contain both an IPv4 and an IPv6 range for dual-stack to work.

## What to apply

CRDs and controllers are installed with `helm`/`kubectl` per `../CLUSTER-SETUP.md` (Gateway API experimental CRDs,
Envoy Gateway, cert-manager, an RWX StorageClass). Once those are in place, apply the manifests here.

Pick **one** GatewayClass:

**A. Plain (no MetalLB customization):**

```bash
kubectl apply -f cluster-setup/gatewayclass.yaml
```

**B. MetalLB pool + dual-stack** (applies the EnvoyProxy and a GatewayClass that references it; set the pool name in
`envoyproxy.yaml` first):

```bash
kubectl apply -f cluster-setup/metallb-dualstack/
```

> Requires Envoy Gateway's own CRDs (the `EnvoyProxy` kind). If you installed the controller with `--skip-crds`, apply
> them first - see `../CLUSTER-SETUP.md` section 2 - otherwise this fails with
> `no matches for kind "EnvoyProxy"`.

TLS is normally handled by the chart itself (it creates a Let's Encrypt `ClusterIssuer` by default). You only need
`clusterissuer-letsencrypt.yaml` if you prefer a single shared, manually-managed issuer - see `../CLUSTER-SETUP.md`
section 4. Either way, install cert-manager **with Gateway API support enabled** (`--set config.enableGatewayAPI=true`).

All variants create a GatewayClass named `envoy`, so set `gateway.className=envoy` in your chart values.
