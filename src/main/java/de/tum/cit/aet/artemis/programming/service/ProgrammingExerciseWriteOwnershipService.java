package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.ServiceUnavailableAlertException;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseWriteOwnershipRepository;

/** Coordinates brief exercise writes through durable database reservations, independent of cache topology. */
@Lazy
@Service
@Profile({ PROFILE_CORE, PROFILE_LOCALVC })
public class ProgrammingExerciseWriteOwnershipService {

    private final ProgrammingExerciseWriteOwnershipRepository repository;

    private final String processIncarnation = UUID.randomUUID().toString();

    public ProgrammingExerciseWriteOwnershipService(ProgrammingExerciseWriteOwnershipRepository repository) {
        this.repository = repository;
    }

    /**
     * Claims a write reservation. A lost database response is accepted only after authoritative token readback.
     *
     * @param exerciseId protected exercise
     * @param purpose    operation kind
     * @return acquired token
     */
    public String claim(long exerciseId, String purpose) {
        return claim(exerciseId, purpose, UUID.randomUUID().toString());
    }

    /**
     * Claims with a caller-generated operation token, which permits exact readback after an uncertain response.
     *
     * @param exerciseId protected exercise
     * @param purpose    operation kind
     * @param token      caller-generated unique operation token
     * @return the acquired token
     */
    public String claim(long exerciseId, String purpose, String token) {
        validatePurpose(purpose);
        validateToken(token);
        int claimed;
        try {
            claimed = repository.claim(exerciseId, token, processIncarnation, purpose, Instant.now());
        }
        catch (RuntimeException uncertainClaim) {
            try {
                if (get(exerciseId).filter(ownership -> ownership.token().equals(token) && ownership.owner().equals(processIncarnation) && ownership.purpose().equals(purpose))
                        .isPresent()) {
                    return token;
                }
            }
            catch (RuntimeException unavailableReadback) {
                uncertainClaim.addSuppressed(unavailableReadback);
            }
            // Never replace or expire an uncertain reservation. The database remains authoritative.
            throw new ServiceUnavailableAlertException("Exercise write ownership could not be confirmed; retry after checking the reservation.", "programmingExercise",
                    "exerciseWriteOwnershipUnavailable");
        }
        if (claimed != 1) {
            throw new ConflictException("Another operation owns the exercise write reservation.", "programmingExercise", "exerciseGenerationRunning");
        }
        return token;
    }

    /**
     * Releases the matching token, returning false if the reservation is absent or belongs to another operation.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @return whether the matching reservation was released
     */
    public boolean release(long exerciseId, String token) {
        validateToken(token);
        return repository.release(exerciseId, token) == 1;
    }

    /**
     * Retains the current token for partial-write or uncertain-write reconciliation.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @param purpose    recovery operation kind
     * @return whether the matching reservation was retained
     */
    public boolean retain(long exerciseId, String token, String purpose) {
        validateToken(token);
        validatePurpose(purpose);
        return repository.retain(exerciseId, token, purpose) == 1;
    }

    /**
     * Transfers an already reconciled recovery reservation; authorization belongs to the caller.
     *
     * @param exerciseId      protected exercise
     * @param expectedToken   reconciled reservation token
     * @param expectedPurpose expected recovery kind
     * @param newToken        retry operation token
     * @param newPurpose      retry operation kind
     * @return whether the matching recovery reservation was transferred
     */
    public boolean replaceRecovery(long exerciseId, String expectedToken, String expectedPurpose, String newToken, String newPurpose) {
        validateToken(expectedToken);
        validateToken(newToken);
        validatePurpose(expectedPurpose);
        validatePurpose(newPurpose);
        return repository.replaceRecovery(exerciseId, expectedToken, expectedPurpose, newToken, newPurpose, processIncarnation, Instant.now()) == 1;
    }

    /**
     * Authorizes the test-build callback for one exact repository commit under this reservation.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @param commitHash authorized tests repository commit
     * @return whether the matching reservation was updated
     */
    public boolean authorizeTestsBuild(long exerciseId, String token, String commitHash) {
        validateToken(token);
        if (commitHash == null || commitHash.isBlank() || commitHash.length() > 64) {
            throw new IllegalArgumentException("Test commit hash must contain 1 to 64 characters");
        }
        return repository.authorizeTestsBuild(exerciseId, token, commitHash) == 1;
    }

    private static void validateToken(String token) {
        if (token == null || token.isBlank() || token.length() > 64) {
            throw new IllegalArgumentException("Write ownership token must contain 1 to 64 characters");
        }
    }

    private static void validatePurpose(String purpose) {
        if (purpose == null || purpose.isBlank() || purpose.length() > 32) {
            throw new IllegalArgumentException("Write ownership purpose must contain 1 to 32 characters");
        }
    }

    /**
     * Checks the database rather than a process-local or distributed cache.
     *
     * @param exerciseId protected exercise
     * @param token      expected operation token
     * @return whether that token currently owns the exercise
     */
    public boolean isOwned(long exerciseId, String token) {
        return get(exerciseId).map(ownership -> ownership.token().equals(token)).orElse(false);
    }

    /**
     * Returns the durable reservation for status and exact-token operator reconciliation.
     *
     * @param exerciseId protected exercise
     * @return current durable reservation, if any
     */
    public Optional<Ownership> get(long exerciseId) {
        return repository.findOwnership(exerciseId)
                .map(row -> new Ownership(row.getToken(), row.getOwner(), row.getPurpose(), row.getStartedAt(), row.getExpectedTestsCommitHash()));
    }

    /**
     * Claims a durable source-copy reservation while allowing other copies.
     *
     * @param exerciseId source exercise
     * @return exact copy token
     */
    public String claimReader(long exerciseId) {
        String token = UUID.randomUUID().toString();
        try {
            if (repository.claimReader(exerciseId, token, processIncarnation, Instant.now()) == 1) {
                return token;
            }
        }
        catch (RuntimeException uncertainClaim) {
            try {
                if (readers(exerciseId).stream().anyMatch(reader -> reader.token().equals(token) && reader.owner().equals(processIncarnation))) {
                    return token;
                }
            }
            catch (RuntimeException unavailableReadback) {
                uncertainClaim.addSuppressed(unavailableReadback);
            }
            throw new ServiceUnavailableAlertException("Exercise copy ownership could not be confirmed.", "programmingExercise", "exerciseWriteOwnershipUnavailable");
        }
        throw new ConflictException("The exercise is reserved for a write.", "programmingExercise", "exerciseGenerationRunning");
    }

    /**
     * Releases only the exact copy operation; there is no time-based takeover.
     *
     * @param exerciseId source exercise
     * @param token      exact copy token
     * @return whether this copy was released
     */
    public boolean releaseReader(long exerciseId, String token) {
        validateToken(token);
        return repository.releaseReader(exerciseId, token) == 1;
    }

    /**
     * Reads durable copy owners without consulting cache membership.
     *
     * @param exerciseId source exercise
     * @return current copy reservations
     */
    public List<Ownership> readers(long exerciseId) {
        return repository.findReaders(exerciseId).stream().map(row -> new Ownership(row.getToken(), row.getOwner(), row.getPurpose(), row.getStartedAt(), null)).toList();
    }

    /** Values identifying one write operation and the process that claimed it. */
    public record Ownership(String token, String owner, String purpose, Instant startedAt, @Nullable String expectedTestsCommitHash) {
    }
}
