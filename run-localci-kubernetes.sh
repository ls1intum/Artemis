#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly SCRIPT_DIRECTORY
readonly RELEASE_NAME="artemis"
readonly ARTEMIS_NAMESPACE="artemis"
readonly BUILD_NAMESPACE="artemis-builds"
readonly APPLICATION_IMAGE="artemis-localci-app:local"
readonly HELPER_IMAGE="artemis-localci-helper:local"
readonly EXERCISE_IMAGE="ubuntu:24.04"
readonly STATE_DIRECTORY="${LOCALCI_K8S_STATE_DIR:-${TMPDIR:-/tmp}/artemis-localci-kubernetes-${UID}}"
readonly PORT_FORWARD_PID_FILE="${STATE_DIRECTORY}/port-forward.pid"
readonly PORT_FORWARD_LOG_FILE="${STATE_DIRECTORY}/port-forward.log"
readonly COOKIE_FILE="${STATE_DIRECTORY}/admin.cookies"
readonly WORKLOAD_NODE_FILE="${STATE_DIRECTORY}/workload-nodes.txt"
readonly LOCAL_ADMIN_USERNAME="artemis_admin"
# Not "artemis_admin": the prod profile refuses to start on a password Artemis publishes as an example, and these
# pods run under it. Same fixture credential the local Docker E2E stacks use.
readonly LOCAL_ADMIN_PASSWORD="local-e2e-admin-not-a-deployment-credential"

COMMAND="${1:-all}"
if [[ $# -gt 0 ]]; then
    shift
fi

SKIP_BUILD=false
KEEP=false
TEST_FILTER=""
WORKLOAD_MONITOR_PID=""
LOCAL_JWT_BASE64_SECRET=""

usage() {
    printf '%s\n' "Usage: ./run-localci-kubernetes.sh <command> [options]"
    printf '%s\n' ""
    printf '%s\n' "Commands:"
    printf '%s\n' "  all       Build, deploy, test, and tear down the local cluster"
    printf '%s\n' "  build     Build the Artemis and trusted helper images"
    printf '%s\n' "  up        Deploy the chart and start the local port-forward"
    printf '%s\n' "  test      Run the Kubernetes LocalCI acceptance flow"
    printf '%s\n' "  status    Show topology, workloads, registered agents, and authorization checks"
    printf '%s\n' "  logs      Show recent logs and cluster events"
    printf '%s\n' "  down      Remove the local release and its two namespaces"
    printf '%s\n' ""
    printf '%s\n' "Options:"
    printf '%s\n' "  --skip-build        Reuse local images for up/all"
    printf '%s\n' "  --keep              Keep the all-command installation running"
    printf '%s\n' "  --filter <pattern>  Run only matching acceptance test names"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --skip-build)
            SKIP_BUILD=true
            ;;
        --keep)
            KEEP=true
            ;;
        --filter)
            if [[ $# -lt 2 ]]; then
                printf '%s\n' "--filter requires a Playwright test-name pattern" >&2
                exit 2
            fi
            TEST_FILTER="$2"
            shift
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
    printf '[localci-k8s] %s\n' "$*"
}

fail() {
    printf '[localci-k8s] ERROR: %s\n' "$*" >&2
    exit 1
}

require_command() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command '$1' was not found"
}

current_kubectl_context() {
    kubectl config current-context 2>/dev/null || true
}

# Whether the active kubectl context is the local Docker Desktop cluster. Returns non-zero when kubectl is missing or
# reports nothing, so an unanswerable question counts as "not local" rather than as permission to proceed.
is_local_kubectl_context() {
    [[ "$(current_kubectl_context)" == "docker-desktop" ]]
}

# Hard guard for commands that mutate a cluster without going through check_cluster_topology first.
require_local_kubectl_context() {
    is_local_kubectl_context || fail "Refusing to touch the cluster: this script only manages the local docker-desktop context, but the current context is '$(current_kubectl_context 2>/dev/null || true)'. Switch with 'kubectl config use-context docker-desktop'."
}

version_at_least() {
    local actual="$1"
    local required="$2"
    local actual_major actual_minor required_major required_minor
    IFS=. read -r actual_major actual_minor _ <<<"$actual"
    IFS=. read -r required_major required_minor _ <<<"$required"
    (( actual_major > required_major || (actual_major == required_major && actual_minor >= required_minor) ))
}

check_docker_desktop() {
    require_command docker
    docker info >/dev/null 2>&1 || fail "Docker Desktop is not running"

    local platform desktop_version
    platform="$(docker version --format '{{.Server.Platform.Name}}')"
    [[ "$platform" == Docker\ Desktop* ]] || fail "The local acceptance target requires Docker Desktop; found '$platform'"
    desktop_version="$(sed -E 's/^Docker Desktop ([0-9]+\.[0-9]+).*/\1/' <<<"$platform")"
    [[ "$desktop_version" =~ ^[0-9]+\.[0-9]+$ ]] || fail "Could not determine the Docker Desktop version from '$platform'"
    version_at_least "$desktop_version" "4.51" || fail "Docker Desktop 4.51 or newer is required; found $desktop_version"

    docker info --format '{{json .DriverStatus}}' | grep -q 'io.containerd.snapshotter' \
        || fail "Enable Docker Desktop's containerd image store so the managed kind nodes can use locally built images"
}

cluster_json() {
    docker desktop kubernetes status --format json
}

check_cluster_topology() {
    require_command kubectl
    require_command jq

    local current_context status mode configured_nodes node_json ready_nodes control_planes workers
    current_context="$(kubectl config current-context 2>/dev/null || true)"
    [[ "$current_context" == "docker-desktop" ]] || fail "Select the docker-desktop kubectl context; current context is '${current_context:-none}'"

    status="$(cluster_json | jq -r '.status // "unknown"')"
    mode="$(cluster_json | jq -r '.content.mode // "unknown"')"
    configured_nodes="$(cluster_json | jq -r '.content.nodeCount // 0')"
    [[ "$status" == "running" ]] || fail "Docker Desktop Kubernetes is not running (status: $status)"
    [[ "$mode" == "kind" ]] || fail "Docker Desktop Kubernetes must use the kind provisioner; current provisioner is '$mode'"
    [[ "$configured_nodes" -eq 3 ]] || fail "Configure exactly three Docker Desktop Kubernetes nodes; current setting is $configured_nodes"

    node_json="$(kubectl get nodes -o json)"
    ready_nodes="$(jq '[.items[] | select(any(.status.conditions[]?; .type == "Ready" and .status == "True"))] | length' <<<"$node_json")"
    control_planes="$(jq '[.items[] | select(.metadata.labels["node-role.kubernetes.io/control-plane"] != null or .metadata.labels["node-role.kubernetes.io/master"] != null)] | length' <<<"$node_json")"
    workers="$(jq '[.items[] | select(.metadata.labels["node-role.kubernetes.io/control-plane"] == null and .metadata.labels["node-role.kubernetes.io/master"] == null and (.spec.unschedulable // false) == false)] | length' <<<"$node_json")"
    [[ "$ready_nodes" -eq 3 ]] || fail "Expected three Ready nodes; found $ready_nodes"
    [[ "$control_planes" -eq 1 ]] || fail "Expected one control-plane node; found $control_planes"
    [[ "$workers" -eq 2 ]] || fail "Expected two schedulable worker nodes; found $workers"

    local cpu_count memory_bytes memory_gib
    cpu_count="$(docker info --format '{{.NCPU}}')"
    memory_bytes="$(docker info --format '{{.MemTotal}}')"
    memory_gib=$((memory_bytes / 1024 / 1024 / 1024))
    (( cpu_count >= 8 )) || fail "Allocate at least 8 CPUs to Docker Desktop; found $cpu_count"
    (( memory_gib >= 24 )) || fail "Allocate at least 24 GiB to Docker Desktop; found ${memory_gib} GiB"

    log "Validated Docker Desktop kind topology: one control-plane, two workers, ${cpu_count} CPUs, ${memory_gib} GiB"
}

check_helm() {
    require_command helm
    ensure_local_jwt_base64_secret
    helm lint "${SCRIPT_DIRECTORY}/helm/artemis" \
        --values "${SCRIPT_DIRECTORY}/helm/artemis/values-docker-desktop.yaml" \
        --set-string "artemis.config.jwtBase64Secret=${LOCAL_JWT_BASE64_SECRET}" \
        --set-string "artemis.config.admin.password=${LOCAL_ADMIN_PASSWORD}"
}

ensure_local_jwt_base64_secret() {
    if [[ -n "$LOCAL_JWT_BASE64_SECRET" ]]; then
        return
    fi

    if [[ -n "${LOCALCI_K8S_JWT_BASE64_SECRET:-}" ]]; then
        LOCAL_JWT_BASE64_SECRET="$LOCALCI_K8S_JWT_BASE64_SECRET"
        return
    fi

    require_command openssl
    LOCAL_JWT_BASE64_SECRET="$(openssl rand -base64 64 | tr -d '\n')"
}

worker_nodes() {
    kubectl get nodes -o json | jq -r '.items[] | select(.metadata.labels["node-role.kubernetes.io/control-plane"] == null and .metadata.labels["node-role.kubernetes.io/master"] == null) | .metadata.name'
}

label_nodes() {
    local -a workers=()
    local worker
    while IFS= read -r worker; do
        workers+=("$worker")
    done < <(worker_nodes)
    [[ "${#workers[@]}" -eq 2 ]] || fail "Cannot label workers: expected two, found ${#workers[@]}"

    kubectl label node "${workers[0]}" artemis.cit.tum.de/core=true --overwrite
    kubectl label node "${workers[0]}" artemis.cit.tum.de/build-worker=true --overwrite
    kubectl label node "${workers[1]}" artemis.cit.tum.de/build-worker=true --overwrite
    kubectl label node "${workers[1]}" artemis.cit.tum.de/core- --overwrite >/dev/null 2>&1 || true
    log "Pinned shared-storage core pods to ${workers[0]} and enabled build workloads on both workers"
}

build_images() {
    check_docker_desktop
    log "Building the production WAR"
    (cd "$SCRIPT_DIRECTORY" && ./gradlew -Pprod -Pwar clean bootWar --no-daemon)

    log "Building $APPLICATION_IMAGE"
    docker build --build-arg WAR_FILE_STAGE=external_builder --file "${SCRIPT_DIRECTORY}/docker/artemis/Dockerfile" --tag "$APPLICATION_IMAGE" "$SCRIPT_DIRECTORY"
    log "Building $HELPER_IMAGE"
    docker build --file "${SCRIPT_DIRECTORY}/docker/localci-kubernetes-helper/Dockerfile" --tag "$HELPER_IMAGE" "${SCRIPT_DIRECTORY}/docker/localci-kubernetes-helper"
    log "Caching the acceptance exercise image"
    docker pull "$EXERCISE_IMAGE"

    docker image inspect "$APPLICATION_IMAGE" "$HELPER_IMAGE" "$EXERCISE_IMAGE" >/dev/null
    log "Local images are available through Docker Desktop's shared containerd image store"
}

stop_port_forward() {
    if [[ ! -f "$PORT_FORWARD_PID_FILE" ]]; then
        return
    fi

    local pid command_line
    pid="$(<"$PORT_FORWARD_PID_FILE")"
    command_line="$(ps -p "$pid" -o command= 2>/dev/null || true)"
    if [[ "$command_line" == *"kubectl"*"port-forward"*"artemis-http"* ]]; then
        kill "$pid" >/dev/null 2>&1 || true
        wait "$pid" >/dev/null 2>&1 || true
    fi
    rm -f "$PORT_FORWARD_PID_FILE"
}

start_port_forward() {
    mkdir -p "$STATE_DIRECTORY"
    stop_port_forward

    if lsof -n -P -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
        fail "TCP port 8080 is already in use; free it before starting the local acceptance stack"
    fi

    kubectl -n "$ARTEMIS_NAMESPACE" port-forward --address 127.0.0.1 service/artemis-http 8080:8080 >"$PORT_FORWARD_LOG_FILE" 2>&1 &
    local pid=$!
    printf '%s\n' "$pid" >"$PORT_FORWARD_PID_FILE"

    local attempt
    for attempt in $(seq 1 240); do
        if curl --silent --fail --max-time 5 http://127.0.0.1:8080/management/health/readiness >/dev/null 2>&1; then
            log "Artemis is reachable at http://127.0.0.1:8080"
            return
        fi
        kill -0 "$pid" >/dev/null 2>&1 || fail "The port-forward stopped unexpectedly; inspect $PORT_FORWARD_LOG_FILE"
        if (( attempt % 12 == 0 )); then
            log "Waiting for the Artemis readiness endpoint (${attempt}/240)"
        fi
        sleep 5
    done
    fail "Timed out waiting for the Artemis readiness endpoint"
}

authenticate_admin() {
    mkdir -p "$STATE_DIRECTORY"
    rm -f "$COOKIE_FILE"
    curl --silent --show-error --fail --cookie-jar "$COOKIE_FILE" \
        --header 'Content-Type: application/json' \
        --data "{\"username\":\"${LOCAL_ADMIN_USERNAME}\",\"password\":\"${LOCAL_ADMIN_PASSWORD}\",\"rememberMe\":true}" \
        http://127.0.0.1:8080/api/core/public/authenticate >/dev/null
}

wait_for_agents() {
    authenticate_admin
    local attempt agents_json count kubernetes_count
    for attempt in $(seq 1 120); do
        agents_json="$(curl --silent --fail --cookie "$COOKIE_FILE" http://127.0.0.1:8080/api/admin/build-agents 2>/dev/null || printf '[]')"
        count="$(jq 'length' <<<"$agents_json")"
        kubernetes_count="$(jq '[.[] | select(.buildAgentDetails.buildRunner == "Kubernetes" and ((.buildAgentDetails.buildRunnerVersion // "") | length > 0))] | length' <<<"$agents_json")"
        if [[ "$count" -eq 2 && "$kubernetes_count" -eq 2 ]]; then
            log "Two Kubernetes build agents are registered"
            return
        fi
        if (( attempt % 10 == 0 )); then
            log "Waiting for two Kubernetes build agents with version metadata (registered: $count, ready: $kubernetes_count)"
        fi
        sleep 3
    done
    fail "Timed out waiting for two Kubernetes build agents to register"
}

verify_agent_placement() {
    local distinct_nodes
    distinct_nodes="$(kubectl -n "$ARTEMIS_NAMESPACE" get pods --selector app.kubernetes.io/component=build-agent -o json \
        | jq '[.items[].spec.nodeName // empty] | unique | length')"
    [[ "$distinct_nodes" -ge 2 ]] || fail "The two build-agent controllers were scheduled on only $distinct_nodes worker node(s)"
    log "Verified build-agent controller placement across both workers"
}

verify_authorization() {
    # kubectl auth can-i answers "no" with exit status 1, so every one of these needs `|| true`: under
    # `set -Eeuo pipefail` the assignment itself would otherwise end the script, and the caller would see a bare
    # kubectl exit instead of the message explaining which permission is missing.
    local controller_can_create controller_can_exec workload_can_list
    controller_can_create="$(kubectl auth can-i create jobs.batch --as "system:serviceaccount:${ARTEMIS_NAMESPACE}:artemis-localci-controller" --namespace "$BUILD_NAMESPACE" || true)"
    controller_can_exec="$(kubectl auth can-i get pods --subresource=exec --as "system:serviceaccount:${ARTEMIS_NAMESPACE}:artemis-localci-controller" --namespace "$BUILD_NAMESPACE" || true)"
    workload_can_list="$(kubectl auth can-i list secrets --as "system:serviceaccount:${BUILD_NAMESPACE}:artemis-localci-workload" --namespace "$BUILD_NAMESPACE" || true)"
    [[ "$controller_can_create" == "yes" ]] || fail "The build controller cannot create Jobs in $BUILD_NAMESPACE"
    [[ "$controller_can_exec" == "yes" ]] || fail "The build controller cannot execute helper commands in $BUILD_NAMESPACE"
    [[ "$workload_can_list" == "no" ]] || fail "The workload service account unexpectedly has permission to list secrets"
    log "Verified controller permissions and the permission-free workload identity"
}

deploy_chart() {
    local application_image_id

    check_docker_desktop
    check_cluster_topology
    check_helm
    label_nodes

    if [[ "$SKIP_BUILD" != true ]]; then
        build_images
    else
        docker image inspect "$APPLICATION_IMAGE" "$HELPER_IMAGE" "$EXERCISE_IMAGE" >/dev/null \
            || fail "--skip-build was requested, but one or more required images are missing"
    fi

    application_image_id="$(docker image inspect --format '{{.Id}}' "$APPLICATION_IMAGE")"
    ensure_local_jwt_base64_secret
    log "Deploying two Artemis cores and two Kubernetes build agents"
    helm upgrade --install "$RELEASE_NAME" "${SCRIPT_DIRECTORY}/helm/artemis" \
        --namespace "$ARTEMIS_NAMESPACE" \
        --create-namespace \
        --values "${SCRIPT_DIRECTORY}/helm/artemis/values-docker-desktop.yaml" \
        --set-string "artemis.config.jwtBase64Secret=${LOCAL_JWT_BASE64_SECRET}" \
        --set-string "artemis.config.admin.password=${LOCAL_ADMIN_PASSWORD}" \
        --set-string "image.rolloutId=${application_image_id}" \
        --wait \
        --timeout 25m

    start_port_forward
    wait_for_agents
    verify_agent_placement
    verify_authorization
}

show_status() {
    check_docker_desktop
    require_command kubectl
    require_command jq
    log "Docker Desktop Kubernetes"
    cluster_json | jq '{status, mode: .content.mode, configuredNodes: .content.nodeCount, version: .content.version}'
    log "Nodes"
    kubectl get nodes --show-labels
    log "Artemis workloads"
    kubectl -n "$ARTEMIS_NAMESPACE" get pods -o wide
    log "Build workloads"
    kubectl -n "$BUILD_NAMESPACE" get jobs,pods -o wide 2>/dev/null || true

    if curl --silent --fail --max-time 2 http://127.0.0.1:8080/management/health/readiness >/dev/null 2>&1; then
        authenticate_admin
        log "Registered build agents"
        curl --silent --fail --cookie "$COOKIE_FILE" http://127.0.0.1:8080/api/admin/build-agents \
            | jq '[.[] | {name: .buildAgent.name, status, currentJobs: .numberOfCurrentBuildJobs, capacity: .maxNumberOfConcurrentBuildJobs, runner: .buildAgentDetails.buildRunner, runnerVersion: .buildAgentDetails.buildRunnerVersion}]'
    else
        log "The local port-forward is not active; API status was skipped"
    fi
}

show_logs() {
    require_command kubectl
    log "Recent Artemis core logs"
    kubectl -n "$ARTEMIS_NAMESPACE" logs --selector app.kubernetes.io/component=artemis --all-containers --prefix --tail=150 || true
    log "Recent build-agent logs"
    kubectl -n "$ARTEMIS_NAMESPACE" logs --selector app.kubernetes.io/component=build-agent --all-containers --prefix --tail=200 || true
    log "Recent build workload logs"
    kubectl -n "$BUILD_NAMESPACE" logs --selector artemis.cit.tum.de/managed=true --all-containers --prefix --tail=100 || true
    log "Recent warning events"
    kubectl get events --all-namespaces --field-selector type=Warning --sort-by=.lastTimestamp | tail -80 || true
}

monitor_workload_nodes() {
    : >"$WORKLOAD_NODE_FILE"
    while true; do
        kubectl -n "$BUILD_NAMESPACE" get pods --selector artemis.cit.tum.de/managed=true -o json 2>/dev/null \
            | jq -r '.items[].spec.nodeName // empty' >>"$WORKLOAD_NODE_FILE" || true
        sleep 1
    done
}

stop_workload_monitor() {
    if [[ -z "$WORKLOAD_MONITOR_PID" ]]; then
        return
    fi
    kill "$WORKLOAD_MONITOR_PID" >/dev/null 2>&1 || true
    wait "$WORKLOAD_MONITOR_PID" >/dev/null 2>&1 || true
    WORKLOAD_MONITOR_PID=""
}

run_acceptance_test() {
    check_docker_desktop
    check_cluster_topology
    require_command pnpm
    require_command jq
    start_port_forward
    wait_for_agents
    verify_agent_placement
    verify_authorization

    mkdir -p "$STATE_DIRECTORY"
    monitor_workload_nodes &
    WORKLOAD_MONITOR_PID=$!
    local test_status=0
    local -a playwright_arguments
    playwright_arguments=(
        exec playwright test
        e2e/localci/KubernetesLocalCI.spec.ts
        --project=kubernetes-tests
        --workers=1
        --retries=0
    )
    if [[ -n "$TEST_FILTER" ]]; then
        playwright_arguments+=(--grep "$TEST_FILTER")
    fi

    log "Running the Kubernetes LocalCI acceptance flow"
    (
        cd "${SCRIPT_DIRECTORY}/src/test/playwright"
        BASE_URL=http://127.0.0.1:8080 \
        ADMIN_USERNAME="$LOCAL_ADMIN_USERNAME" \
        ADMIN_PASSWORD="$LOCAL_ADMIN_PASSWORD" \
        ALLOW_GROUP_CUSTOMIZATION=true \
        STUDENT_GROUP_NAME=artemis-e2etest-students \
        TUTOR_GROUP_NAME=artemis-e2etest-tutors \
        EDITOR_GROUP_NAME=artemis-e2etest-editors \
        INSTRUCTOR_GROUP_NAME=artemis-e2etest-instructors \
        EXPECTED_CLUSTER_NODE_COUNT=2 \
        PLAYWRIGHT_COVERAGE=off \
        TEST_TIMEOUT_SECONDS=600 \
        SLOW_TEST_TIMEOUT_SECONDS=600 \
        BUILD_RESULT_TIMEOUT_MS=300000 \
        BUILD_FINISH_TIMEOUT_MS=300000 \
        pnpm "${playwright_arguments[@]}"
    ) || test_status=$?

    stop_workload_monitor

    if [[ "$test_status" -ne 0 ]]; then
        show_logs
        return "$test_status"
    fi

    local distinct_nodes
    distinct_nodes="$(sort -u "$WORKLOAD_NODE_FILE" | sed '/^$/d' | wc -l | tr -d ' ')"
    [[ "$distinct_nodes" -ge 2 ]] || fail "Acceptance builds were observed on only $distinct_nodes worker node(s)"
    log "Observed native LocalCI Jobs on both build workers"
}

tear_down() {
    stop_workload_monitor
    stop_port_forward
    # The context is checked before anything below can mutate a cluster, not after. These are unconditional namespace
    # deletions of two fixed names, so aimed at the wrong context they would delete a real cluster's artemis and
    # artemis-builds namespaces. `down` also runs this without having gone through check_cluster_topology, and this
    # function doubles as an EXIT trap, so the guard has to live here rather than only at the call site.
    if ! is_local_kubectl_context; then
        log "Skipped the cluster teardown: the current kubectl context is '$(current_kubectl_context)', not docker-desktop"
    else
        if command -v helm >/dev/null 2>&1; then
            helm uninstall "$RELEASE_NAME" --namespace "$ARTEMIS_NAMESPACE" --ignore-not-found --wait || true
        fi
        if command -v kubectl >/dev/null 2>&1; then
            kubectl delete namespace "$BUILD_NAMESPACE" --ignore-not-found --wait=false || true
            kubectl delete namespace "$ARTEMIS_NAMESPACE" --ignore-not-found --wait=false || true
            kubectl label nodes --all artemis.cit.tum.de/core- artemis.cit.tum.de/build-worker- >/dev/null 2>&1 || true
        fi
        log "Removed the local release, namespaces, and Artemis node labels; locally built images were retained"
    fi
    rm -f "$COOKIE_FILE" "$WORKLOAD_NODE_FILE" "$PORT_FORWARD_LOG_FILE"
    rmdir "$STATE_DIRECTORY" >/dev/null 2>&1 || true
}

trap stop_workload_monitor EXIT

case "$COMMAND" in
    all)
        check_docker_desktop
        check_cluster_topology
        check_helm
        if [[ "$KEEP" != true ]]; then
            trap tear_down EXIT
        fi
        deploy_chart
        run_acceptance_test
        show_status
        if [[ "$KEEP" == true ]]; then
            log "Validation passed; the installation and port-forward remain active"
        else
            log "Validation passed; tearing down the local installation"
        fi
        ;;
    build)
        build_images
        ;;
    up)
        deploy_chart
        ;;
    test)
        run_acceptance_test
        ;;
    status)
        show_status
        ;;
    logs)
        show_logs
        ;;
    down)
        # Explicitly requested, so a wrong context is an error to report rather than something to skip silently.
        require_local_kubectl_context
        tear_down
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
