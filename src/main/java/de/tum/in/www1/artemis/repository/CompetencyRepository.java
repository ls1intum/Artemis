package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.LearningPath;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyExerciseMasteryCalculationDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface CompetencyRepository extends ArtemisJpaRepository<Competency, Long>, JpaSpecificationExecutor<Competency> {

    @Query("""
            SELECT c
            FROM Competency c
            WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH c.exercises
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findWithLectureUnitsAndExercisesById(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("competencyId") long competencyId);

    /**
     * Fetches all information related to the calculation of the mastery for exercises in a competency.
     * The complex grouping by is necessary for postgres
     *
     * @param competencyId the id of the competency for which to fetch the exercise information
     * @return the exercise information for the calculation of the mastery in the competency
     */
    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyExerciseMasteryCalculationDTO(
                ex.maxPoints,
                ex.difficulty,
                CASE WHEN TYPE(ex) = ProgrammingExercise THEN TRUE ELSE FALSE END,
                COALESCE(sS.lastScore, tS.lastScore),
                COALESCE(sS.lastPoints, tS.lastPoints),
                COALESCE(sS.lastModifiedDate, tS.lastModifiedDate),
                COUNT(s)
            )
            FROM Competency c
                LEFT JOIN c.exercises ex
                LEFT JOIN ex.studentParticipations sp
                LEFT JOIN sp.submissions s
                LEFT JOIN StudentScore sS ON sS.exercise = ex
                LEFT JOIN TeamScore tS ON tS.exercise = ex
            WHERE c.id = :competencyId
                AND ex IS NOT NULL
            GROUP BY ex.maxPoints, ex.difficulty, TYPE(ex), sS.lastScore, tS.lastScore, sS.lastPoints, tS.lastPoints, sS.lastModifiedDate, tS.lastModifiedDate
            """)
    Set<CompetencyExerciseMasteryCalculationDTO> findAllExerciseInfoByCompetencyId(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises ex
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") long competencyId);

    /**
     * Query which fetches all competencies for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialDescription competency description search term
     * @param partialCourseTitle course title search term
     * @param semester           semester search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (:partialTitle IS NULL OR c.title LIKE %:partialTitle%)
                AND (:partialDescription IS NULL OR c.description LIKE %:partialDescription%)
                AND (:partialCourseTitle IS NULL OR c.course.title LIKE %:partialCourseTitle%)
                AND (:semester IS NULL OR c.course.semester = :semester)
            """)
    Page<Competency> findForImportAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialDescription") String partialDescription,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("semester") String semester, @Param("groups") Set<String> groups, Pageable pageable);

    /**
     * Query which fetches all competencies matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialDescription competency description search term
     * @param partialCourseTitle course title search term
     * @param semester           semester search term
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (:partialTitle IS NULL OR c.title LIKE %:partialTitle%)
                AND (:partialDescription IS NULL OR c.description LIKE %:partialDescription%)
                AND (:partialCourseTitle IS NULL OR c.course.title LIKE %:partialCourseTitle%)
                AND (:semester IS NULL OR c.course.semester = :semester)
            """)
    Page<Competency> findForImport(@Param("partialTitle") String partialTitle, @Param("partialDescription") String partialDescription,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("semester") String semester, Pageable pageable);

    /**
     * Returns the title of the competency with the given id.
     *
     * @param competencyId the id of the competency
     * @return the name/title of the competency or null if the competency does not exist
     */
    @Query("""
            SELECT c.title
            FROM Competency c
            WHERE c.id = :competencyId
            """)
    @Cacheable(cacheNames = "competencyTitle", key = "#competencyId", unless = "#result == null")
    String getCompetencyTitle(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.learningPaths lp
            WHERE lp = :learningPath
            """)
    Set<Competency> findAllByLearningPath(@Param("learningPath") LearningPath learningPath);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesElseThrow(long competencyId) {
        return findByIdWithExercises(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdElseThrow(long competencyId) {
        return findById(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findWithLectureUnitsAndExercisesByIdElseThrow(long competencyId) {
        return findWithLectureUnitsAndExercisesById(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return findByIdWithLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    long countByCourse(Course course);

    List<Competency> findByCourseIdOrderById(long courseId);

    boolean existsByIdAndCourseId(long competencyId, long courseId);
}
