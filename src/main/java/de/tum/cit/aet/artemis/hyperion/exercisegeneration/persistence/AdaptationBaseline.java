package de.tum.cit.aet.artemis.hyperion.exercisegeneration.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The pre-adaptation commit state of an ACCEPTED {@code ADAPT} run applied to the live repositories in place, so the instructor can later revert the adaptation. It records, per
 * repository, the commit HEAD each repository pointed at immediately BEFORE the accepted adaptation was committed over it. Reverting resets every repository back to these commits.
 * It is recorded only on the accepted-and-applied path, so a cancelled/rejected/errored run (which leaves the live repositories untouched) never overwrites it.
 * <p>
 * Serializable so it can live in the Hazelcast baseline map; a repository with nothing committed to it (or no prior commit) is absent from {@code repositoryHeads} and simply not
 * reverted.
 *
 * @param jobId           the adaptation job that produced this baseline (the most recent accepted run wins)
 * @param userLogin       the instructor who performed the adaptation (for audit)
 * @param exerciseId      the adapted exercise
 * @param repositoryHeads the pre-adaptation commit HEAD per repository (template/solution/tests); a repository not committed to is omitted
 * @param capturedAt      when the baseline was recorded (at persist, on acceptance)
 */
public record AdaptationBaseline(String jobId, String userLogin, long exerciseId, Map<RepositoryType, String> repositoryHeads, Instant capturedAt) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @param repositoryType the repository whose captured pre-run HEAD is requested
     * @return the captured commit hash, or {@code null} when none was recorded for this repository
     */
    @Nullable
    public String headFor(RepositoryType repositoryType) {
        return repositoryHeads.get(repositoryType);
    }
}
