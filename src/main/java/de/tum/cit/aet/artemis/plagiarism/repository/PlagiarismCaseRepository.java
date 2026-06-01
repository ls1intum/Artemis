package de.tum.cit.aet.artemis.plagiarism.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
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

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
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
                student.name,
                student.visibleRegistrationNumber,
                post.id,
                post.creationDate,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.name,
                verdictBy.visibleRegistrationNumber,
                COUNT(DISTINCT plagiarismSubmission.id),
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
                LEFT JOIN plagiarismCase.plagiarismSubmissions plagiarismSubmission
            WHERE exercise.course.id = :courseId
            GROUP BY plagiarismCase.id, exercise.id, exercise.title, TYPE(exercise), exercise.dueDate, course.id, examCourse.id, course.title, examCourse.title, exam.id,
                exam.title, plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, student.id, student.login, student.name,
                student.visibleRegistrationNumber, post.id, post.creationDate, plagiarismCase.verdict, plagiarismCase.verdictDate, verdictBy.id, verdictBy.login,
                verdictBy.name, verdictBy.visibleRegistrationNumber, plagiarismCase.createdByContinuousPlagiarismControl
            """)
    List<PlagiarismCaseOverviewDTO> findOverviewDtosByCourseId(@Param("courseId") Long courseId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseOverviewDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
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
                student.name,
                student.visibleRegistrationNumber,
                post.id,
                post.creationDate,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.name,
                verdictBy.visibleRegistrationNumber,
                COUNT(DISTINCT plagiarismSubmission.id),
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
                LEFT JOIN plagiarismCase.plagiarismSubmissions plagiarismSubmission
            WHERE exam.id = :examId
            GROUP BY plagiarismCase.id, exercise.id, exercise.title, TYPE(exercise), exercise.dueDate, course.id, examCourse.id, course.title, examCourse.title, exam.id,
                exam.title, plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, student.id, student.login, student.name,
                student.visibleRegistrationNumber, post.id, post.creationDate, plagiarismCase.verdict, plagiarismCase.verdictDate, verdictBy.id, verdictBy.login,
                verdictBy.name, verdictBy.visibleRegistrationNumber, plagiarismCase.createdByContinuousPlagiarismControl
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

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN plagiarismCase.team.students teamStudent
            WHERE plagiarismCase.exercise.course.id = :courseId
                AND (plagiarismCase.student.id = :studentId OR teamStudent.id = :studentId)
            """)
    List<PlagiarismCase> findByCourseIdAndStudentId(@Param("courseId") Long courseId, @Param("studentId") Long studentId);

    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.id IN :exerciseIds
                AND plagiarismCase.student.id = :userId
            """)
    List<PlagiarismCase> findByStudentIdAndExerciseIds(@Param("userId") Long userId, @Param("exerciseIds") Set<Long> exerciseIds);

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
            SELECT new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseDetailDTO(
                plagiarismCase.id,
                new de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseExerciseDTO(
                    exercise.id,
                    exercise.title,
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
                student.name,
                student.visibleRegistrationNumber,
                post.id,
                post.creationDate,
                plagiarismCase.verdict,
                plagiarismCase.verdictDate,
                verdictBy.id,
                verdictBy.login,
                verdictBy.name,
                verdictBy.visibleRegistrationNumber,
                COUNT(DISTINCT plagiarismSubmission.id),
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
                LEFT JOIN plagiarismCase.plagiarismSubmissions plagiarismSubmission
            WHERE plagiarismCase.id = :plagiarismCaseId
            GROUP BY plagiarismCase.id, exercise.id, exercise.title, TYPE(exercise), exercise.dueDate, course.id, examCourse.id, course.title, examCourse.title, exam.id,
                exam.title, plagiarismDetectionConfig.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod, student.id, student.login, student.name,
                student.visibleRegistrationNumber, post.id, post.creationDate, plagiarismCase.verdict, plagiarismCase.verdictDate, verdictBy.id, verdictBy.login,
                verdictBy.name, verdictBy.visibleRegistrationNumber, plagiarismCase.createdByContinuousPlagiarismControl, plagiarismCase.verdictMessage,
                plagiarismCase.verdictPointDeduction
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
