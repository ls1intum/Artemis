package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.BuildJob;
import de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild;

// Todo: Remove builagent profile as soon as database access for buildagent is unnecessary
@Profile("core || buildagent")
@Repository
public interface BuildJobRepository extends JpaRepository<BuildJob, Long> {

    List<BuildJob> findAllByCourseId(Long courseId);

    List<BuildJob> findAllByExerciseId(Long exerciseId);

    List<BuildJob> findAllByParticipationId(Long participationId);

    Optional<BuildJob> findFirstByParticipationIdOrderByBuildStartDateDesc(Long participationId);

    List<BuildJob> findAllByBuildAgentAddress(String buildAgentAddress);

    @Query("""
            SELECT new de.tum.in.www1.artemis.service.connectors.localci.dto.DockerImageBuild(
                b.dockerImage,
                max(b.buildStartDate)
            )
            FROM BuildJob b
            WHERE b.dockerImage IN :#{#dockerImageNames}
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findLastBuildDatesForDockerImages(@Param("dockerImageNames") List<String> dockerImageNames);

}
