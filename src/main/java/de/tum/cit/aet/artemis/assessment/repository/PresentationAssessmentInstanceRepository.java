package de.tum.cit.aet.artemis.assessment.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.PresentationAssessmentInstance;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentExportDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO;
import de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO;
import de.tum.cit.aet.artemis.core.domain.DomainObject_;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for individual presentation assessment instances.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface PresentationAssessmentInstanceRepository extends ArtemisJpaRepository<PresentationAssessmentInstance, Long>,
        JpaSpecificationExecutor<PresentationAssessmentInstance>, PresentationAssessmentInstanceWriteRepository {

    /**
     * Finds an instance within the given presentation assessment and course.
     * Fetches the student so its identity information remains available for DTO mapping.
     *
     * @param id           the instance id
     * @param assessmentId the presentation assessment id
     * @param courseId     the owning course id
     * @return the matching instance, or empty if it does not belong to the specified presentation and course
     */
    @EntityGraph(attributePaths = { "student" })
    Optional<PresentationAssessmentInstance> findByIdAndPresentationAssessmentIdAndPresentationAssessmentCourseId(long id, long assessmentId, long courseId);

    /**
     * Returns the highest assigned result points for the presentation assessment.
     *
     * @param assessmentId the presentation assessment id
     * @return the highest result points, or empty if no points have been assigned
     */
    @Query("""
            SELECT MAX(instance.resultPoints)
            FROM PresentationAssessmentInstance instance
            WHERE instance.presentationAssessment.id = :assessmentId
            """)
    Optional<Double> findHighestResultPointsByPresentationAssessmentId(@Param("assessmentId") long assessmentId);

    /**
     * Returns a filtered and sorted page of instance IDs.
     *
     * @param courseId         the owning course id
     * @param assessmentId     the presentation assessment id, or null for no filter
     * @param assessed         whether result points are assigned, or null for no filter
     * @param linkedToExercise whether the presentation is linked to an exercise, or null for no filter
     * @param searchPattern    the prepared lower-case LIKE pattern, or null for no filter
     * @param pageable         pagination and sorting information
     * @return a page of matching instance IDs with the total number of matches
     */
    default Page<Long> findStudentRowIdsByCourseId(long courseId, Long assessmentId, Boolean assessed, Boolean linkedToExercise, String searchPattern, Pageable pageable) {
        var spec = Specification.where(PresentationAssessmentInstanceSpecs.forCourse(courseId)).and(PresentationAssessmentInstanceSpecs.forAssessment(assessmentId))
                .and(PresentationAssessmentInstanceSpecs.withAssessmentStatus(assessed)).and(PresentationAssessmentInstanceSpecs.linkedToExercise(linkedToExercise))
                .and(PresentationAssessmentInstanceSpecs.matchesSearch(searchPattern));

        return findBy(spec, query -> query.project(DomainObject_.ID).page(pageable)).map(PresentationAssessmentInstance::getId);
    }

    /**
     * Loads presentation, instance, and student data for the given instance IDs.
     * The returned rows have no guaranteed order.
     *
     * @param instanceIds the instance IDs to load
     * @return the matching student rows
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStudentRowDTO(
                assessment.id, assessment.title, assessment.description, assessment.maxPoints,
                assessment.course.id, exercise.id, exercise.title,
                instance.id, instance.presentationDate, instance.resultPoints,
                instance.language, instance.mode, instance.location, instance.meetingLink, instance.remark,
                student.login, student.firstName, student.lastName, student.email)
            FROM PresentationAssessmentInstance instance
            JOIN instance.presentationAssessment assessment
            JOIN instance.student student
            LEFT JOIN assessment.exercise exercise
            WHERE instance.id IN :instanceIds
            """)
    List<PresentationAssessmentStudentRowDTO> findStudentRowsByInstanceIds(@Param("instanceIds") List<Long> instanceIds);

    /**
     * Returns course-wide total and assessed instance counts.
     *
     * @param courseId the owning course id
     * @return the total and assessed instance counts
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentStatisticsDTO(
                COUNT(instance), COUNT(instance.resultPoints))
            FROM PresentationAssessmentInstance instance
            WHERE instance.presentationAssessment.course.id = :courseId
            """)
    PresentationAssessmentStatisticsDTO findStatisticsByCourseId(@Param("courseId") long courseId);

    long countByPresentationAssessmentCourseId(long courseId);

    /**
     * Returns the logins of those of the given students that already have an instance of the presentation assessment.
     *
     * @param assessmentId the presentation assessment id
     * @param studentIds   the ids of the students to check
     * @return the logins of the students that are already assigned, in alphabetical order
     */
    @Query("""
            SELECT instance.student.login
            FROM PresentationAssessmentInstance instance
            WHERE instance.presentationAssessment.id = :assessmentId
                AND instance.student.id IN :studentIds
            ORDER BY instance.student.login
            """)
    List<String> findAssignedStudentLogins(@Param("assessmentId") long assessmentId, @Param("studentIds") Collection<Long> studentIds);

    /**
     * Checks whether the student has another instance of the presentation assessment than the given one.
     *
     * @param assessmentId the presentation assessment id
     * @param studentId    the student id
     * @param instanceId   the id of the instance that is not counted
     * @return true if the student has a different instance of the presentation assessment
     */
    boolean existsByPresentationAssessmentIdAndStudentIdAndIdNot(long assessmentId, long studentId, long instanceId);

    /**
     * Loads all presentation assessment instances of a student for the personal data export.
     *
     * @param userId the id of the student
     * @return the instances of the student ordered by course, presentation and date
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.PresentationAssessmentExportDTO(
                course.title, assessment.title, assessment.maxPoints, instance.presentationDate, instance.resultPoints,
                instance.language, instance.mode, instance.location, instance.meetingLink, instance.remark)
            FROM PresentationAssessmentInstance instance
            JOIN instance.presentationAssessment assessment
            JOIN assessment.course course
            WHERE instance.student.id = :userId
            ORDER BY course.title, assessment.title, instance.presentationDate, instance.id
            """)
    List<PresentationAssessmentExportDTO> findExportRowsByStudentId(@Param("userId") long userId);

    @Transactional // ok because of delete
    long deleteAllByPresentationAssessmentCourseId(long courseId);
}
