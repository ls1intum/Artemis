package de.tum.in.www1.artemis.repository.plagiarism;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.plagiarism.PlagiarismCase;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the PlagiarismCase entity.
 */
@Repository
public interface PlagiarismCaseRepository extends JpaRepository<PlagiarismCase, Long> {

    @EntityGraph(type = LOAD, attributePaths = { "plagiarismSubmissions" })
    Optional<PlagiarismCase> findWithEagerSubmissionsById(Long aLong);

    @Query("""
                SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
                LEFT JOIN FETCH plagiarismCase.student
                LEFT JOIN FETCH plagiarismCase.exercise
                LEFT JOIN FETCH plagiarismCase.post
                LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmission
                WHERE plagiarismCase.student.login = :studentLogin
                AND plagiarismCase.exercise.id = :exerciseId
            """)
    Optional<PlagiarismCase> findByStudentLoginAndExerciseId(@Param("studentLogin") String studentLogin, @Param("exerciseId") Long exerciseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.exercise exercise
            LEFT JOIN FETCH plagiarismCase.post
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            LEFT JOIN FETCH plagiarismComparison.submissionA submissionA
            LEFT JOIN FETCH plagiarismComparison.submissionB submissionB
            WHERE plagiarismCase.exercise.course.id = :courseId
            """)
    List<PlagiarismCase> findPlagiarismCasesForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.exercise exercise
            LEFT JOIN FETCH plagiarismCase.post
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            LEFT JOIN FETCH plagiarismSubmissions.plagiarismComparison plagiarismComparison
            LEFT JOIN FETCH plagiarismComparison.submissionA submissionA
            LEFT JOIN FETCH plagiarismComparison.submissionB submissionB
            WHERE plagiarismCase.exercise.course.id = :courseId
            AND plagiarismCase.student.id = :userId
            AND plagiarismCase.post IS NOT NULL
            """)
    List<PlagiarismCase> findPlagiarismCasesForStudentForCourse(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Query("""
            SELECT DISTINCT plagiarismCase FROM PlagiarismCase plagiarismCase
            LEFT JOIN FETCH plagiarismCase.exercise exercise
            LEFT JOIN FETCH plagiarismCase.plagiarismSubmissions plagiarismSubmissions
            WHERE plagiarismCase.id = :plagiarismCaseId
            """)
    Optional<PlagiarismCase> findByIdWithExerciseAndPlagiarismSubmissions(@Param("plagiarismCaseId") long plagiarismCaseId);

    default PlagiarismCase findByIdWithExerciseAndPlagiarismSubmissionsElseThrow(long plagiarismCaseId) {
        return findByIdWithExerciseAndPlagiarismSubmissions(plagiarismCaseId).orElseThrow(() -> new EntityNotFoundException("PlagiarismCase", plagiarismCaseId));
    }

}
