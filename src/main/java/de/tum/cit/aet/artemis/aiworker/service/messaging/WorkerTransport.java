package de.tum.cit.aet.artemis.aiworker.service.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.aiworker.api.WorkerMessageCodecApi;
import de.tum.cit.aet.artemis.aiworker.domain.WorkerEventType;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerCommandDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkerEventDTO;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/** Provider-backed worker messages. A reader removes an event only after its callback succeeds. */
public class WorkerTransport {

    private static final Duration COMMAND_TTL = Duration.ofMinutes(2);

    private static final Duration EVENT_TTL = Duration.ofHours(4);

    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(10);

    private static final int CHUNK_CHARS = 512 * 1024;

    private static final int MAX_DELIVERY_FAILURES = 5;

    private static final long MAX_OUTSTANDING_BYTES_PER_WORKER = 256L * 1024 * 1024;

    private final WorkerMessageCodecApi codec;

    private final DistributedDataProvider provider;

    private volatile Maps cachedMaps;

    public WorkerTransport(DistributedDataProvider provider, WorkerMessageCodecApi codec) {
        this.provider = provider;
        this.codec = codec;
    }

    private Maps maps() {
        Maps current = cachedMaps;
        if (current == null) {
            synchronized (this) {
                current = cachedMaps;
                if (current == null) {
                    current = new Maps(provider.getExpiringMap("aiworker-commands", COMMAND_TTL), provider.getExpiringMap("aiworker-events", EVENT_TTL),
                            provider.getExpiringMap("aiworker-event-chunks", EVENT_TTL), provider.getExpiringMap("aiworker-heartbeats", HEARTBEAT_TTL),
                            provider.getExpiringMap("aiworker-dead-commands", Duration.ofHours(24)), provider.getExpiringMap("aiworker-command-failures", COMMAND_TTL));
                    cachedMaps = current;
                }
            }
        }
        return current;
    }

    public void send(WorkerCommandDTO command) {
        Maps maps = maps();
        String key = command.identity().workerId() + ":" + command.identity().executionId() + ":" + command.type();
        maps.commands().put(key, codec.encode(command));
    }

    /**
     * Polls this worker's commands. Failed admission leaves a command for retry or dead-letter handling.
     *
     * @param workerId           this worker's configured ID
     * @param currentIncarnation whether a command targets this process
     * @param accept             local command admission
     */
    public void receiveCommands(String workerId, Predicate<WorkerCommandDTO> currentIncarnation, Consumer<WorkerCommandDTO> accept) {
        Maps maps = maps();
        String prefix = workerId + ":";
        maps.commands().keySet().stream().filter(key -> key.startsWith(prefix)).sorted(Comparator.comparingInt(WorkerTransport::commandOrder)).forEach(key -> {
            String body = maps.commands().get(key);
            if (body == null) {
                return;
            }
            try {
                WorkerCommandDTO command = codec.decodeCommand(body);
                if (!workerId.equals(command.identity().workerId())) {
                    throw new IllegalArgumentException("Command has a different worker identity");
                }
                if (!currentIncarnation.test(command)) {
                    return;
                }
                accept.accept(command);
                maps.commands().remove(key, body);
                maps.commandFailures().remove(key);
            }
            catch (RuntimeException failure) {
                Integer count = maps.commandFailures().get(key);
                int next = count == null ? 1 : count + 1;
                maps.commandFailures().put(key, next);
                if (next >= MAX_DELIVERY_FAILURES) {
                    maps.deadCommands().put(key, body);
                    maps.commands().remove(key, body);
                    maps.commandFailures().remove(key);
                }
                else {
                    throw failure;
                }
            }
        });
    }

    private static int commandOrder(String key) {
        return key.endsWith(":START") ? 0 : key.endsWith(":RENEW") ? 2 : 1;
    }

    /**
     * Stores one event before reporting successful publication.
     *
     * @param event the worker event
     */
    public void publish(WorkerEventDTO event) {
        Maps maps = maps();
        String body = codec.encode(event);
        if (event.type() == WorkerEventType.HEARTBEAT) {
            maps.heartbeats().put(event.workerId(), body);
            return;
        }
        ExecutionIdentityDTO identity = event.identity();
        if (identity == null) {
            throw new IllegalArgumentException("Execution event has no identity");
        }
        String key = eventKey(event);
        int bytes = body.getBytes(StandardCharsets.UTF_8).length;
        String descriptor = body.length() + ":" + ((body.length() + CHUNK_CHARS - 1) / CHUNK_CHARS) + ":" + sha256(body) + ":" + bytes;
        String writing = "W:" + descriptor;
        String ready = "R:" + descriptor;
        String acknowledged = "A:" + descriptor;
        maps.events().lock(event.workerId());
        String existing;
        try {
            existing = maps.events().get(key);
            if (existing == null) {
                long outstanding = maps.events().entrySet().stream().filter(entry -> entry.getKey().startsWith(event.workerId() + ":") && !entry.getValue().startsWith("A:"))
                        .mapToLong(entry -> Long.parseLong(entry.getValue().substring(entry.getValue().lastIndexOf(':') + 1))).sum();
                if (outstanding + bytes > MAX_OUTSTANDING_BYTES_PER_WORKER) {
                    throw new IllegalStateException("Worker event storage is full");
                }
                existing = maps.events().putIfAbsent(key, writing);
            }
        }
        finally {
            maps.events().unlock(event.workerId());
        }
        if (ready.equals(existing) || acknowledged.equals(existing)) {
            return;
        }
        if (existing != null && !writing.equals(existing)) {
            throw new IllegalStateException("Worker event sequence was reused with different content");
        }
        int count = (body.length() + CHUNK_CHARS - 1) / CHUNK_CHARS;
        for (int index = 0; index < count; index++) {
            int start = index * CHUNK_CHARS;
            maps.chunks().put(key + ":" + index, body.substring(start, Math.min(start + CHUNK_CHARS, body.length())));
        }
        maps.events().lock(key);
        try {
            String current = maps.events().get(key);
            if (acknowledged.equals(current)) {
                return;
            }
            if (!writing.equals(current) && !ready.equals(current)) {
                throw new IllegalStateException("Worker event reservation changed during publication");
            }
            maps.events().put(key, ready);
        }
        finally {
            maps.events().unlock(key);
        }
    }

    /**
     * Applies one exact execution event. Failure leaves its manifest and chunks for retry.
     *
     * @param identity the claimed execution
     * @param apply    the core callback
     * @return whether an event was applied
     */
    public boolean receive(ExecutionIdentityDTO identity, Consumer<WorkerEventDTO> apply) {
        Maps maps = maps();
        String prefix = identity.workerId() + ":" + identity.executionId() + ":";
        String key = maps.events().keySet().stream().filter(candidate -> candidate.startsWith(prefix) && !String.valueOf(maps.events().get(candidate)).startsWith("A:"))
                .min(String::compareTo).orElse(null);
        if (key == null) {
            return false;
        }
        int count;
        maps.events().lock(key);
        try {
            String manifest = maps.events().get(key);
            if (manifest == null || !manifest.startsWith("R:")) {
                return false;
            }
            String[] parts = manifest.substring(2).split(":", 4);
            int length = Integer.parseInt(parts[0]);
            count = Integer.parseInt(parts[1]);
            if (length < 1 || length > WorkerMessageCodecApi.MAX_MESSAGE_CHARS || count < 1 || count > (WorkerMessageCodecApi.MAX_MESSAGE_CHARS / CHUNK_CHARS) + 1) {
                throw new IllegalArgumentException("Invalid worker event manifest");
            }
            StringBuilder body = new StringBuilder(length);
            for (int index = 0; index < count; index++) {
                String chunk = maps.chunks().get(key + ":" + index);
                if (chunk == null) {
                    return false;
                }
                body.append(chunk);
            }
            if (body.length() != length || !sha256(body.toString()).equals(parts[2])) {
                throw new IllegalStateException("Worker event chunks do not match their manifest");
            }
            WorkerEventDTO event = codec.decodeEvent(body.toString());
            if (!identity.equals(event.identity())) {
                throw new IllegalArgumentException("Worker event does not match its execution");
            }
            apply.accept(event);
            maps.events().put(key, "A:" + manifest.substring(2));
        }
        finally {
            maps.events().unlock(key);
        }
        for (int index = 0; index < count; index++) {
            maps.chunks().remove(key + ":" + index);
        }
        return true;
    }

    @Nullable
    public WorkerEventDTO heartbeat(String workerId) {
        Maps maps = maps();
        String body = maps.heartbeats().get(workerId);
        return body == null ? null : codec.decodeEvent(body);
    }

    private static String eventKey(WorkerEventDTO event) {
        return event.workerId() + ":" + event.identity().executionId() + ":" + String.format(java.util.Locale.ROOT, "%019d", event.sequence());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private record Maps(DistributedMap<String, String> commands, DistributedMap<String, String> events, DistributedMap<String, String> chunks,
            DistributedMap<String, String> heartbeats, DistributedMap<String, String> deadCommands, DistributedMap<String, Integer> commandFailures) {
    }

}
