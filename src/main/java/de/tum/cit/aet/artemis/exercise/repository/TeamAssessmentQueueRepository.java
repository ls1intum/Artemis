package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueResultDTO;
import de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueSubmissionDTO;
import de.tum.cit.aet.artemis.exercise.dto.TeamAssessmentQueueEntryDTO;

/**
 * Repository for the team assessment queue of the exercise assessment dashboard.
 * <p>
 * Every query selects scalar metadata only, so the queue never loads submitted answers, feedback or team members.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface TeamAssessmentQueueRepository extends ArtemisJpaRepository<StudentParticipation, Long> {

    /**
     * Selects the team of every participation together with its latest submission that has a manual result in the given correction round.
     *
     * @param exerciseId      the exercise id
     * @param teamOwnerId     the team owner, or null for all teams
     * @param correctionRound the displayed correction round
     * @return one entry per participation, whose submission id is null when the team has no manual result in that round
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.TeamAssessmentQueueEntryDTO(p.id, team.id, team.shortName,
                (SELECT MAX(s.id) FROM Submission s JOIN s.results r WHERE s.participation.id = p.id AND r.correctionRound = :correctionRound
                    AND r.assessmentType IN (
                        de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                        de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)
                    AND (s.submissionDate IS NULL OR s.submissionDate = (
                        SELECT MAX(newer.submissionDate) FROM Submission newer JOIN newer.results newerResult
                        WHERE newer.participation.id = p.id AND newerResult.correctionRound = :correctionRound
                            AND newerResult.assessmentType IN (
                                de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                                de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)))))
            FROM StudentParticipation p JOIN p.team team
            WHERE p.exercise.id = :exerciseId AND p.testRun = FALSE
                AND (:teamOwnerId IS NULL OR team.owner.id = :teamOwnerId)
            ORDER BY p.id
            """)
    List<TeamAssessmentQueueEntryDTO> findTeamAssessmentQueueParticipations(@Param("exerciseId") long exerciseId, @Param("teamOwnerId") Long teamOwnerId,
            @Param("correctionRound") int correctionRound);

    /**
     * Selects the latest submitted submission of every team whose deadline has passed and that has no manual result yet.
     * <p>
     * The individual due date of a participation replaces the exercise due date. For programming exercises the latest submission is the one with the latest
     * submission date, with the id breaking ties; for the other exercise types it is the one with the highest id.
     *
     * @param exerciseId  the exercise id
     * @param now         the time used to check the deadlines
     * @param teamOwnerId the team owner, or null for all teams
     * @return the metadata of the submissions that can be assessed next
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueSubmissionDTO(s.id, s.submitted, s.type, s.exampleSubmission, s.submissionDate,
                p.id, p.initializationState, p.initializationDate, p.individualDueDate, team.name,
                programming.buildFailed, programming.commitHash, text.language)
            FROM StudentParticipation p
                LEFT JOIN p.team team
                LEFT JOIN p.submissions s
                LEFT JOIN ProgrammingSubmission programming ON programming.id = s.id
                LEFT JOIN TextSubmission text ON text.id = s.id
            WHERE p.exercise.id = :exerciseId
                AND (:teamOwnerId IS NULL OR team.owner.id = :teamOwnerId)
                AND ((p.individualDueDate IS NULL AND (p.exercise.dueDate IS NULL OR p.exercise.dueDate <= :now))
                    OR p.individualDueDate <= :now)
                AND p.testRun = FALSE
                AND NOT EXISTS (
                    SELECT prs
                    FROM Submission s2 JOIN s2.results prs
                    WHERE s2.participation = p
                      AND prs.assessmentType IN (
                          de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                          de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC
                      )
                )
                AND s.submitted = TRUE
                AND s.id = (
                    SELECT MAX(s3.id) FROM p.submissions s3
                    WHERE s3.submitted = TRUE AND (programming.id IS NULL
                        OR NOT EXISTS (SELECT dated.id FROM p.submissions dated WHERE dated.submitted = TRUE AND dated.submissionDate IS NOT NULL)
                        OR s3.submissionDate = (SELECT MAX(dated.submissionDate) FROM p.submissions dated WHERE dated.submitted = TRUE))
                )
            """)
    List<AssessmentQueueSubmissionDTO> findCandidateSubmissions(@Param("exerciseId") long exerciseId, @Param("now") ZonedDateTime now, @Param("teamOwnerId") Long teamOwnerId);

    /**
     * Selects the metadata of the given team submissions.
     *
     * @param submissionIds the submission ids
     * @return the metadata of these submissions
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueSubmissionDTO(s.id, s.submitted, s.type, s.exampleSubmission, s.submissionDate,
                p.id, p.initializationState, p.initializationDate, p.individualDueDate, team.name,
                programming.buildFailed, programming.commitHash, text.language)
            FROM Submission s
                JOIN StudentParticipation p ON p.id = s.participation.id
                JOIN p.team team
                LEFT JOIN ProgrammingSubmission programming ON programming.id = s.id
                LEFT JOIN TextSubmission text ON text.id = s.id
            WHERE s.id IN :submissionIds
            """)
    List<AssessmentQueueSubmissionDTO> findSubmissions(@Param("submissionIds") List<Long> submissionIds);

    /**
     * Selects the results of the given submissions together with their assessors.
     *
     * @param submissionIds the submission ids
     * @return the metadata of the results
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.dto.AssessmentQueueResultDTO(
                r.submission.id, r.id, r.completionDate, r.successful, r.score, r.rated, r.assessmentType, r.correctionRound,
                r.hasComplaint, r.exampleResult, r.testCaseCount, r.passedTestCaseCount, r.codeIssueCount,
                assessor.id, assessor.login, assessor.firstName, assessor.lastName)
            FROM Result r
                LEFT JOIN r.assessor assessor
            WHERE r.submission.id IN :submissionIds
            """)
    List<AssessmentQueueResultDTO> findResults(@Param("submissionIds") List<Long> submissionIds);
}
