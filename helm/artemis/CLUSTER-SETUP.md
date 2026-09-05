# Cluster setup and validation

## Docker Desktop

The local acceptance target is Docker Desktop's managed Kubernetes cluster, not a separately installed `kind` binary.

1. Open Docker Desktop settings.
2. Enable the containerd image store.
3. Under Kubernetes, choose the `kind` provisioner and configure three nodes.
4. Apply/recreate the cluster and wait until it is running.
5. Select the `docker-desktop` context:

   ```bash
   kubectl config use-context docker-desktop
   kubectl get nodes
   ```

The expected topology is one control-plane and two schedulable workers. The helper script refuses a one-node or `kubeadm` Docker Desktop cluster because that
would not exercise multi-node placement.

Run all local steps:

```bash
./run-localci-kubernetes.sh all
```

Individual commands are available:

```bash
./run-localci-kubernetes.sh build
./run-localci-kubernetes.sh up
./run-localci-kubernetes.sh status
./run-localci-kubernetes.sh test
./run-localci-kubernetes.sh logs
./run-localci-kubernetes.sh down
```

`up --skip-build` reuses existing images. `test --filter <playwright-filter>` forwards an optional Playwright filter. `all --keep` leaves the installation and
port-forward running after validation. `down` removes the release, both namespaces, and the Artemis-specific worker labels while retaining the local images.

## Scheduling labels

The local script labels one worker for both core pods and both workers for controllers/workloads:

```text
artemis.cit.tum.de/core=true
artemis.cit.tum.de/build-worker=true
```

The core pin is only for Docker Desktop's `ReadWriteOnce` volume. Do not copy it into a real cluster; provide `ReadWriteMany` storage instead.

## Verification

Useful checks after installation:

```bash
kubectl -n artemis get pods -o wide
kubectl -n artemis-builds get jobs,pods -w
kubectl auth can-i create jobs.batch \
  --as system:serviceaccount:artemis:artemis-localci-controller \
  -n artemis-builds
kubectl auth can-i list secrets \
  --as system:serviceaccount:artemis-builds:artemis-localci-workload \
  -n artemis-builds
```

The first authorization check should return `yes`; the workload check should return `no`.

The chart exposes `artemis-http` as a ClusterIP. For local access:

```bash
kubectl -n artemis port-forward service/artemis-http 8080:8080
```

## Real clusters

- Use an RWX storage class for `sharedStorage`.
- Use a managed PostgreSQL/broker if availability is required.
- Configure a public `artemis.config.serverUrl` that is also resolvable from build-agent pods.
- Publish both the Artemis image and trusted helper image to a registry and configure pull secrets.
- Keep the controller and workload service accounts separate.
- Add network policy, admission controls, quotas, runtime isolation, TLS, and production secret management according to local requirements.
