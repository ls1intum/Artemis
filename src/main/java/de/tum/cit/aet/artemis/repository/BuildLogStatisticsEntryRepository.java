package de.tum.cit.aet.artemis.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.domain.statistics.BuildLogStatisticsEntry.BuildJobPartDuration;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.domain.statistics.BuildLogStatisticsEntry;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.web.rest.dto.BuildLogStatisticsDTO;

/**
 * Spring Data JPA repository for the BuildLogStatisticsEntry entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface BuildLogStatisticsEntryRepository extends ArtemisJpaRepository<BuildLogStatisticsEntry, Long> {

    @Query("""
            SELECT new de.tum.cit.aet.artemis.web.rest.dto.BuildLogStatisticsDTO(
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
                OR p.id = :templateParticipationId
                OR p.id = :solutionParticipationId
            """)
    BuildLogStatisticsDTO findAverageBuildLogStatistics(@Param("exercise") ProgrammingExercise exercise, @Param("templateParticipationId") Long templateParticipationId,
            @Param("solutionParticipationId") Long solutionParticipationId);

    default BuildLogStatisticsDTO findAverageBuildLogStatistics(ProgrammingExercise exercise) {
        return findAverageBuildLogStatistics(exercise, exercise.getTemplateParticipation() != null ? exercise.getTemplateParticipation().getId() : null,
                exercise.getSolutionParticipation() != null ? exercise.getSolutionParticipation().getId() : null);
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
