package de.tum.in.www1.artemis.repository;

import java.util.Optional;
import java.util.Set;

import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyProgress;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnitCompletion;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the Competency entity.
 */
@Repository
public interface CompetencyRepository extends JpaRepository<Competency, Long>, JpaSpecificationExecutor<Competency> {

    @Query("""
            SELECT c
            FROM Competency c
            LEFT JOIN FETCH c.userProgress progress
                WHERE c.course.id = :courseId
            """)
    Set<Competency> findAllForCourse(@Param("courseId") Long courseId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.userProgress progress
            WHERE c.course.id = :courseId
                AND (progress IS NULL OR progress.user.id = :userId)
            """)
    Set<Competency> findAllForCourseWithProgressForUser(@Param("courseId") Long courseId, @Param("userId") Long userId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :#{#competencyId}
            """)
    Optional<Competency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    class CompetencySpecification {

        static public Specification<Competency> getCompetencyWithFetchesAndConditionalJoins(Long competencyId, Long userId) {
            return (root, query, builder) -> {
                root.fetch("userProgress", JoinType.LEFT);
                root.fetch("exercises", JoinType.LEFT);
                Fetch<Competency, LectureUnit> lectureUnitFetch = root.fetch("lectureUnits", JoinType.LEFT);
                lectureUnitFetch.fetch("completedUsers", JoinType.LEFT);
                lectureUnitFetch.fetch("lecture", JoinType.LEFT);

                Join<LectureUnit, LectureUnitCompletion> completionJoin = root.join("lectureUnits", JoinType.LEFT).join("completedUsers", JoinType.LEFT);
                completionJoin.on(completionJoin.get("user").get("id").in(userId));

                Join<LectureUnit, CompetencyProgress> progressJoin = root.join("userProgress", JoinType.LEFT);
                progressJoin.on(progressJoin.get("user").get("id").in(userId));
                return builder.and(builder.equal(root.get("id"), competencyId));
            };
        }
    }

    /**
     * Fetches a competency with all linked exercises, lecture units, the associated progress, and completion of the specified user.
     * <p>
     * IMPORTANT: We use the {@link Specification} to fetch the lazy loaded data. The fetched data is limited by joining on the user id.
     *
     * @param competencyId the id of the competency that should be fetched
     * @param userId       the id of the user whose progress should be fetched
     * @return the competency
     */
    default Optional<Competency> findByIdWithExercisesAndLectureUnitsAndProgressForUser(Long competencyId, Long userId) {
        return findOne(CompetencySpecification.getCompetencyWithFetchesAndConditionalJoins(competencyId, userId));
    }

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsAndCompletions(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT c
            FROM Competency c
                LEFT JOIN FETCH c.consecutiveCourses
            WHERE c.id = :competencyId
            """)
    Optional<Competency> findByIdWithConsecutiveCourses(@Param("competencyId") Long competencyId);

    @Query("""
            SELECT pr
            FROM Competency pr
                LEFT JOIN FETCH pr.consecutiveCourses c
            WHERE c.id = :courseId
            ORDER BY pr.title
            """)
    Set<Competency> findPrerequisitesByCourseId(@Param("courseId") Long courseId);

    /**
     * Query which fetches all competencies for which the user is editor or instructor in the course and
     * matching the search criteria.
     *
     * @param partialTitle       competency title search term
     * @param partialCourseTitle course title search term
     * @param groups             user groups
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM Competency c
            WHERE (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (c.title LIKE %:partialTitle% OR c.course.title LIKE %:partialCourseTitle%)
            """)
    Page<Competency> findByTitleInLectureOrCourseAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialCourseTitle") String partialCourseTitle,
            @Param("groups") Set<String> groups, Pageable pageable);

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
    String getCompetencyTitle(@Param("competencyId") Long competencyId);

    @SuppressWarnings("PMD.MethodNamingConventions")
    Page<Competency> findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(String partialTitle, String partialCourseTitle, Pageable pageable);

    default Competency findByIdWithLectureUnitsAndCompletionsElseThrow(long competencyId) {
        return findByIdWithLectureUnitsAndCompletions(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return findByIdWithExercisesAndLectureUnitsBidirectional(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithConsecutiveCoursesElseThrow(long competencyId) {
        return findByIdWithConsecutiveCourses(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdElseThrow(Long competencyId) {
        return findById(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithLectureUnitsElseThrow(Long competencyId) {
        return findByIdWithLectureUnits(competencyId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    default Competency findByIdWithExercisesAndLectureUnitsAndProgressForUserElseThrow(long competencyId, long userId) {
        return findByIdWithExercisesAndLectureUnitsAndProgressForUser(competencyId, userId).orElseThrow(() -> new EntityNotFoundException("Competency", competencyId));
    }

    long countByCourse(Course course);
}
