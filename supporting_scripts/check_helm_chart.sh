#!/usr/bin/env bash
# Checks the Helm chart in deployment/helm/artemis without a cluster: it lints, renders it in the variants that matter and checks
# what comes out, and, when kubeconform is installed, validates the Kubernetes objects against their schemas.
#
# Why this exists: a template that renders in the default configuration can fail, or silently render something else, in another
# one. A values file that sets a map to null, or a ScaledObject that KEDA warns about, are what such checks would have caught.
# It checks the contract of the chart, so that a change that breaks one of the following fails here and not on a cluster:
#   - Without autoscaling nothing changes: no ScaledObject, and the build agent StatefulSet keeps its replica count.
#   - With autoscaling the StatefulSet no longer sets its replica count, and the ScaledObject scopes its query to one installation.
#   - A configuration that cannot work is refused when the chart renders, with a message that names the value.
#
# Usage: supporting_scripts/check_helm_chart.sh   (from the root of the repository; needs helm, and uses kubeconform if present)
set -Eeuo pipefail

chart="deployment/helm/artemis"
values="$chart/ci"
failures=0

fail() {
    echo "::error::$*"
    failures=$((failures + 1))
}

render() {
    helm template artemis "$chart" -f "$values/base-values.yaml" "$@"
}

# Counts the occurrences of a fixed string, which is not the number of lines: both gauges of a query are on one. grep exits 1 for no
# match, which must not end the script.
count_of() {
    local pattern="$1"
    { grep -o -F -- "$pattern" || true; } | wc -l | tr -d ' '
}

expect() {
    local description="$1" expected="$2" actual="$3"
    if [ "$expected" != "$actual" ]; then
        fail "$description: expected $expected, got $actual"
    else
        echo "ok: $description"
    fi
}

# The chart must refuse the configuration, and the message must say why.
expect_refused() {
    local description="$1" message="$2"
    shift 2
    local output
    if output=$(render "$@" 2>&1); then
        fail "$description: the chart rendered, but it should have been refused"
    elif ! grep -q -F -- "$message" <<< "$output"; then
        fail "$description: refused, but the message does not contain '$message': $output"
    else
        echo "ok: $description"
    fi
}

echo "== lint"
helm lint "$chart" -f "$values/base-values.yaml"
helm lint "$chart" -f "$values/base-values.yaml" -f "$values/autoscaling-values.yaml"

echo "== default: no autoscaling"
default_render=$(render)
expect "no ScaledObject by default" 0 "$(count_of 'kind: ScaledObject' <<< "$default_render")"
default_agents=$(render --show-only templates/build-agents/statefulset.yaml)
expect "the build agents keep their replica count" 1 "$(count_of '  replicas: 2' <<< "$default_agents")"

echo "== autoscaling"
scaled=$(render -f "$values/autoscaling-values.yaml" --show-only templates/build-agents/scaledobject.yaml)
expect "one ScaledObject" 1 "$(count_of 'kind: ScaledObject' <<< "$scaled")"
expect "it is named like the build agent StatefulSet and targets it" 2 "$(count_of 'name: artemis-build-agent' <<< "$scaled")"
expect "minimum" 1 "$(count_of 'minReplicaCount: 2' <<< "$scaled")"
expect "maximum" 1 "$(count_of 'maxReplicaCount: 8' <<< "$scaled")"
expect "the query is scoped to the installation, for both gauges" 2 "$(count_of 'namespace=\"artemis\"' <<< "$scaled")"
expect "the threshold is the concurrent builds of one controller" 1 "$(count_of 'threshold: "2"' <<< "$scaled")"
expect "a missing metric is an error and not zero" 1 "$(count_of 'ignoreNullValues: "false"' <<< "$scaled")"
expect "no polling interval above zero replicas, which KEDA ignores and warns about" 0 "$(count_of 'pollingInterval' <<< "$scaled")"
expect "no cooldown period above zero replicas, which KEDA ignores and warns about" 0 "$(count_of 'cooldownPeriod' <<< "$scaled")"
scaled_agents=$(render -f "$values/autoscaling-values.yaml" --show-only templates/build-agents/statefulset.yaml)
expect "the StatefulSet no longer sets its replica count, so an upgrade does not reset it" 0 "$(count_of '  replicas:' <<< "$scaled_agents")"

echo "== autoscaling down to zero"
to_zero=$(render -f "$values/autoscaling-values.yaml" --set buildAgents.autoscaling.minReplicas=0 --show-only templates/build-agents/scaledobject.yaml)
expect "the polling interval is rendered for scaling from zero" 1 "$(count_of 'pollingInterval' <<< "$to_zero")"
expect "the cooldown period is rendered for scaling to zero" 1 "$(count_of 'cooldownPeriod' <<< "$to_zero")"

echo "== a custom query needs no selector"
custom=$(render -f "$values/autoscaling-values.yaml" --set buildAgents.autoscaling.prometheus.selector= --set-string buildAgents.autoscaling.prometheus.query='sum(my_metric)' \
    --show-only templates/build-agents/scaledobject.yaml)
expect "the custom query is used as it is" 1 "$(count_of 'query: "sum(my_metric)"' <<< "$custom")"

echo "== a missing autoscaling map counts as autoscaling being off"
render --set buildAgents.autoscaling=null > /dev/null
expect "the build agents keep their replica count" 1 "$(count_of '  replicas: 2' <<< "$(render --set buildAgents.autoscaling=null --show-only templates/build-agents/statefulset.yaml)")"

echo "== no build agents, no autoscaling"
without_agents=$(render -f "$values/autoscaling-values.yaml" --set buildAgents.enabled=false)
expect "no ScaledObject without build agents" 0 "$(count_of 'kind: ScaledObject' <<< "$without_agents")"
expect "no build agent StatefulSet without build agents" 0 "$(count_of 'name: artemis-build-agent' <<< "$without_agents")"

echo "== configurations that cannot work are refused"
expect_refused "autoscaling without a Prometheus address" "buildAgents.autoscaling.prometheus.serverAddress is required" --set buildAgents.autoscaling.enabled=true
expect_refused "autoscaling without a selector or a query" "buildAgents.autoscaling.prometheus.selector is required" \
    --set buildAgents.autoscaling.enabled=true --set buildAgents.autoscaling.prometheus.serverAddress=http://prometheus:9090
expect_refused "a maximum below the minimum" "maxReplicas must not be lower than minReplicas" -f "$values/autoscaling-values.yaml" \
    --set buildAgents.autoscaling.minReplicas=5 --set buildAgents.autoscaling.maxReplicas=2

echo "== Gateway API resources"
gateway=$(render -f "$values/gateway-values.yaml")
expect "the HTTP route is rendered" 1 "$(count_of 'kind: HTTPRoute' <<< "$gateway")"

echo "== Kubernetes schemas"
if command -v kubeconform > /dev/null 2>&1; then
    # The schemas of resources that Kubernetes does not ship, KEDA and the Gateway API among them, come from the CRD catalog.
    # A kind that has none is skipped, so only a catalog that is unreachable for a kind that exists would hide an error.
    crd_schemas='https://raw.githubusercontent.com/datreeio/CRDs-catalog/main/{{.Group}}/{{.ResourceKind}}_{{.ResourceAPIVersion}}.json'
    for variant in "" "-f $values/autoscaling-values.yaml" "-f $values/gateway-values.yaml"; do
        # shellcheck disable=SC2086 # the variant is a list of arguments on purpose
        if render $variant | kubeconform -strict -summary -ignore-missing-schemas -schema-location default -schema-location "$crd_schemas"; then
            echo "ok: schemas${variant:+ with $variant}"
        else
            fail "kubeconform found an invalid object${variant:+ with $variant}"
        fi
    done
else
    echo "kubeconform is not installed, so the Kubernetes schemas are not checked. See https://github.com/yannh/kubeconform"
fi

if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) of the Helm chart failed"
    exit 1
fi
echo "All checks of the Helm chart passed"
