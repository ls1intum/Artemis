package de.tum.cit.aet.artemis.metis.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupMembershipDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisStudentGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO;

/**
 * Spring Data JPA repository for the {@link MetisGroupWindow} entity, the per-tutorial-group time windows that override the default window of an exercise's
 * Metis configuration.
 * <p>
 * The tutorial groups and the registrations of students are read here with Metis' own statements over the tutorial group entities, which are mapped whether
 * or not the tutorial group module is active. That keeps Metis free of a dependency on the module's services and reads every membership of a course in one
 * statement instead of one per registration.
 */
@Conditional(MetisEnabled.class)
@Lazy
@Repository
public interface MetisGroupWindowRepository extends ArtemisJpaRepository<MetisGroupWindow, Long> {

    /**
     * All tutorial-group windows configured for one exercise.
     *
     * @param exerciseId the exercise whose configuration the windows belong to
     * @return the windows, empty when every group uses the default window of the configuration
     */
    List<MetisGroupWindow> findAllByExerciseId(long exerciseId);

    /**
     * The windows of an exercise that apply to one student, because the student is registered in their tutorial groups.
     *
     * @param exerciseId the exercise
     * @param studentId  the student
     * @return the windows with the titles of their groups, the earliest start first and, for the same start, the earliest end first
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisStudentGroupWindowDTO(tg.id, tg.title, w.windowStart, w.windowEnd)
            FROM MetisGroupWindow w
                JOIN TutorialGroup tg ON tg.id = w.tutorialGroupId
                JOIN TutorialGroupRegistration r ON r.tutorialGroup.id = tg.id
            WHERE w.exerciseId = :exerciseId
                AND r.student.id = :studentId
            ORDER BY w.windowStart ASC, w.windowEnd ASC
            """)
    List<MetisStudentGroupWindowDTO> findGroupWindowsOfStudent(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    /**
     * Every registration of a student in a tutorial group of a course, with the title of the group.
     *
     * @param courseId the course
     * @return one entry per registration, in no particular order
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisGroupMembershipDTO(r.student.id, tg.id, tg.title)
            FROM TutorialGroupRegistration r
                JOIN r.tutorialGroup tg
            WHERE tg.course.id = :courseId
            """)
    List<MetisGroupMembershipDTO> findGroupMembershipsByCourseId(@Param("courseId") long courseId);

    /**
     * The tutorial groups of a course, for the group window settings.
     *
     * @param courseId the course
     * @return the groups, ordered by title
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO(tg.id, tg.title)
            FROM TutorialGroup tg
            WHERE tg.course.id = :courseId
            ORDER BY tg.title ASC
            """)
    List<MetisTutorialGroupDTO> findTutorialGroupsByCourseId(@Param("courseId") long courseId);

    /**
     * How many of the given tutorial groups belong to a course, so that a request with a group of another course can be refused.
     *
     * @param courseId         the course
     * @param tutorialGroupIds the groups to check
     * @return the number of the given groups that belong to the course
     */
    @Query("""
            SELECT COUNT(tg)
            FROM TutorialGroup tg
            WHERE tg.course.id = :courseId
                AND tg.id IN :tutorialGroupIds
            """)
    long countTutorialGroupsOfCourse(@Param("courseId") long courseId, @Param("tutorialGroupIds") Collection<Long> tutorialGroupIds);
}
