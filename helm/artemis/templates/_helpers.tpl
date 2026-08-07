{{/*
Expand the name of the chart.
*/}}
{{- define "artemis.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Fully qualified app name.
*/}}
{{- define "artemis.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{- define "artemis.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "artemis.labels" -}}
helm.sh/chart: {{ include "artemis.chart" . }}
{{ include "artemis.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Base selector labels (common to every resource in the release). Deliberately
does NOT include a component label so per-component resources can add their own.
*/}}
{{- define "artemis.selectorLabels" -}}
app.kubernetes.io/name: {{ include "artemis.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Selector labels for the Artemis core pods (leader + member). Used by the
Artemis StatefulSets and the http/ssh/headless Services so they only ever
select Artemis pods, never postgres/registry/broker.
*/}}
{{- define "artemis.coreSelectorLabels" -}}
{{ include "artemis.selectorLabels" . }}
app.kubernetes.io/component: artemis
{{- end }}

{{- define "artemis.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "artemis.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/* ------------------------------------------------------------------ */}}
{{/* Resource names                                                     */}}
{{/* ------------------------------------------------------------------ */}}
{{- define "artemis.leaderName"   -}}{{ include "artemis.fullname" . }}-leader{{- end }}
{{- define "artemis.memberName"   -}}{{ include "artemis.fullname" . }}-member{{- end }}
{{- define "artemis.headlessName" -}}{{ include "artemis.fullname" . }}-headless{{- end }}
{{- define "artemis.httpName"     -}}{{ include "artemis.fullname" . }}-http{{- end }}
{{- define "artemis.sshName"      -}}{{ include "artemis.fullname" . }}-ssh{{- end }}
{{- define "artemis.configName"   -}}{{ include "artemis.fullname" . }}-config{{- end }}
{{- define "artemis.secretName"   -}}{{ include "artemis.fullname" . }}-secret{{- end }}
{{- define "artemis.hostkeySecretName" -}}{{ include "artemis.fullname" . }}-ssh-hostkey{{- end }}
{{- define "artemis.postgresName" -}}{{ include "artemis.fullname" . }}-postgres{{- end }}
{{- define "artemis.hadesAdapterName" -}}{{ include "artemis.fullname" . }}-hades-adapter{{- end }}
{{- define "artemis.registryName" -}}{{ include "artemis.fullname" . }}-registry{{- end }}
{{- define "artemis.brokerName"   -}}{{ include "artemis.fullname" . }}-broker{{- end }}

{{/* ------------------------------------------------------------------ */}}
{{/* Derived hostnames / ports                                          */}}
{{/* ------------------------------------------------------------------ */}}

{{/* Effective public server URL (falls back to https://<gateway.hostname>). */}}
{{- define "artemis.serverUrl" -}}
{{- if .Values.artemis.config.serverUrl -}}
{{- .Values.artemis.config.serverUrl -}}
{{- else -}}
https://{{ required "gateway.hostname (or artemis.config.serverUrl) is required" .Values.gateway.hostname }}
{{- end -}}
{{- end }}

{{/* Database host: bundled service or external host. */}}
{{- define "artemis.dbHost" -}}
{{- if .Values.postgresql.deploy -}}
{{ include "artemis.postgresName" . }}
{{- else -}}
{{ required "postgresql.external.host is required when postgresql.deploy=false" .Values.postgresql.external.host }}
{{- end -}}
{{- end }}

{{- define "artemis.dbPort" -}}
{{- if .Values.postgresql.deploy -}}5432{{- else -}}{{ .Values.postgresql.external.port }}{{- end -}}
{{- end }}

{{- define "artemis.dbSslMode" -}}
{{- if .Values.postgresql.deploy -}}disable{{- else -}}{{ .Values.postgresql.external.sslmode }}{{- end -}}
{{- end }}

{{/* JDBC URL for PostgreSQL. */}}
{{- define "artemis.jdbcUrl" -}}
jdbc:postgresql://{{ include "artemis.dbHost" . }}:{{ include "artemis.dbPort" . }}/{{ .Values.postgresql.auth.database }}?sslmode={{ include "artemis.dbSslMode" . }}
{{- end }}

{{/* Broker STOMP address list. */}}
{{- define "artemis.brokerAddresses" -}}
{{- if .Values.broker.deploy -}}
{{ include "artemis.brokerName" . }}:{{ .Values.broker.stompPort }}
{{- else -}}
{{ required "broker.externalAddresses is required when broker.deploy=false" .Values.broker.externalAddresses }}
{{- end -}}
{{- end }}

{{/* Base URL the hades-artemis-adapter uses to reach Artemis (in-cluster http service by default). */}}
{{- define "artemis.hadesAdapterArtemisBaseUrl" -}}
{{- .Values.hadesAdapter.artemisBaseUrl | default (printf "http://%s:8080" (include "artemis.httpName" .)) -}}
{{- end }}

{{/* Effective Hades adapter endpoint Artemis hands to the result parser. When the adapter is
     deployed by this chart and the user didn't override it, point at the in-cluster adapter.
     MUST be the fully-qualified service DNS name: the Hades build/parse containers run in the
     Hades namespace, so a bare service name would not resolve. */}}
{{- define "artemis.hadesAdapterEndpoint" -}}
{{- if .Values.artemis.config.hades.adapterEndpoint -}}
{{- .Values.artemis.config.hades.adapterEndpoint -}}
{{- else if .Values.hadesAdapter.deploy -}}
http://{{ include "artemis.hadesAdapterName" . }}.{{ .Release.Namespace }}.svc.cluster.local:{{ .Values.hadesAdapter.port }}{{ .Values.hadesAdapter.ingestPath }}
{{- else -}}
{{- required "artemis.config.hades.adapterEndpoint is required when hadesAdapter.deploy=false" .Values.artemis.config.hades.adapterEndpoint -}}
{{- end -}}
{{- end }}

{{/* Eureka service URL with embedded admin credentials. */}}
{{- define "artemis.eurekaUrl" -}}
http://admin:{{ required "registry.password is required" .Values.registry.password }}@{{ include "artemis.registryName" . }}:{{ .Values.registry.service.port }}/eureka/
{{- end }}

{{/* TLS Secret backing the HTTPS listener (cert-manager writes the cert here). */}}
{{- define "artemis.tlsSecretName" -}}
{{- .Values.gateway.tls.secretName | default (printf "%s-tls" (include "artemis.fullname" .)) -}}
{{- end }}

{{/* Effective cert-manager issuer name: the created one wins over an existing ref. */}}
{{- define "artemis.certIssuerName" -}}
{{- if .Values.gateway.tls.certManagerIssuer.create -}}
{{- .Values.gateway.tls.certManagerIssuer.name | default (printf "%s-letsencrypt" (include "artemis.fullname" .)) -}}
{{- else -}}
{{- .Values.gateway.tls.certManagerClusterIssuer -}}
{{- end -}}
{{- end }}

{{/* Name/namespace of the Gateway (created here, or the referenced existing one). */}}
{{- define "artemis.gatewayName" -}}
{{- if .Values.gateway.create -}}
{{- include "artemis.fullname" . -}}
{{- else -}}
{{- required "gateway.parentRef.name is required when gateway.create=false" .Values.gateway.parentRef.name -}}
{{- end -}}
{{- end }}
{{- define "artemis.gatewayNamespace" -}}
{{- if .Values.gateway.create -}}
{{- .Release.Namespace -}}
{{- else -}}
{{- .Values.gateway.parentRef.namespace | default .Release.Namespace -}}
{{- end -}}
{{- end }}

{{/* Base Spring profiles shared by every core node. */}}
{{- define "artemis.baseProfiles" -}}
prod,core,artemis,localvc,hades,docker
{{- range .Values.artemis.extraProfiles -}},{{ . }}{{- end -}}
{{- end }}

{{/* ------------------------------------------------------------------ */}}
{{/* Shared pod spec for leader and member StatefulSets.                */}}
{{/* Call with a dict: (dict "root" $ "extraProfiles" "scheduling")     */}}
{{/* ------------------------------------------------------------------ */}}
{{- define "artemis.podSpec" -}}
{{- $ := .root -}}
serviceAccountName: {{ include "artemis.serviceAccountName" $ }}
{{- with $.Values.imagePullSecrets }}
imagePullSecrets:
  {{- toYaml . | nindent 2 }}
{{- end }}
securityContext:
  {{- toYaml $.Values.artemis.podSecurityContext | nindent 2 }}
{{- with $.Values.artemis.nodeSelector }}
nodeSelector:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with $.Values.artemis.affinity }}
affinity:
  {{- toYaml . | nindent 2 }}
{{- end }}
{{- with $.Values.artemis.tolerations }}
tolerations:
  {{- toYaml . | nindent 2 }}
{{- end }}
initContainers:
  # (a) Fix ownership of the shared volumes for uid/gid 1337 - many RWX
  # provisioners ignore fsGroup, so we chown as root before the app starts.
  - name: init-chown
    image: busybox:1.37
    securityContext:
      runAsUser: 0
      runAsNonRoot: false
    command:
      - sh
      - -c
      - |
        mkdir -p /opt/artemis/data /opt/artemis/local
        chown -R 1337:1337 /opt/artemis/data /opt/artemis/local
    volumeMounts:
      - name: artemis-data
        mountPath: /opt/artemis/data
      - name: artemis-local
        mountPath: /opt/artemis/local
  # (b) Wait for PostgreSQL and the JHipster Registry before booting.
  - name: init-wait-deps
    image: busybox:1.37
    command:
      - sh
      - -c
      - |
        until nc -z -w 2 {{ include "artemis.dbHost" $ }} {{ include "artemis.dbPort" $ }}; do
          echo "waiting for database {{ include "artemis.dbHost" $ }}:{{ include "artemis.dbPort" $ }}"; sleep 5; done
        until nc -z -w 2 {{ include "artemis.registryName" $ }} {{ $.Values.registry.service.port }}; do
          echo "waiting for registry"; sleep 5; done
        echo "dependencies are up"
{{- if .waitForLeader }}
  # (c) Members wait for the leader to be ready so concurrent Liquibase
  # migrations on a fresh DB don't race on the artemis_version primary key.
  - name: init-wait-leader
    image: busybox:1.37
    command:
      - sh
      - -c
      - |
        until wget -q -T 5 -O - http://{{ include "artemis.leaderName" $ }}-0.{{ include "artemis.headlessName" $ }}:8080/management/health/readiness | grep -q '"status":"UP"'; do
          echo "waiting for leader to be ready"; sleep 10; done
        echo "leader is ready"
{{- end }}
containers:
  - name: artemis
    image: "{{ $.Values.image.repository }}:{{ $.Values.image.tag | default $.Chart.AppVersion }}"
    imagePullPolicy: {{ $.Values.image.pullPolicy }}
    securityContext:
      {{- toYaml $.Values.artemis.containerSecurityContext | nindent 6 }}
    env:
      # Hazelcast binds to the pod IP; peers are discovered via Eureka.
      - name: MY_POD_IP
        valueFrom:
          fieldRef:
            fieldPath: status.podIP
      - name: SPRING_HAZELCAST_INTERFACE
        value: "$(MY_POD_IP)"
      - name: SPRING_PROFILES_ACTIVE
        value: "{{ include "artemis.baseProfiles" $ }}{{ if .extraProfiles }},{{ .extraProfiles }}{{ end }}"
      - name: EUREKA_INSTANCE_INSTANCEID
        valueFrom:
          fieldRef:
            fieldPath: metadata.name
    envFrom:
      - configMapRef:
          name: {{ include "artemis.configName" $ }}
      - secretRef:
          name: {{ include "artemis.secretName" $ }}
    ports:
      - name: http
        containerPort: 8080
      - name: hazelcast
        containerPort: 5701
      - name: ssh
        containerPort: 7921
    startupProbe:
      httpGet:
        path: /management/health/readiness
        port: http
      periodSeconds: {{ $.Values.artemis.probes.startup.periodSeconds }}
      failureThreshold: {{ $.Values.artemis.probes.startup.failureThreshold }}
      timeoutSeconds: {{ $.Values.artemis.probes.startup.timeoutSeconds }}
    readinessProbe:
      httpGet:
        path: /management/health/readiness
        port: http
      periodSeconds: {{ $.Values.artemis.probes.readiness.periodSeconds }}
      failureThreshold: {{ $.Values.artemis.probes.readiness.failureThreshold }}
      timeoutSeconds: {{ $.Values.artemis.probes.readiness.timeoutSeconds }}
    livenessProbe:
      httpGet:
        path: /management/health/liveness
        port: http
      periodSeconds: {{ $.Values.artemis.probes.liveness.periodSeconds }}
      failureThreshold: {{ $.Values.artemis.probes.liveness.failureThreshold }}
      timeoutSeconds: {{ $.Values.artemis.probes.liveness.timeoutSeconds }}
    resources:
      {{- toYaml $.Values.artemis.resources | nindent 6 }}
    volumeMounts:
      - name: artemis-data
        mountPath: /opt/artemis/data
      - name: artemis-local
        mountPath: /opt/artemis/local
      - name: ssh-hostkey
        mountPath: /opt/artemis/config/ssh-hostkey
        readOnly: true
volumes:
  - name: artemis-data
    persistentVolumeClaim:
      claimName: {{ $.Values.sharedStorage.existingClaim | default (printf "%s-data" (include "artemis.fullname" $)) }}
  - name: artemis-local
    emptyDir: {}
  - name: ssh-hostkey
    secret:
      secretName: {{ include "artemis.hostkeySecretName" $ }}
      defaultMode: 0400
{{- end }}
