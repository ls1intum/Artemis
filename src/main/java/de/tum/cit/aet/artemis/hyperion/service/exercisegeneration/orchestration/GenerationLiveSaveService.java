package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;

/** Coordinates durable authoring operations, not the preceding generation work. */
@Lazy
@Service
@Profile(PROFILE_CORE + " | " + PROFILE_LOCALVC)
public class GenerationLiveSaveService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GenerationLiveSaveService.class);

    public static final String MAP_NAME = de.tum.cit.aet.artemis.core.config.Constants.HYPERION_LIVE_SAVE_COORDINATION_MAP;

    private final DistributedMap<String, LiveSaveSlot> reservations;

    private final boolean enabled;

    private final String ownerNodeId = UUID.randomUUID().toString();

    private final ThreadLocal<Map<Long, HeldReservation>> held = ThreadLocal.withInitial(HashMap::new);

    /**
     * @param provider shared-store provider
     * @param enabled  deployment-wide generation setting, identical on every writer
     */
    public GenerationLiveSaveService(DistributedDataProvider provider, @Value("${artemis.hyperion.exercise-generation.enabled:false}") boolean enabled) {
        this.reservations = provider.getMap(MAP_NAME);
        this.enabled = enabled;
        if (enabled) {
            log.info("Live-save coordination owner identity: {}", ownerNodeId);
        }
    }

    /** @return whether this writer participates in live-save coordination */
    public boolean isCoordinationEnabled() {
        return enabled;
    }

    /**
     * Claims a durable operation. A crashed owner leaves its token for audited recovery.
     *
     * @param exerciseId exercise to protect
     * @return exact ownership token
     */
    public String claim(long exerciseId) {
        if (!enabled) {
            return "disabled";
        }
        var owned = held.get().get(exerciseId);
        if (owned != null) {
            verify(exerciseId, owned.token);
            owned.depth++;
            return owned.token;
        }
        String token = "live-save:" + UUID.randomUUID();
        if (reservations.putIfAbsent(String.valueOf(exerciseId), new LiveSaveSlot(token, ownerNodeId, Instant.now())) != null) {
            throw new ConflictException("An exercise save or repository copy is running. Please retry after it finishes.", "hyperionExerciseGeneration", "exerciseSaveRunning");
        }
        held.get().put(exerciseId, new HeldReservation(token));
        return token;
    }

    /**
     * @param exerciseId exercise to protect
     * @return a caller-thread reservation with automatic cleanup and explicit partial-save retention
     */
    public WriteReservation reserveWrite(long exerciseId) {
        return new WriteReservation(exerciseId, claim(exerciseId));
    }

    public final class WriteReservation implements AutoCloseable {

        private final long exerciseId;

        private final String token;

        private boolean retained;

        private WriteReservation(long exerciseId, String token) {
            this.exerciseId = exerciseId;
            this.token = token;
        }

        /** Verifies current ownership before the next durable mutation. */
        public void verify() {
            GenerationLiveSaveService.this.verify(exerciseId, token);
        }

        /** Keeps an uncertain save reserved for manual reconciliation. */
        public void retain() {
            GenerationLiveSaveService.this.retain(exerciseId, token);
            retained = true;
        }

        @Override
        public void close() {
            if (!retained) {
                release(exerciseId, token);
            }
        }
    }

    /**
     * Claims a shared repository-copy or snapshot reservation.
     *
     * @param exerciseId exercise to protect
     * @return individual reader token, or the calling writer's borrowed token
     */
    public String claimReader(long exerciseId) {
        if (!enabled) {
            return "disabled";
        }
        if (held.get().containsKey(exerciseId)) {
            return claim(exerciseId);
        }
        String token = "live-reader:" + UUID.randomUUID();
        String key = String.valueOf(exerciseId);
        while (true) {
            var current = reservations.get(key);
            if (current != null && current.readers().isEmpty()) {
                throw new ConflictException("An exercise save is running. Please retry after it finishes.", "hyperionExerciseGeneration", "exerciseSaveRunning");
            }
            var readers = new HashMap<String, String>();
            if (current != null) {
                readers.putAll(current.readers());
            }
            readers.put(token, ownerNodeId);
            var next = readerSlot(readers, current == null ? Instant.now() : current.startedAt());
            if (current == null ? reservations.putIfAbsent(key, next) == null : reservations.replace(key, current, next)) {
                return token;
            }
        }
    }

    /**
     * Releases only the matching reader while retaining concurrent copies.
     *
     * @param exerciseId protected exercise
     * @param token      individual reader or borrowed writer token
     */
    public void releaseReader(long exerciseId, String token) {
        if (!enabled) {
            return;
        }
        if (token.startsWith("live-save:")) {
            release(exerciseId, token);
            return;
        }
        String key = String.valueOf(exerciseId);
        while (true) {
            var current = reservations.get(key);
            if (current == null || !current.readers().containsKey(token)) {
                return;
            }
            var readers = new HashMap<>(current.readers());
            readers.remove(token);
            if (readers.isEmpty() ? reservations.remove(key, current) : reservations.replace(key, current, readerSlot(readers, current.startedAt()))) {
                return;
            }
        }
    }

    private static LiveSaveSlot readerSlot(Map<String, String> readers, Instant startedAt) {
        return new LiveSaveSlot("live-copy:" + UUID.randomUUID(), String.join(",", readers.values().stream().distinct().sorted().toList()), startedAt, readers);
    }

    /**
     * @param exerciseId protected exercise
     * @param token      expected writer token
     * @throws ConflictException when this operation no longer owns the reservation
     */
    public void verify(long exerciseId, String token) {
        if (enabled && getSlotInfo(exerciseId).filter(slot -> slot.token().equals(token)).isEmpty()) {
            throw new ConflictException("The exercise save reservation was lost. Stop writing and reconcile the exercise.", "hyperionExerciseGeneration",
                    "exerciseSaveOwnershipLost");
        }
    }

    /**
     * Releases the calling writer's token after its final nested operation finishes.
     *
     * @param exerciseId protected exercise
     * @param token      exact writer token
     */
    public void release(long exerciseId, String token) {
        if (!enabled) {
            return;
        }
        var owned = held.get().get(exerciseId);
        if (owned == null || !owned.token.equals(token)) {
            throw new IllegalStateException("Only the owning operation can release its exercise reservation");
        }
        if (--owned.depth == 0) {
            held.get().remove(exerciseId);
            if (held.get().isEmpty()) {
                held.remove();
            }
            if (!recoverSlot(exerciseId, token)) {
                throw new IllegalStateException("The completed exercise operation lost its reservation; manual reconciliation is required");
            }
        }
    }

    /**
     * Retains an uncertain save without lending it to the next request on this thread.
     *
     * @param exerciseId protected exercise
     * @param token      exact writer token
     */
    public void retain(long exerciseId, String token) {
        if (!enabled) {
            return;
        }
        var current = held.get().get(exerciseId);
        if (current == null || !current.token.equals(token) || current.depth != 1) {
            throw new IllegalStateException("Cannot retain an unowned or nested save reservation");
        }
        held.get().remove(exerciseId);
        if (held.get().isEmpty()) {
            held.remove();
        }
    }

    /**
     * @param exerciseId exercise to inspect
     * @return its current reservation, or empty when coordination is disabled or no operation is active
     */
    public Optional<LiveSaveSlot> getSlotInfo(long exerciseId) {
        return enabled ? Optional.ofNullable(reservations.get(String.valueOf(exerciseId))) : Optional.empty();
    }

    /**
     * Releases an exact reservation after the operator stops its owners and reconciles durable changes.
     *
     * @param exerciseId protected exercise
     * @param exactToken current reservation token from the administrator inspection
     * @return whether the matching reservation was removed
     */
    public boolean recoverSlot(long exerciseId, String exactToken) {
        if (!enabled) {
            return false;
        }
        var current = reservations.get(String.valueOf(exerciseId));
        return current != null && current.token().equals(exactToken) && reservations.remove(String.valueOf(exerciseId), current);
    }

    public record LiveSaveSlot(String token, String ownerNodeId, Instant startedAt, Map<String, String> readers) implements java.io.Serializable {

        public LiveSaveSlot(String token, String ownerNodeId, Instant startedAt) {
            this(token, ownerNodeId, startedAt, Map.of());
        }

        public LiveSaveSlot {
            readers = Map.copyOf(readers);
        }
    }

    private static final class HeldReservation {

        private final String token;

        private int depth = 1;

        private HeldReservation(String token) {
            this.token = token;
        }
    }
}
