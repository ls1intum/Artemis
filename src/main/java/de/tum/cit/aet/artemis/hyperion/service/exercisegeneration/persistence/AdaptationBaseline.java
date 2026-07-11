package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.persistence;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import de.tum.cit.aet.artemis.hyperion.dto.GenerationMode;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;

/**
 * The head-guarded pre-run state of the latest accepted generation or adaptation. The legacy class and map names are retained so rolling deployments can read existing entries.
 */
public record AdaptationBaseline(String jobId, @Nullable GenerationMode mode, Map<RepositoryType, String> repositoryHeads, Map<RepositoryType, String> expectedCurrentHeads,
        @Nullable String problemStatement, @Nullable String title, @Nullable String expectedProblemStatement, @Nullable String expectedTitle) implements Serializable {

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
