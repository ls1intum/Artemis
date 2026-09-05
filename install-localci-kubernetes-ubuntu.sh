#!/usr/bin/env bash

# Installs Artemis with the Kubernetes LocalCI build runner on a single-node k3s cluster.
#
# Run this INSIDE an Ubuntu 24.04 machine - a VM, a cloud instance, or bare metal. It installs k3s and Helm if they are
# missing, loads the two Artemis images, and installs the chart in this repository with a single-node profile.
#
# The Docker Desktop script (run-localci-kubernetes.sh) is a three-node acceptance harness and refuses any other
# cluster. This one targets the opposite case: one machine you own, no Gateway API, no shared storage.
#
# See documentation/docs/admin/production-setup/build-runners.mdx for the walkthrough.

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
readonly RELEASE_NAME="artemis"
readonly ARTEMIS_NAMESPACE="artemis"
readonly BUILD_NAMESPACE="artemis-builds"
readonly STATE_DIRECTORY="${LOCALCI_UBUNTU_STATE_DIR:-$HOME/.artemis-localci-kubernetes}"
readonly VALUES_FILE="${STATE_DIRECTORY}/values.yaml"
readonly CREDENTIALS_FILE="${STATE_DIRECTORY}/credentials.env"

readonly MINIMUM_CPUS=6
readonly MINIMUM_MEMORY_GIB=12
readonly MINIMUM_DISK_GIB=40

COMMAND="${1:-install}"
if [[ $# -gt 0 ]]; then
    shift
fi

APPLICATION_IMAGE="artemis-localci-app:local"
HELPER_IMAGE="artemis-localci-helper:local"
IMAGE_ARCHIVE_DIRECTORY=""
NODE_PORT="30080"
CONCURRENT_BUILDS="2"
SKIP_PREFLIGHT=false

usage() {
    printf '%s\n' "Usage: ./install-localci-kubernetes-ubuntu.sh <command> [options]"
    printf '%s\n' ""
    printf '%s\n' "Commands:"
    printf '%s\n' "  install     Install k3s if needed, load the images, and install the chart (default)"
    printf '%s\n' "  status      Show the cluster, the Artemis pods, and the build workloads"
    printf '%s\n' "  logs        Show recent Artemis, build-agent, and build workload logs"
    printf '%s\n' "  uninstall   Remove the release and both namespaces, leaving k3s in place"
    printf '%s\n' ""
    printf '%s\n' "Options:"
    printf '%s\n' "  --image-archive-dir <dir>  Directory holding artemis-app.tar and artemis-helper.tar, produced on a"
    printf '%s\n' "                             build machine with 'docker save'. Use this when there is no registry."
    printf '%s\n' "  --image <ref>              Artemis image reference (default: ${APPLICATION_IMAGE})"
    printf '%s\n' "  --helper-image <ref>       Trusted helper image reference (default: ${HELPER_IMAGE})"
    printf '%s\n' "  --node-port <port>         Port Artemis is served on (default: ${NODE_PORT})"
    printf '%s\n' "  --concurrent-builds <n>    Builds the single agent runs at once (default: ${CONCURRENT_BUILDS})"
    printf '%s\n' "  --skip-preflight           Install even if the machine is below the recommended size"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --image-archive-dir)
            [[ $# -ge 2 ]] || { printf '%s\n' "--image-archive-dir requires a directory" >&2; exit 2; }
            IMAGE_ARCHIVE_DIRECTORY="$2"
            shift
            ;;
        --image)
            [[ $# -ge 2 ]] || { printf '%s\n' "--image requires an image reference" >&2; exit 2; }
            APPLICATION_IMAGE="$2"
            shift
            ;;
        --helper-image)
            [[ $# -ge 2 ]] || { printf '%s\n' "--helper-image requires an image reference" >&2; exit 2; }
            HELPER_IMAGE="$2"
            shift
            ;;
        --node-port)
            [[ $# -ge 2 ]] || { printf '%s\n' "--node-port requires a port" >&2; exit 2; }
            NODE_PORT="$2"
            shift
            ;;
        --concurrent-builds)
            [[ $# -ge 2 ]] || { printf '%s\n' "--concurrent-builds requires a number" >&2; exit 2; }
            CONCURRENT_BUILDS="$2"
            shift
            ;;
        --skip-preflight)
            SKIP_PREFLIGHT=true
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            printf 'Unknown option: %s\n' "$1" >&2
            usage >&2
            exit 2
            ;;
    esac
    shift
done

log() {
    printf '[artemis-k8s] %s\n' "$*"
}

fail() {
    printf '[artemis-k8s] ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found"
}

# k3s writes a root-owned kubeconfig, so every kubectl and helm call goes through sudo with it pointed at that file
# rather than asking the operator to copy it into their home directory first.
kube() {
    sudo env KUBECONFIG=/etc/rancher/k3s/k3s.yaml "$@"
}

check_preflight() {
    local os_id os_version cpus memory_gib disk_gib
    os_id="$(. /etc/os-release && printf '%s' "$ID")"
    os_version="$(. /etc/os-release && printf '%s' "$VERSION_ID")"
    if [[ "$os_id" != "ubuntu" || "$os_version" != "24.04" ]]; then
        log "WARNING: this script is tested on Ubuntu 24.04, found ${os_id} ${os_version}"
    fi

    cpus="$(nproc)"
    memory_gib=$(( $(awk '/MemTotal/ {print $2}' /proc/meminfo) / 1024 / 1024 ))
    disk_gib="$(df -BG --output=avail / | tail -1 | tr -dc '0-9')"

    if [[ "$SKIP_PREFLIGHT" == true ]]; then
        log "Skipping the size check on request (${cpus} CPUs, ${memory_gib} GiB memory, ${disk_gib} GiB free disk)"
        return
    fi
    (( cpus >= MINIMUM_CPUS )) || fail "At least ${MINIMUM_CPUS} CPUs are needed; found ${cpus}. Pass --skip-preflight to install anyway."
    (( memory_gib >= MINIMUM_MEMORY_GIB )) || fail "At least ${MINIMUM_MEMORY_GIB} GiB of memory are needed; found ${memory_gib} GiB. Pass --skip-preflight to install anyway."
    (( disk_gib >= MINIMUM_DISK_GIB )) || fail "At least ${MINIMUM_DISK_GIB} GiB of free disk are needed; found ${disk_gib} GiB. Pass --skip-preflight to install anyway."
    log "Validated the machine: ${cpus} CPUs, ${memory_gib} GiB memory, ${disk_gib} GiB free disk"
}

install_k3s() {
    if command -v k3s >/dev/null 2>&1; then
        log "k3s is already installed"
    else
        require_command curl
        # Traefik is disabled because this install is reached through a NodePort, and an unused ingress controller
        # would only take memory away from the builds.
        log "Installing k3s"
        curl -sfL https://get.k3s.io | sudo sh -s - --disable=traefik --write-kubeconfig-mode=0600
    fi

    local attempt
    for attempt in $(seq 1 60); do
        if kube kubectl get nodes >/dev/null 2>&1; then
            break
        fi
        if (( attempt == 60 )); then
            fail "k3s did not become reachable; check 'sudo systemctl status k3s'"
        fi
        sleep 2
    done
    kube kubectl wait --for=condition=Ready node --all --timeout=180s >/dev/null
    log "k3s is ready: $(kube kubectl get nodes --no-headers | awk '{print $1, $5}')"
}

install_helm() {
    if command -v helm >/dev/null 2>&1; then
        log "Helm is already installed"
        return
    fi
    require_command curl
    log "Installing Helm"
    curl -sfL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | sudo bash >/dev/null
}

load_images() {
    if [[ -z "$IMAGE_ARCHIVE_DIRECTORY" ]]; then
        log "No image archive directory given; the cluster will pull ${APPLICATION_IMAGE} and ${HELPER_IMAGE} from their registry"
        return
    fi
    local application_archive="${IMAGE_ARCHIVE_DIRECTORY}/artemis-app.tar"
    local helper_archive="${IMAGE_ARCHIVE_DIRECTORY}/artemis-helper.tar"
    [[ -f "$application_archive" ]] || fail "Missing ${application_archive}. Create it on a build machine with: docker save ${APPLICATION_IMAGE} -o artemis-app.tar"
    [[ -f "$helper_archive" ]] || fail "Missing ${helper_archive}. Create it on a build machine with: docker save ${HELPER_IMAGE} -o artemis-helper.tar"

    log "Importing the Artemis image into the k3s image store"
    sudo k3s ctr images import "$application_archive" >/dev/null
    log "Importing the trusted helper image into the k3s image store"
    sudo k3s ctr images import "$helper_archive" >/dev/null
}

generate_credentials() {
    mkdir -p "$STATE_DIRECTORY"
    chmod 700 "$STATE_DIRECTORY"
    if [[ -f "$CREDENTIALS_FILE" ]]; then
        log "Reusing the credentials in ${CREDENTIALS_FILE}"
        return
    fi
    require_command openssl
    log "Generating credentials in ${CREDENTIALS_FILE}"
    {
        printf 'ARTEMIS_ADMIN_PASSWORD=%s\n' "$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24)"
        printf 'ARTEMIS_JWT_BASE64_SECRET=%s\n' "$(openssl rand -base64 64 | tr -d '\n')"
        printf 'POSTGRES_PASSWORD=%s\n' "$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24)"
        printf 'REGISTRY_PASSWORD=%s\n' "$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24)"
        printf 'BROKER_PASSWORD=%s\n' "$(openssl rand -base64 24 | tr -d '\n/+=' | cut -c1-24)"
    } >"$CREDENTIALS_FILE"
    chmod 600 "$CREDENTIALS_FILE"
}

write_values_file() {
    local node_address
    node_address="$(kube kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')"
    # shellcheck disable=SC1090
    . "$CREDENTIALS_FILE"

    cat >"$VALUES_FILE" <<EOF
# Generated by install-localci-kubernetes-ubuntu.sh. Edit and re-run 'install' to apply changes.
image:
  repository: ${APPLICATION_IMAGE%:*}
  tag: "${APPLICATION_IMAGE##*:}"
  pullPolicy: IfNotPresent

artemis:
  # One core node. A second one would need ReadWriteMany storage, which a single-node k3s does not provide.
  replicaCount: 1
  service:
    type: NodePort
    nodePort: ${NODE_PORT}
  config:
    # Build agents clone LocalVC repositories through this URL, so it has to be the address of this machine rather
    # than localhost, which inside a build pod would mean the pod itself.
    serverUrl: http://${node_address}:${NODE_PORT}
    admin:
      username: artemis_admin
      password: "${ARTEMIS_ADMIN_PASSWORD}"
    jwtBase64Secret: "${ARTEMIS_JWT_BASE64_SECRET}"
    # Served over plain HTTP, so a Secure cookie would never be sent back by the browser. Put TLS in front of this
    # before exposing it beyond the machine, and set this back to true.
    secureCookies: false

sharedStorage:
  # A single core node owns the volume, so k3s' local-path provisioner is enough.
  accessMode: ReadWriteOnce
  size: 20Gi

postgresql:
  deploy: true
  auth:
    database: Artemis
    username: Artemis
    password: "${POSTGRES_PASSWORD}"
  persistence:
    accessMode: ReadWriteOnce
    size: 10Gi

registry:
  password: "${REGISTRY_PASSWORD}"

broker:
  deploy: true
  auth:
    username: guest
    password: "${BROKER_PASSWORD}"

gateway:
  # Reached through the NodePort above instead.
  enabled: false
  create: false
  ssh:
    mode: none

buildAgents:
  enabled: true
  replicaCount: 1
  concurrentBuildsPerAgent: ${CONCURRENT_BUILDS}
  namespace: ${BUILD_NAMESPACE}
  createNamespace: true
  # Exercise images install packages as root and network isolation needs NET_ADMIN, neither of which the baseline
  # Pod Security profile allows. Only the build namespace is affected.
  podSecurityLevel: privileged
  helperImage:
    repository: ${HELPER_IMAGE%:*}
    tag: "${HELPER_IMAGE##*:}"
    pullPolicy: IfNotPresent
  defaultResources:
    cpu: "2"
    memory: 2Gi
    ephemeralStorage: 4Gi
EOF
    chmod 600 "$VALUES_FILE"
    log "Wrote ${VALUES_FILE}"
}

install_chart() {
    log "Installing the chart (this pulls PostgreSQL, the registry, and the broker on a first run)"
    kube helm upgrade --install "$RELEASE_NAME" "${SCRIPT_DIRECTORY}/helm/artemis" \
        --namespace "$ARTEMIS_NAMESPACE" \
        --create-namespace \
        --values "$VALUES_FILE" \
        --wait \
        --timeout 25m
}

wait_for_agent() {
    local attempt
    for attempt in $(seq 1 120); do
        if kube kubectl -n "$ARTEMIS_NAMESPACE" get pods --selector app.kubernetes.io/component=build-agent \
            -o jsonpath='{.items[*].status.containerStatuses[*].ready}' 2>/dev/null | grep -q true; then
            log "The build agent is ready"
            return
        fi
        sleep 5
    done
    fail "The build agent did not become ready; run './install-localci-kubernetes-ubuntu.sh logs'"
}

print_summary() {
    local node_address
    node_address="$(kube kubectl get nodes -o jsonpath='{.items[0].status.addresses[?(@.type=="InternalIP")].address}')"
    # shellcheck disable=SC1090
    . "$CREDENTIALS_FILE"
    printf '\n'
    log "Artemis is available at http://${node_address}:${NODE_PORT}"
    log "  administrator: artemis_admin / ${ARTEMIS_ADMIN_PASSWORD}"
    log "  credentials:   ${CREDENTIALS_FILE}"
    log "  values:        ${VALUES_FILE}"
    log "  build Jobs:    sudo k3s kubectl -n ${BUILD_NAMESPACE} get jobs,pods"
    printf '\n'
}

show_status() {
    require_command kubectl || true
    log "Node"
    kube kubectl get nodes -o wide
    log "Artemis workloads"
    kube kubectl -n "$ARTEMIS_NAMESPACE" get pods -o wide
    log "Build workloads"
    kube kubectl -n "$BUILD_NAMESPACE" get jobs,pods -o wide 2>/dev/null || true
}

show_logs() {
    log "Recent Artemis core logs"
    kube kubectl -n "$ARTEMIS_NAMESPACE" logs --selector app.kubernetes.io/component=artemis --all-containers --prefix --tail=150 || true
    log "Recent build-agent logs"
    kube kubectl -n "$ARTEMIS_NAMESPACE" logs --selector app.kubernetes.io/component=build-agent --all-containers --prefix --tail=150 || true
    log "Recent build workload logs"
    kube kubectl -n "$BUILD_NAMESPACE" logs --selector artemis.cit.tum.de/managed=true --all-containers --prefix --tail=100 2>/dev/null || true
    log "Recent warning events"
    kube kubectl get events --all-namespaces --field-selector type=Warning --sort-by=.lastTimestamp | tail -40 || true
}

uninstall() {
    log "Removing the release and both namespaces; k3s itself is left installed"
    kube helm uninstall "$RELEASE_NAME" --namespace "$ARTEMIS_NAMESPACE" --ignore-not-found --wait || true
    kube kubectl delete namespace "$BUILD_NAMESPACE" --ignore-not-found --wait=false || true
    kube kubectl delete namespace "$ARTEMIS_NAMESPACE" --ignore-not-found --wait=false || true
    log "Run 'sudo /usr/local/bin/k3s-uninstall.sh' to remove k3s as well"
}

case "$COMMAND" in
    install)
        check_preflight
        install_k3s
        install_helm
        load_images
        generate_credentials
        write_values_file
        install_chart
        wait_for_agent
        print_summary
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    uninstall)
        uninstall
        ;;
    -h|--help|help)
        usage
        ;;
    *)
        printf 'Unknown command: %s\n' "$COMMAND" >&2
        usage >&2
        exit 2
        ;;
esac
