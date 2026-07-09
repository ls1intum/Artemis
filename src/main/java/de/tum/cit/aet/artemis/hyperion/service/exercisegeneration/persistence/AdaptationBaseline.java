package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The pre-adaptation commit state of an accepted {@code ADAPT} run applied to the live repositories in place, so the instructor can later revert the adaptation. It records, per
 * repository, the commit HEAD each repository pointed at immediately before the accepted adaptation was committed over it, plus the commit HEAD immediately after the adaptation.
 * Reverting resets every repository back to the pre-adaptation commits only while the live branch still points at the captured post-adaptation commit, so a later manual edit is
 * not
 * silently clobbered. It is recorded only on the accepted-and-applied path, so a cancelled/rejected/errored run (which leaves the live repositories untouched) never overwrites it.
 * <p>
 * Serializable so it can live in the Hazelcast baseline map (keyed by exercise id); a repository with nothing committed to it (or no prior commit) is absent from
 * {@code repositoryHeads} and simply not reverted.
 *
 * @param jobId                    the adaptation job that produced this baseline (the most recent accepted run wins), retained for audit
 * @param repositoryHeads          the pre-adaptation commit HEAD per repository (template/solution/tests); a repository not committed to is omitted
 * @param expectedCurrentHeads     the post-adaptation commit HEAD per repository; a revert refuses to reset a repository whose current HEAD moved elsewhere
 * @param problemStatement         the problem statement before the adaptation, restored after every repository was reset
 * @param title                    the title before the adaptation, restored with the problem statement
 * @param expectedProblemStatement the problem statement immediately after the adaptation; a revert refuses to overwrite later manual edits
 * @param expectedTitle            the title immediately after the adaptation; a revert refuses to overwrite later manual edits
 */
public record AdaptationBaseline(String jobId, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, String> expectedCurrentHeads, @Nullable String problemStatement,
        @Nullable String title, @Nullable String expectedProblemStatement, @Nullable String expectedTitle) implements Serializable {

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

    @Nullable
    public String expectedCurrentHeadFor(RepositoryType repositoryType) {
        return expectedCurrentHeads.get(repositoryType);
    }
}
