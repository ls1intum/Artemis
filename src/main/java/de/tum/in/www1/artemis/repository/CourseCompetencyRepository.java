package de.tum.in.www1.artemis.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.CourseCompetency;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;
import de.tum.in.www1.artemis.web.rest.dto.metrics.CompetencyExerciseMasteryCalculationDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

/**
 * Spring Data JPA repository for the {@link CourseCompetency} entity.
 */
public interface CourseCompetencyRepository extends ArtemisJpaRepository<CourseCompetency, Long> {

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.id IN :courseCompetencyIds AND (c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
            """)
    List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourse(@Param("courseCompetencyIds") List<Long> courseCompetencyIds, @Param("groups") Set<String> groups);

    @Query("""
            SELECT c.title
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
            """)
    List<String> findAllTitlesByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnits lu
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
            """)
    Set<CourseCompetency> findAllForCourse(@Param("courseId") long courseId);

    /**
     * Fetches all information related to the calculation of the mastery for exercises in a competency.
     * The complex grouping by is necessary for postgres
     *
     * @param competencyId the id of the competency for which to fetch the exercise information
     * @param user         the user for which to fetch the exercise information
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
            FROM CourseCompetency c
                LEFT JOIN c.exercises ex
                LEFT JOIN ex.studentParticipations sp ON sp.student = :user OR :user MEMBER OF sp.team.students
                LEFT JOIN sp.submissions s
                LEFT JOIN StudentScore sS ON sS.exercise = ex AND sS.user = :user
                LEFT JOIN TeamScore tS ON tS.exercise = ex AND :user MEMBER OF tS.team.students
            WHERE c.id = :competencyId
                AND ex IS NOT NULL
            GROUP BY ex.maxPoints, ex.difficulty, TYPE(ex), sS.lastScore, tS.lastScore, sS.lastPoints, tS.lastPoints, sS.lastModifiedDate, tS.lastModifiedDate
            """)
    Set<CompetencyExerciseMasteryCalculationDTO> findAllExerciseInfoByCompetencyId(@Param("competencyId") long competencyId, @Param("user") User user);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH c.exercises ex
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithExercisesAndLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exercises ex
                LEFT JOIN FETCH ex.competencies
                LEFT JOIN FETCH c.lectureUnits lu
                LEFT JOIN FETCH lu.competencies
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") long competencyId);

    /**
     * Finds a list of competencies by id and verifies that the user is at least editor in the respective courses.
     * If any of the competencies are not accessible, throws a {@link EntityNotFoundException}
     *
     * @param courseCompetencyIds the ids of the course competencies to find
     * @param userGroups          the userGroups of the user to check
     * @return the list of course competencies
     */
    default List<CourseCompetency> findAllByIdAndUserIsAtLeastEditorInCourseElseThrow(List<Long> courseCompetencyIds, Set<String> userGroups) {
        var courseCompetencies = findAllByIdAndUserIsAtLeastEditorInCourse(courseCompetencyIds, userGroups);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }

    /**
     * Finds a list of course competencies by id. If any of them do not exist throws a {@link EntityNotFoundException}
     *
     * @param courseCompetencyIds the ids of the course competencies to find
     * @return the list of course competencies
     */
    default List<CourseCompetency> findAllByIdElseThrow(List<Long> courseCompetencyIds) {
        var courseCompetencies = findAllById(courseCompetencyIds);
        if (courseCompetencies.size() != courseCompetencyIds.size()) {
            throw new EntityNotFoundException("Could not find all requested courseCompetencies!");
        }
        return courseCompetencies;
    }

    default CourseCompetency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    default CourseCompetency findByIdWithExercisesAndLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithExercisesAndLectureUnits(competencyId), competencyId);
    }

    default CourseCompetency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithExercisesAndLectureUnitsBidirectional(competencyId), competencyId);
    }

    List<CourseCompetency> findByCourseIdOrderById(long courseId);
}
