package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.BuildJob;

public interface BuildJobRepository extends JpaRepository<BuildJob, Long> {

    List<BuildJob> findAllByCourseId(Long courseId);

    List<BuildJob> findAllByExerciseId(Long exerciseId);

    List<BuildJob> findAllByParticipationId(Long participationId);

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    List<BuildJob> findAllByBuildAgentAddress(String buildAgentAddress);

    @Query("""
            SELECT b FROM BuildJob b
            WHERE b.dockerImage = :#{#dockerImage}
            AND b.buildStartDate = (SELECT max(b2.buildStartDate) FROM BuildJob b2 WHERE b2.dockerImage = :#{#dockerImage})
            """)
    Optional<BuildJob> findLatestBuildJobByDockerImage(@Param("dockerImage") String dockerImage);

}
