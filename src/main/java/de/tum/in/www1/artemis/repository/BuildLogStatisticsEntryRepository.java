package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry.BuildJobPartDuration;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO;

/**
 * Spring Data JPA repository for the BuildLogStatisticsEntry entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface BuildLogStatisticsEntryRepository extends JpaRepository<BuildLogStatisticsEntry, Long> {

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.BuildLogStatisticsDTO(
                COUNT(b.id),
                AVG(b.agentSetupDuration),
                AVG(b.testDuration),
                AVG(b.scaDuration),
                AVG(b.totalJobDuration),
                AVG(b.dependenciesDownloadedCount)
            )
            FROM BuildLogStatisticsEntry b
                LEFT JOIN b.programmingSubmission s
                LEFT JOIN s.participation p
            WHERE p.exercise = :exercise
                OR p = :templateParticipation
                OR p = :solutionParticipation
            """)
    BuildLogStatisticsDTO findAverageBuildLogStatistics(@Param("exercise") ProgrammingExercise exercise,
            @Param("templateParticipation") TemplateProgrammingExerciseParticipation templateParticipation,
            @Param("solutionParticipation") SolutionProgrammingExerciseParticipation solutionParticipation);

    default BuildLogStatisticsDTO findAverageBuildLogStatistics(ProgrammingExercise exercise) {
        return findAverageBuildLogStatistics(exercise, exercise.getTemplateParticipation(), exercise.getSolutionParticipation());
    }

    @Transactional // ok because of delete
    @Modifying
    void deleteByProgrammingSubmissionId(long programmingSubmissionId);

    /**
     * Generate a BuildLogStatisticsEntry from the given ZonedDateTime (and other parameters) and persist it.
     *
     * @param programmingSubmission       the submission for which the BuildLogStatisticsEntry should be generated
     * @param agentSetupDuration          the BuildJobPartDuration between the start of the build on the CI server and the completion of the agent setup. This includes e.g. pulling
     *                                        the docker images
     * @param testDuration                the BuildJobPartDuration of the test execution
     * @param scaDuration                 the BuildJobPartDuration of the SCA execution
     * @param totalJobDuration            the BuildJobPartDuration of the complete job
     * @param dependenciesDownloadedCount the number of dependencies downloaded during the build, or null (if it is not exposed through the logs)
     * @return the already persisted BuildLogStatisticsEntry
     */
    default BuildLogStatisticsEntry saveBuildLogStatisticsEntry(ProgrammingSubmission programmingSubmission, BuildJobPartDuration agentSetupDuration,
            BuildJobPartDuration testDuration, BuildJobPartDuration scaDuration, BuildJobPartDuration totalJobDuration, Integer dependenciesDownloadedCount) {

        BuildLogStatisticsEntry buildLogStatisticsEntry = new BuildLogStatisticsEntry(programmingSubmission, agentSetupDuration.durationInSeconds(),
                testDuration.durationInSeconds(), scaDuration.durationInSeconds(), totalJobDuration.durationInSeconds(), dependenciesDownloadedCount);
        return save(buildLogStatisticsEntry);
    }

}
