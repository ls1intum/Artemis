package de.tum.cit.aet.artemis.programming.service;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import de.tum.cit.aet.artemis.core.exception.ConflictException;

/** State-aware storage seam for resource tests; actual database exclusion is tested separately. */
public final class ProgrammingExerciseWriteOwnershipTestFixture {

    private ProgrammingExerciseWriteOwnershipTestFixture() {
    }

    /** @return a state-aware ownership seam for resource mutation intervals */
    public static ProgrammingExerciseWriteOwnershipService create() {
        var slots = new ConcurrentHashMap<Long, ProgrammingExerciseWriteOwnershipService.Ownership>();
        var ownership = mock(ProgrammingExerciseWriteOwnershipService.class);
        when(ownership.claim(anyLong(), anyString(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            String purpose = call.getArgument(1);
            String token = call.getArgument(2);
            if (slots.putIfAbsent(id, new ProgrammingExerciseWriteOwnershipService.Ownership(token, "test-process", purpose, Instant.now(), null)) != null) {
                throw new ConflictException("Exercise write reserved", "programmingExercise", "exerciseGenerationRunning");
            }
            return token;
        });
        when(ownership.get(anyLong())).thenAnswer(call -> Optional.ofNullable(slots.get(call.getArgument(0, Long.class))));
        when(ownership.release(anyLong(), anyString())).thenAnswer(call -> {
            long id = call.getArgument(0);
            String token = call.getArgument(1);
            var current = slots.get(id);
            return current != null && current.token().equals(token) && slots.remove(id, current);
        });
        return ownership;
    }
}
