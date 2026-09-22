package de.tum.cit.aet.artemis.aiworker.dto;

import java.util.Map;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Trusted local deployment policy. Requests and model tools cannot choose or widen these limits. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SandboxPolicyDTO(String id, String image, String runtime, long memoryBytes, long cpuQuota, long pids, String ownerLabel, String containerPrefix,
        Map<String, String> writableFilesystems) {

    private static final Pattern WORKER_ID = Pattern.compile("[a-zA-Z0-9_-]{1,64}");

    private static final Pattern IMAGE = Pattern.compile("(?:[^\\s]+@)?sha256:[a-f0-9]{64}");

    private static final Pattern OWNER_LABEL = Pattern.compile("[a-z][a-z0-9.-]{0,127}");

    private static final Pattern CONTAINER_PREFIX = Pattern.compile("[a-z][a-z0-9-]{0,63}-");

    public SandboxPolicyDTO {
        if (id == null || !WORKER_ID.matcher(id).matches() || image == null || !IMAGE.matcher(image).matches()) {
            throw new IllegalArgumentException("Sandbox identity must be destination-safe and its image pinned by digest");
        }
        if (runtime == null || runtime.isBlank() || memoryBytes <= 0 || cpuQuota <= 0 || pids <= 0) {
            throw new IllegalArgumentException("Sandbox runtime and positive resource limits are required");
        }
        if (ownerLabel == null || !OWNER_LABEL.matcher(ownerLabel).matches() || containerPrefix == null || !CONTAINER_PREFIX.matcher(containerPrefix).matches()) {
            throw new IllegalArgumentException("Sandbox ownership requires a bounded label and container prefix");
        }
        writableFilesystems = Map.copyOf(writableFilesystems);
        if (writableFilesystems.size() > 16 || writableFilesystems.keySet().stream().anyMatch(path -> !path.startsWith("/") || path.equals("/") || path.contains(".."))) {
            throw new IllegalArgumentException("Sandbox writable mounts must be bounded absolute non-root paths");
        }
    }
}
