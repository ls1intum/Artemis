package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import de.tum.in.www1.artemis.domain.BuildJob;

public interface BuildJobRepository extends JpaRepository<BuildJob, Long> {

    List<BuildJob> findAllByCourseId(Long courseId);

    List<BuildJob> findAllByExerciseId(Long exerciseId);

    List<BuildJob> findAllByParticipationId(Long participationId);

    List<BuildJob> findAllByBuildAgentAddress(String buildAgentAddress);

    @Query("SELECT buildjob FROM BuildJob buildjob WHERE buildjob.dockerImage = :dockerImage ORDER BY buildjob.buildStartDate DESC")
    Optional<BuildJob> findTopByDockerImageByBuildStartDateDesc(String dockerImage);

}
