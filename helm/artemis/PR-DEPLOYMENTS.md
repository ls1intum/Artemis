# Per-PR Kubernetes deployments

Each pull request can be deployed to its own throwaway Artemis environment so reviewers can test it live. This is driven
by three GitHub Actions workflows plus a small amount of one-time cluster setup. The chart itself needs **no changes** -
the workflows just install it per PR with per-PR values.

## How it works

- **Trigger**: a PR is deployed once its **CI passes** *and* it has the **`ready for review`** label
  (`.github/workflows/k8s-pr-deployment.yml`). Both orderings are handled - a new green build on a labeled PR, or the
  label added after CI already went green.
- **Isolation**: one namespace per PR, **`artemis-pr-<N>`**, created inside a designated **Rancher project** (the
  `field.cattle.io/projectId` annotation is set at namespace creation so Rancher applies the project's quota/limits).
- **URL**: **`https://pr-<N>.artemis.envoy.stud.k8s.aet.cit.tum.de`**, posted as a sticky PR comment when ready.
- **Updates**: pushing a new commit redeploys the env once that commit's CI passes. The deploy pins the
  **immutable per-commit image** `sha-<headSHA>` (CI builds it alongside the mutable `pr-<N>`), so every push
  actually rolls the pods - pinning `pr-<N>` would render an identical manifest and silently keep the old image. The
  sticky comment shows the **live commit + update time**, and the namespace is annotated
  (`artemis.aet.cit.tum.de/commit`, `.../deployed-at`, `.../pr`) so `kubectl get ns -o yaml` and the Rancher UI are
  authoritative about which version each env runs.
- **Concurrent pushes**: runs are serialized per PR (`cancel-in-progress: false`, so a `helm --wait` is never killed
  mid-upgrade). Because GitHub does not guarantee queued runs execute in commit order, each run re-checks - right before
  deploying - that its SHA is still the PR's current head; a superseded (older) run no-ops, so the env never rolls back to
  an older commit. The same gate also confirms the `sha-<headSHA>` image is actually published before deploying, so a
  green-but-imageless CI can't wedge the env in `ImagePullBackOff` while holding the lock.
- **Networking / TLS**: every PR gets its own Gateway with a **per-host Let's Encrypt cert (HTTP-01)**. Envoy Gateway's
  **`mergeGateways`** collapses all per-PR Gateways onto **one** LoadBalancer IP, so a single **wildcard DNS**
  `*.artemis.envoy.stud.k8s.aet.cit.tum.de` covers every PR. git-over-SSH is disabled for PR envs (`gateway.ssh.mode=none`);
  exercise repos are cloned over HTTPS.
- **Secrets**: all app secrets (admin/JWT/DB/registry/broker/git passwords, result token) are **generated on the first
  deploy and reused on redeploys** (read back from the stored Helm values). They must stay stable - the Postgres PVC is
  initialized with the DB password on first install and ignores later changes, so regenerating it would break the DB
  connection. Only the shared Hades URL + auth key come from repo secrets.
- **Teardown**: the env is removed when the PR is **closed**, the **label is removed**, or the PR goes **stale**
  (`.github/workflows/k8s-pr-teardown.yml`). Stale is automatic - `actions/stale` removes the `ready for review` label,
  firing the `unlabeled` teardown. A nightly **GC** (`.github/workflows/k8s-pr-gc.yml`) prunes any orphaned namespace.

## One-time cluster prerequisites

1. **Merge per-PR Gateways onto one LB** - set `mergeGateways: true` on the shared `EnvoyProxy`:
   ```bash
   kubectl -n envoy-gateway-system patch envoyproxy artemis-envoy-proxy \
     --type merge -p '{"spec":{"mergeGateways":true}}'
   ```
2. **Wildcard DNS** `*.artemis.envoy.stud.k8s.aet.cit.tum.de` → the merged Envoy LoadBalancer IP
   (`kubectl -n envoy-gateway-system get svc`).
3. A **Rancher project** for PR envs with a **generous per-namespace default resource quota** (each PR runs Artemis +
   Postgres + Registry + Broker + adapter; leave ≥100m CPU / 64Mi headroom per namespace for the cert-manager ACME solver
   pod). Note its projectId `<cluster-id>:<project-id>`.
4. cert-manager with Gateway API support enabled and the `envoy` GatewayClass (see [CLUSTER-SETUP.md](./CLUSTER-SETUP.md)).
5. The workflows + chart must be on the **default branch** (they only run from `develop`).

## GitHub secrets / variables

Configure under repo Settings → Secrets and variables → Actions.

| Name | Kind | Purpose |
|------|------|---------|
| `KUBECONFIG` | secret | Cluster auth. **Scope its RBAC to the Rancher project only** - a PR's own image runs arbitrary code in its namespace; the kubeconfig must not reach `kube-system`/cluster-admin. |
| `HADES_AUTH_KEY` | secret | Shared Hades Basic-Auth key (must match the cluster-wide Hades scheduler). |
| `RANCHER_PROJECT_ID` | variable | `<cluster-id>:<project-id>` for the namespace annotation. |
| `HADES_URL` | variable | Shared Hades scheduler URL (e.g. `https://hades.stud.k8s.aet.cit.tum.de`). |
| `PR_DEPLOY_ACME_EMAIL` | variable | ACME contact email for the per-host certs. |
| `PR_DEPLOY_ACME_SERVER` | variable | ACME directory URL - use **staging** while validating / under high churn (see below). |
| `MAX_PR_ENVS` | variable | Cap on concurrent PR environments (capacity guard; `0`/unset = no cap). |

## Limitations

- **Let's Encrypt rate limit is domain-wide.** The 50-certs/week limit keys on the registered domain (`tum.de`), shared by
  every `*.tum.de` cert. High PR churn (each new hostname = a new cert) can exhaust it and block issuance across the whole
  domain. Default `PR_DEPLOY_ACME_SERVER` to LE **staging** (`https://acme-staging-v02.api.letsencrypt.org/directory`);
  switch to prod only when the PR-cert volume is known to be safe. Redeploying the *same* PR reuses its existing cert.
- **Build logs across concurrent PRs.** Fixed once the image carries [#13491](https://github.com/ls1intum/Artemis/pull/13491)
  and the cluster runs Hades with [hades#452](https://github.com/ls1intum/hades/pull/452): Artemis now advertises a
  **per-job log callback URL** (`hades.adapter.logs-endpoint`, wired to each PR's own adapter via
  `hadesAdapter.logsPath`), so build *logs* route per-PR just like build *results*. Older images without #13491 fall back
  to Hades' removed global adapter URL and can only reach one PR's adapter at a time.
- **Image must contain the Hades integration** - only meaningful once `hades` is on `develop` (or the PR branch carries it).
- **`mergeGateways` is GatewayClass-wide** - it merges all `envoy`-class Gateways onto one LB; the existing manual test env's
  IP may change when you enable it.
