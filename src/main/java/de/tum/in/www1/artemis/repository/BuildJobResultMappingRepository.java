package de.tum.in.www1.artemis.repository;

import java.util.Optional;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.BuildJobResultMapping;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

@Profile(Constants.PROFILE_CORE)
@Repository
public interface BuildJobResultMappingRepository extends JpaRepository<BuildJobResultMapping, Long> {

    Optional<BuildJobResultMapping> findByBuildJobId(String buildJobId);

    Optional<BuildJobResultMapping> findByResultId(Long resultId);

    @NotNull
    default BuildJobResultMapping findByBuildJobIdOrElseThrow(String buildJobId) {
        return findByBuildJobId(buildJobId).orElseThrow(() -> new EntityNotFoundException("BuildJobResultMapping with buildJobId " + buildJobId + " does not exist"));
    }

    @NotNull
    default BuildJobResultMapping findByResultIdOrElseThrow(Long resultId) {
        return findByResultId(resultId).orElseThrow(() -> new EntityNotFoundException("BuildJobResultMapping with resultId " + resultId + " does not exist"));
    }

}
