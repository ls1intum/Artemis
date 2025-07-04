package de.tum.cit.aet.artemis.atlas.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.atlas.config.AtlasEnabled;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyExerciseMasteryCalculationDTO;
import de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyLectureUnitMasteryCalculationDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;

/**
 * Spring Data JPA repository for the {@link CourseCompetency} entity.
 */
@Conditional(AtlasEnabled.class)
@Lazy
@Repository
public interface CourseCompetencyRepository extends ArtemisJpaRepository<CourseCompetency, Long> {

    @Query("""
            SELECT c.title
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
            """)
    List<String> findAllTitlesByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
            """)
    Set<CourseCompetency> findAllForCourse(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.attachments
            WHERE c.course.id = :courseId
            """)
    Set<CourseCompetency> findAllForCourseWithExercisesAndLectureUnitsAndLecturesAndAttachments(@Param("courseId") long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.lectureUnits
                LEFT JOIN FETCH l.attachments
            WHERE c.id = :id
            """)
    Optional<CourseCompetency> findByIdWithExercisesAndLectureUnitsAndLectures(@Param("id") long id);

    default CourseCompetency findByIdWithExercisesAndLectureUnitsAndLecturesElseThrow(long id) {
        return getValueElseThrow(findByIdWithExercisesAndLectureUnitsAndLectures(id), id);
    }

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks e
                LEFT JOIN FETCH e.exercise
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.lecture l
                LEFT JOIN FETCH l.attachments
            WHERE c.id IN :ids
            """)
    Set<CourseCompetency> findAllByIdWithExercisesAndLectureUnitsAndLecturesAndAttachments(@Param("ids") Set<Long> ids);

    /**
     * Fetches all information related to the calculation of the mastery for exercises in a competency.
     * The complex grouping by is necessary for postgres
     *
     * @param competencyId the id of the competency for which to fetch the exercise information
     * @param user         the user for which to fetch the exercise information
     * @return the exercise information for the calculation of the mastery in the competency
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyExerciseMasteryCalculationDTO(
                e.id,
                e.maxPoints,
                e.difficulty,
                CASE WHEN TYPE(e) = ProgrammingExercise THEN TRUE ELSE FALSE END,
                el.weight,
                COALESCE(sS.lastScore, tS.lastScore),
                COALESCE(sS.lastPoints, tS.lastPoints),
                COALESCE(sS.lastModifiedDate, tS.lastModifiedDate),
                COUNT(s)
            )
            FROM CourseCompetency c
                LEFT JOIN c.exerciseLinks el
                LEFT JOIN el.exercise e
                LEFT JOIN e.studentParticipations sp ON sp.student = :user OR :user MEMBER OF sp.team.students
                LEFT JOIN sp.submissions s
                LEFT JOIN StudentScore sS ON sS.exercise = e AND sS.user = :user
                LEFT JOIN TeamScore tS ON tS.exercise = e AND :user MEMBER OF tS.team.students
            WHERE c.id = :competencyId
                AND e IS NOT NULL
            GROUP BY e.id, e.maxPoints, e.difficulty, TYPE(e), el.weight, sS.lastScore, tS.lastScore, sS.lastPoints, tS.lastPoints, sS.lastModifiedDate, tS.lastModifiedDate
            """)
    Set<CompetencyExerciseMasteryCalculationDTO> findAllExerciseInfoByCompetencyIdAndUser(@Param("competencyId") long competencyId, @Param("user") User user);

    /**
     * Fetches all information related to the calculation of the mastery for lecture units in a competency.
     * The complex grouping by is necessary for postgres
     *
     * @param competencyId the id of the competency for which to fetch the lecture unit information
     * @param user         the user for which to fetch the lecture unit information
     * @return the lecture unit information for the calculation of the mastery in the competency
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.atlas.dto.metrics.CompetencyLectureUnitMasteryCalculationDTO(
               lu.id,
               CASE WHEN u IS NOT NULL THEN TRUE ELSE FALSE END,
               lul.weight
            )
            FROM CourseCompetency c
                LEFT JOIN c.lectureUnitLinks lul
                LEFT JOIN lul.lectureUnit lu
                LEFT JOIN lu.completedUsers cu
                LEFT JOIN cu.user u ON u = :user
            WHERE c.id = :competencyId
                AND lu IS NOT NULL
            GROUP BY lu.id, u, lul.weight, u
            """)
    Set<CompetencyLectureUnitMasteryCalculationDTO> findAllLectureUnitInfoByCompetencyIdAndUser(@Param("competencyId") long competencyId, @Param("user") User user);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
                LEFT JOIN FETCH c.exerciseLinks e
                LEFT JOIN FETCH e.exercise
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithExercisesAndLectureUnits(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise e
                LEFT JOIN FETCH e.competencyLinks ecl
                LEFT JOIN FETCH ecl.competency
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit lu
                LEFT JOIN FETCH lu.competencyLinks lucl
                LEFT JOIN FETCH lucl.competency
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithExercisesAndLectureUnitsBidirectional(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c.id
            FROM CourseCompetency c
                LEFT JOIN c.exerciseLinks el
                LEFT JOIN el.exercise e
            WHERE :exercise = e
            """)
    Set<Long> findAllIdsByExercise(@Param("exercise") Exercise exercise);

    @Query("""
            SELECT c.id
            FROM CourseCompetency c
                LEFT JOIN c.lectureUnitLinks lul
                LEFT JOIN lul.lectureUnit lu
            WHERE :lectureUnit = lu
            """)
    Set<Long> findAllIdsByLectureUnit(@Param("lectureUnit") LectureUnit lectureUnit);

    /**
     * Returns the title of the course competency with the given id.
     *
     * @param competencyId the id of the course competency
     * @return the name/title of the course competency or null if the competency does not exist
     */
    @Query("""
            SELECT c.title
            FROM CourseCompetency c
            WHERE c.id = :competencyId
            """)
    @Cacheable(cacheNames = "competencyTitle", key = "#competencyId", unless = "#result == null")
    String getCompetencyTitle(@Param("competencyId") long competencyId);

    /**
     * Query which fetches all course competencies for which the user has access to and match the search criteria.
     * The user has access to all competencies where they are an editor or instructor of the course or to all of they are an admin.
     *
     * @param partialTitle       course competency title search term
     * @param partialDescription course competency description search term
     * @param partialCourseTitle course title search term
     * @param semester           semester search term
     * @param groups             user groups
     * @param isAdmin            if the user is an admin
     * @param pageable           Pageable
     * @return Page with search results
     */
    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE (:isAdmin = TRUE OR c.course.instructorGroupName IN :groups OR c.course.editorGroupName IN :groups)
                AND (:partialTitle IS NULL OR c.title LIKE %:partialTitle%)
                AND (:partialDescription IS NULL OR c.description LIKE %:partialDescription%)
                AND (:partialCourseTitle IS NULL OR c.course.title LIKE %:partialCourseTitle%)
                AND (:semester IS NULL OR c.course.semester = :semester)
            """)
    Page<CourseCompetency> findForImportAndUserHasAccessToCourse(@Param("partialTitle") String partialTitle, @Param("partialDescription") String partialDescription,
            @Param("partialCourseTitle") String partialCourseTitle, @Param("semester") String semester, @Param("groups") Set<String> groups, @Param("isAdmin") boolean isAdmin,
            Pageable pageable);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithExercises(@Param("competencyId") long competencyId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
                LEFT JOIN FETCH lul.lectureUnit
                LEFT JOIN FETCH c.exerciseLinks el
                LEFT JOIN FETCH el.exercise
            WHERE c.id = :competencyId
            """)
    Optional<CourseCompetency> findByIdWithLectureUnitsAndExercises(@Param("competencyId") long competencyId);

    default CourseCompetency findByIdWithLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnits(competencyId), competencyId);
    }

    default CourseCompetency findByIdWithExercisesAndLectureUnitsElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithExercisesAndLectureUnits(competencyId), competencyId);
    }

    default CourseCompetency findByIdWithExercisesAndLectureUnitsBidirectionalElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithExercisesAndLectureUnitsBidirectional(competencyId), competencyId);
    }

    /**
     * Finds the set of ids of course competencies that are linked to a given learning object
     *
     * @param learningObject the learning object to find the course competencies for
     * @return the set of ids of course competencies linked to the learning object
     */
    default Set<Long> findAllIdsByLearningObject(LearningObject learningObject) {
        return switch (learningObject) {
            case LectureUnit lectureUnit -> findAllIdsByLectureUnit(lectureUnit);
            case Exercise exercise -> findAllIdsByExercise(exercise);
            default -> throw new IllegalArgumentException("Unknown LearningObject type: " + learningObject.getClass());
        };
    }

    default CourseCompetency findByIdWithExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithExercises(competencyId), competencyId);
    }

    default CourseCompetency findByIdWithLectureUnitsAndExercisesElseThrow(long competencyId) {
        return getValueElseThrow(findByIdWithLectureUnitsAndExercises(competencyId), competencyId);
    }

    List<CourseCompetency> findByCourseIdOrderById(long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
            WHERE c.course.id = :courseId
                AND (SIZE(c.lectureUnitLinks) > 0 OR SIZE(c.exerciseLinks) > 0)
            ORDER BY c.id
            """)
    List<CourseCompetency> findByCourseIdAndLinkedToLearningObjectOrderById(@Param("courseId") long courseId);

    boolean existsByIdAndCourseId(long competencyId, long courseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.exerciseLinks el
            WHERE el.exercise.id = :exerciseId
            """)
    List<CourseCompetency> findAllByExerciseIdWithExerciseLinks(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT c
            FROM CourseCompetency c
                LEFT JOIN FETCH c.lectureUnitLinks lul
            WHERE lul.lectureUnit.id = :lectureUnitId
            """)
    List<CourseCompetency> findAllByLectureUnitIdWithLectureUnitLinks(@Param("lectureUnitId") long lectureUnitId);
}
