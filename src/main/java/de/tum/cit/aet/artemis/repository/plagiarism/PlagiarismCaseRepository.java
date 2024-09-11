package de.tum.cit.aet.artemis.repository.plagiarism;

import static de.tum.cit.aet.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.cit.aet.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Profile(PROFILE_CORE)
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
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
                LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            WHERE plagiarismCase.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findByCourseIdWithPlagiarismSubmissionsAndComparison(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.post
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
                LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            WHERE plagiarismCase.exercise.exerciseGroup.exam.id = :examId
            """)
    List<PlagiarismCase> findByExamIdWithPlagiarismSubmissionsAndComparison(@Param("examId") Long examId);

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

    @Query("""
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
            WHERE plagiarismCase.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findByCourseId(@Param("courseId") Long courseId);

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
            SELECT plagiarismCase
            FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
                LEFT JOIN FETCH plagiarismCase.exercise e
                LEFT JOIN FETCH e.plagiarismDetectionConfig
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<PlagiarismCase> findByIdWithPlagiarismSubmissionsAndPlagiarismDetectionConfig(@Param("plagiarismCaseId") long plagiarismCaseId);

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

    default PlagiarismCase findByIdWithPlagiarismSubmissionsAndPlagiarismDetectionConfigElseThrow(long plagiarismCaseId) {
        return getValueElseThrow(findByIdWithPlagiarismSubmissionsAndPlagiarismDetectionConfig(plagiarismCaseId), plagiarismCaseId);
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
            WHERE plagiarismCase.student.isDeleted = FALSE
                AND plagiarismCase.exercise.id = :exerciseId
            """)
    long countByExerciseId(@Param("exerciseId") long exerciseId);
}
