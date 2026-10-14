package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseWriteOwnershipService.Ownership;

/** Ownership protocol double; native SQL exclusion is checked separately against PostgreSQL and MySQL. */
public class GenerationWriteOwnershipTestFixture {

    private final Map<Long, Ownership> writers = new java.util.concurrent.ConcurrentHashMap<>();

    private final Map<Long, Map<String, Ownership>> readers = new java.util.concurrent.ConcurrentHashMap<>();

    public final ProgrammingExerciseWriteOwnershipService database = mock(ProgrammingExerciseWriteOwnershipService.class);

    public final GenerationExternalMutationService service = org.mockito.Mockito.spy(new GenerationExternalMutationService(database));

    public GenerationWriteOwnershipTestFixture() {
        when(database.claim(anyLong(), anyString(), anyString())).thenAnswer(call -> claim(call.getArgument(0), call.getArgument(1), call.getArgument(2)));
        when(database.claim(anyLong(), anyString())).thenAnswer(call -> claim(call.getArgument(0), call.getArgument(1), UUID.randomUUID().toString()));
        when(database.get(anyLong())).thenAnswer(call -> Optional.ofNullable(writers.get(call.getArgument(0))));
        when(database.release(anyLong(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            var current = writers.get(id);
            return current != null && current.token().equals(call.getArgument(1)) && writers.remove(id, current);
        });
        when(database.releaseActiveSave(anyLong(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            var current = writers.get(id);
            return current != null && current.token().equals(call.getArgument(1)) && current.purpose().equals("SAVE") && writers.remove(id, current);
        });
        when(database.retain(anyLong(), anyString(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            var current = writers.get(id);
            if (current == null || !current.token().equals(call.getArgument(1))) {
                return false;
            }
            writers.put(id, slot(current.token(), call.getArgument(2)));
            return true;
        });
        when(database.replaceRecovery(anyLong(), anyString(), anyString(), anyString(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            var current = writers.get(id);
            if (current == null || !current.token().equals(call.getArgument(1)) || !current.purpose().equals(call.getArgument(2))) {
                return false;
            }
            writers.put(id, slot(call.getArgument(3), call.getArgument(4)));
            return true;
        });
        when(database.claimReader(anyLong())).thenAnswer(call -> {
            long id = call.getArgument(0);
            if (writers.containsKey(id)) {
                throw conflict();
            }
            String token = UUID.randomUUID().toString();
            readers.computeIfAbsent(id, _ -> new java.util.concurrent.ConcurrentHashMap<>()).put(token, slot(token, "COPY"));
            return token;
        });
        when(database.releaseReader(anyLong(), anyString()))
                .thenAnswer(call -> readers.computeIfAbsent(call.getArgument(0), _ -> new java.util.concurrent.ConcurrentHashMap<>()).remove(call.getArgument(1)) != null);
        when(database.readers(anyLong())).thenAnswer(call -> java.util.List.copyOf(readers.getOrDefault(call.getArgument(0), Map.of()).values()));
    }

    private synchronized String claim(long id, String purpose, String token) {
        if (writers.containsKey(id) || !readers.getOrDefault(id, Map.of()).isEmpty()) {
            throw conflict();
        }
        writers.put(id, slot(token, purpose));
        return token;
    }

    private static Ownership slot(String token, String purpose) {
        return new Ownership(token, "fixture-process", purpose, Instant.now(), null);
    }

    private static ConflictException conflict() {
        return new ConflictException("Another operation owns the exercise reservation", "programmingExercise", "exerciseGenerationRunning");
    }
}
