package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildLogStatisticsEntry.BuildJobPartDuration;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.build.BuildLogStatisticsEntry;
import de.tum.cit.aet.artemis.programming.dto.BuildLogStatisticsDTO;

/**
 * Spring Data JPA repository for the BuildLogStatisticsEntry entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface BuildLogStatisticsEntryRepository extends ArtemisJpaRepository<BuildLogStatisticsEntry, Long> {

    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.BuildLogStatisticsDTO(
                COUNT(b.id),
                AVG(b.agentSetupDuration),
                AVG(b.testDuration),
                AVG(b.scaDuration),
                AVG(b.totalJobDuration),
                AVG(b.dependenciesDownloadedCount)
            )
            FROM BuildLogStatisticsEntry b
            WHERE b.programmingSubmission.participation.exercise = :exercise
            """)
    BuildLogStatisticsDTO findAverageStudentBuildLogStatistics(@Param("exercise") ProgrammingExercise exercise);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.BuildLogStatisticsDTO(
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
            WHERE p.id = :templateParticipationId
                OR p.id = :solutionParticipationId
            """)
    BuildLogStatisticsDTO findAverageExerciseBuildLogStatistics(@Param("templateParticipationId") Long templateParticipationId,
            @Param("solutionParticipationId") Long solutionParticipationId);

    /**
     * Find the average build log statistics for the given exercise. If the exercise has a template or solution participation, the statistics are also calculated for these
     * NOTE: we cannot calculate this within one query, this would be way too slow, therefore, we split it into multiple queries and combine the result
     *
     * @param exercise the exercise for which the statistics should be calculated
     * @return the average build log statistics
     */
    default BuildLogStatisticsDTO findAverageBuildLogStatistics(ProgrammingExercise exercise) {
        var studentStatistics = findAverageStudentBuildLogStatistics(exercise);
        var exerciseStatistics = findAverageExerciseBuildLogStatistics(exercise.getTemplateParticipation() != null ? exercise.getTemplateParticipation().getId() : null,
                exercise.getSolutionParticipation() != null ? exercise.getSolutionParticipation().getId() : null);
        // build the average of two values based on the count
        var studentCount = studentStatistics.buildCount();
        var exerciseCount = exerciseStatistics.buildCount();
        var count = studentCount + exerciseCount;
        return new BuildLogStatisticsDTO(count,
                count == 0 ? 0.0 : (studentStatistics.agentSetupDuration() * studentCount + exerciseStatistics.agentSetupDuration() * exerciseCount) / count,
                count == 0 ? 0.0 : (studentStatistics.testDuration() * studentCount + exerciseStatistics.testDuration() * exerciseCount) / count,
                count == 0 ? 0.0 : (studentStatistics.scaDuration() * studentCount + exerciseStatistics.scaDuration() * exerciseCount) / count,
                count == 0 ? 0.0 : (studentStatistics.totalJobDuration() * studentCount + exerciseStatistics.totalJobDuration() * exerciseCount) / count,
                count == 0 ? 0.0 : (studentStatistics.dependenciesDownloadedCount() * studentCount + exerciseStatistics.dependenciesDownloadedCount() * exerciseCount) / count);

    }

    @Transactional // required due to delete
    @Modifying
    @Query("""
            DELETE FROM BuildLogStatisticsEntry e
            WHERE e.programmingSubmission.participation.id = :participationId
            """)
    void deleteByParticipationId(@Param("participationId") Long participationId);

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
