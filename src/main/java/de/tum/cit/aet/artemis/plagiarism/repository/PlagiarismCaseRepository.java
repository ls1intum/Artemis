package de.tum.cit.aet.artemis.plagiarism.repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDetailDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismSubmissionForCaseDTO;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Conditional(PlagiarismEnabled.class)
@Lazy
@Repository
public interface PlagiarismCaseRepository extends ArtemisJpaRepository<PlagiarismCase, Long> {

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmission
            WHERE plagiarismCase.student.login = :studentLogin
                AND plagiarismCase.exercise.id = :exerciseId
            """)
    Optional<PlagiarismCase> findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(@Param("studentLogin") String studentLogin, @Param("exerciseId") Long exerciseId);

    /**
     * Finds all plagiarism cases (with their submissions eagerly loaded) whose course ended before the given date. The
     * relevant course is reached directly for course exercises and via the exam for exam exercises, so exam plagiarism
     * cases (which are just as grade-relevant) are included in the retention cleanup rather than being retained forever.
     * Used by the data-privacy cleanup to remove plagiarism cases once their retention period elapsed.
     *
     * @param endDateBefore only cases of courses that ended strictly before this are returned
     * @return the matching plagiarism cases with their submissions initialized
     */
    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions
                JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
            WHERE COALESCE(course.endDate, examCourse.endDate) < :endDateBefore
            """)
    List<PlagiarismCase> findWithSubmissionsByCourseEndDateBefore(@Param("endDateBefore") ZonedDateTime endDateBefore);

    /**
     * Counts the plagiarism cases whose course ended before the given date (course exercises via their course, exam
     * exercises via their exam's course).
     *
     * @param endDateBefore only cases of courses that ended strictly before this are counted
     * @return the number of matching plagiarism cases
     */
    @Query("""
            SELECT COUNT(DISTINCT plagiarismCase)
            FROM PlagiarismCase plagiarismCase
                JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
            WHERE COALESCE(course.endDate, examCourse.endDate) < :endDateBefore
            """)
    int countByCourseEndDateBefore(@Param("endDateBefore") ZonedDateTime endDateBefore);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
                    exercise.shortName,
                    TYPE(exercise),
                    exercise.dueDate,
                    COALESCE(course.id, examCourse.id),
                    COALESCE(course.title, examCourse.title),
                    exam.id,
                    exam.title,
                    plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod
                ),
                student.id,
                student.login,
                student.firstName,
                student.lastName,
                post.id,
                post.creationDate,
                CASE WHEN EXISTS (
                    SELECT 1
                    FROM AnswerPost studentAnswer
                    WHERE studentAnswer.post = post
                        AND studentAnswer.author = student
                ) THEN TRUE ELSE FALSE END,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.firstName,
                verdictBy.lastName,
                (
                    SELECT COUNT(plagiarismSubmission.id)
                    FROM PlagiarismSubmission plagiarismSubmission
                    WHERE plagiarismSubmission.plagiarismCase = plagiarismCase
                ),
                plagiarismCase.createdByContinuousPlagiarismControl
            )
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.student student
                LEFT JOIN plagiarismCase.verdictBy verdictBy
                LEFT JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.plagiarismDetectionConfig plagiarismDetectionConfig
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
                LEFT JOIN plagiarismCase.post post
            WHERE exercise.course.id = :courseId
            """)
    List<PlagiarismCaseOverviewDTO> findOverviewDtosByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
                    exercise.shortName,
                    TYPE(exercise),
                    exercise.dueDate,
                    COALESCE(course.id, examCourse.id),
                    COALESCE(course.title, examCourse.title),
                    exam.id,
                    exam.title,
                    plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod
                ),
                student.id,
                student.login,
                student.firstName,
                student.lastName,
                post.id,
                post.creationDate,
                CASE WHEN EXISTS (
                    SELECT 1
                    FROM AnswerPost studentAnswer
                    WHERE studentAnswer.post = post
                        AND studentAnswer.author = student
                ) THEN TRUE ELSE FALSE END,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.firstName,
                verdictBy.lastName,
                (
                    SELECT COUNT(plagiarismSubmission.id)
                    FROM PlagiarismSubmission plagiarismSubmission
                    WHERE plagiarismSubmission.plagiarismCase = plagiarismCase
                ),
                plagiarismCase.createdByContinuousPlagiarismControl
            )
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.student student
                LEFT JOIN plagiarismCase.verdictBy verdictBy
                LEFT JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.plagiarismDetectionConfig plagiarismDetectionConfig
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
                LEFT JOIN plagiarismCase.post post
            WHERE exam.id = :examId
            """)
    List<PlagiarismCaseOverviewDTO> findOverviewDtosByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post p
            WHERE plagiarismCase.exercise.id = :exerciseId
                AND plagiarismCase.student.id = :userId
                AND p.id IS NOT NULL
            """)
    Optional<PlagiarismCase> findByStudentIdAndExerciseIdWithPost(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.team.students teamStudent
                LEFT JOIN FETCH plagiarismCase.post p
                LEFT JOIN FETCH p.answers
            WHERE plagiarismCase.exercise.id = :exerciseId
                AND (plagiarismCase.student.id = :userId OR teamStudent.id = :userId)
                AND p.id IS NOT NULL
            """)
    Optional<PlagiarismCase> findByStudentIdAndExerciseIdWithPostAndAnswerPost(@Param("userId") Long userId, @Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId
            """)
    List<PlagiarismCase> findByExamId(@Param("examId") Long examId);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId
                AND plagiarismCase.student.id = :studentId
            """)
    List<PlagiarismCase> findByExamIdAndStudentId(@Param("examId") Long examId, @Param("studentId") Long studentId);

    // The left join fetches are done on ManyToOne relationships to avoid that Hibernate fetches
    @Query("""
            SELECT DISTINCT p
            FROM PlagiarismCase p
                LEFT JOIN FETCH p.student
                LEFT JOIN FETCH p.exercise
                LEFT JOIN FETCH p.team
                LEFT JOIN FETCH p.post
            WHERE p.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findByCourseId(@Param("courseId") Long courseId);

    // The left join fetches are done on ManyToOne relationships to avoid that Hibernate fetches
    @Query("""
            SELECT DISTINCT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDTO(p.id, p.verdict, p.student.id)
            FROM PlagiarismCase p
            WHERE p.exercise.course.id = :courseId
            """)
    List<PlagiarismCaseDTO> findPlagiarismCaseDtoByCourseId(@Param("courseId") Long courseId);

    /**
     * The plagiarism cases affecting one student in a course, including those attached to their team.
     * <p>
     * Fetches the team and its members for the same reason as {@link #findByStudentIdAndExerciseIds}: consumers resolve
     * a team case through {@code PlagiarismCase#getStudents()}, which walks {@code team.students}. The filtering join
     * is kept separate from the fetch joins so that restricting to the requesting user cannot truncate the fetched
     * membership.
     *
     * @param courseId  the course to look in
     * @param studentId the student whose cases are wanted
     * @return the student's plagiarism cases, individual and team
     */
    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.team.students filterStudent
                LEFT JOIN FETCH plagiarismCase.team fetchedTeam
                LEFT JOIN FETCH fetchedTeam.students
            WHERE plagiarismCase.exercise.course.id = :courseId
                AND (plagiarismCase.student.id = :studentId OR filterStudent.id = :studentId)
            """)
    List<PlagiarismCase> findByCourseIdAndStudentId(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    /**
     * The plagiarism cases affecting one student in the given exercises, including those attached to their team.
     * <p>
     * The team and its members are fetched, not merely joined: consumers resolve a team case to its members through
     * {@code PlagiarismCase#getStudents()}, which walks {@code team.students} and would otherwise fail outside a
     * session. The filtering join is kept separate from the fetch joins so that restricting to the requesting user
     * cannot truncate the fetched membership.
     *
     * @param userId      the student whose cases are wanted
     * @param exerciseIds the exercises to look in
     * @return the student's plagiarism cases, individual and team
     */
    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.team.students filterStudent
                LEFT JOIN FETCH plagiarismCase.team fetchedTeam
                LEFT JOIN FETCH fetchedTeam.students
            WHERE plagiarismCase.exercise.id IN :exerciseIds
                AND (plagiarismCase.student.id = :userId OR filterStudent.id = :userId)
            """)
    List<PlagiarismCase> findByStudentIdAndExerciseIds(@Param("userId") Long userId, @Param("exerciseIds") Set<Long> exerciseIds);

    /**
     * Projects only the plagiarism fields that affect one student's course score. Team cases are resolved to the
     * requesting team member in the query, so the calculator receives the same shape for individual and team exercises.
     *
     * @param userId      the requesting student
     * @param exerciseIds the visible course exercises
     * @return score-relevant plagiarism cases for the student
     */
    @Query("""
            SELECT DISTINCT NEW de.tum.cit.aet.artemis.plagiarism.api.dtos.PlagiarismCaseScoreDTO(
                COALESCE(plagiarismCase.student.id, teamStudent.id),
                plagiarismCase.exercise.id,
                plagiarismCase.verdict,
                plagiarismCase.verdictPointDeduction)
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.team.students teamStudent
            WHERE plagiarismCase.exercise.id IN :exerciseIds
                AND (plagiarismCase.student.id = :userId OR teamStudent.id = :userId)
            """)
    List<PlagiarismCaseScoreDTO> findScoreInformationByStudentIdAndExerciseIds(@Param("userId") long userId, @Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post p
            WHERE plagiarismCase.exercise.id IN :exerciseIds
                AND plagiarismCase.student.id = :userId
                AND p.id IS NOT NULL
            """)
    List<PlagiarismCase> findByStudentIdAndExerciseIdsWithPost(@Param("userId") Long userId, @Param("exerciseIds") Set<Long> exerciseIds);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<PlagiarismCase> findByIdWithPlagiarismSubmissions(@Param("plagiarismCaseId") long plagiarismCaseId);

    @Query("""
            SELECT COALESCE(course.id, examCourse.id)
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<Long> findCourseIdById(@Param("plagiarismCaseId") long plagiarismCaseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDetailDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
                    exercise.shortName,
                    TYPE(exercise),
                    exercise.dueDate,
                    COALESCE(course.id, examCourse.id),
                    COALESCE(course.title, examCourse.title),
                    exam.id,
                    exam.title,
                    plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod
                ),
                student.id,
                student.login,
                student.firstName,
                student.lastName,
                post.id,
                post.creationDate,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.firstName,
                verdictBy.lastName,
                (
                    SELECT COUNT(plagiarismSubmission.id)
                    FROM PlagiarismSubmission plagiarismSubmission
                    WHERE plagiarismSubmission.plagiarismCase = plagiarismCase
                ),
                plagiarismCase.createdByContinuousPlagiarismControl,
                plagiarismCase.verdictMessage,
                plagiarismCase.verdictPointDeduction
            )
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.student student
                LEFT JOIN plagiarismCase.verdictBy verdictBy
                LEFT JOIN plagiarismCase.exercise exercise
                LEFT JOIN exercise.plagiarismDetectionConfig plagiarismDetectionConfig
                LEFT JOIN exercise.course course
                LEFT JOIN exercise.exerciseGroup exerciseGroup
                LEFT JOIN exerciseGroup.exam exam
                LEFT JOIN exam.course examCourse
                LEFT JOIN plagiarismCase.post post
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<PlagiarismCaseDetailDTO> findDetailDtoById(@Param("plagiarismCaseId") long plagiarismCaseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismSubmissionForCaseDTO(
                plagiarismSubmission.id,
                plagiarismSubmission.submissionId,
                plagiarismSubmission.studentLogin,
                plagiarismSubmission.size,
                plagiarismSubmission.score,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismComparisonSummaryDTO(
                    plagiarismComparison.id,
                    plagiarismComparison.similarity,
                    plagiarismComparison.status
                )
            )
            FROM PlagiarismSubmission plagiarismSubmission
                JOIN plagiarismSubmission.plagiarismComparison plagiarismComparison
            WHERE plagiarismSubmission.plagiarismCase.id = :plagiarismCaseId
            """)
    List<PlagiarismSubmissionForCaseDTO> findSubmissionDtosByPlagiarismCaseId(@Param("plagiarismCaseId") long plagiarismCaseId);

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            WHERE plagiarismCase.exercise.id = :exerciseId
                AND plagiarismCase.createdByContinuousPlagiarismControl = TRUE
            """)
    List<PlagiarismCase> findAllCreatedByContinuousPlagiarismControlByExerciseIdWithPlagiarismSubmissions(@Param("exerciseId") long exerciseId);

    default PlagiarismCase findByIdWithPlagiarismSubmissionsElseThrow(long plagiarismCaseId) {
        return getValueElseThrow(findByIdWithPlagiarismSubmissions(plagiarismCaseId), plagiarismCaseId);
    }

    default Long findCourseIdByIdElseThrow(long plagiarismCaseId) {
        return getArbitraryValueElseThrow(findCourseIdById(plagiarismCaseId), String.valueOf(plagiarismCaseId));
    }

    default PlagiarismCaseDetailDTO findDetailDtoByIdElseThrow(long plagiarismCaseId) {
        return getArbitraryValueElseThrow(findDetailDtoById(plagiarismCaseId), String.valueOf(plagiarismCaseId));
    }

    /**
     * Count the number of plagiarism cases for a given exercise id excluding deleted users.
     *
     * @param exerciseId the id of the exercise
     * @return the number of plagiarism cases
     */
    @Query("""
            SELECT COUNT(plagiarismCase)
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.student.deleted = FALSE
                AND plagiarismCase.exercise.id = :exerciseId
            """)
    long countByExerciseId(@Param("exerciseId") long exerciseId);
}
