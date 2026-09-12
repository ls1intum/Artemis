package de.tum.cit.aet.artemis.globalsearch.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Collection;
import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * The DB side of the ingestion coverage diff: for a set of courses, which entity ids does the database expect to be
 * present in Weaviate? Each method returns {@code (courseId, entityId)} pairs so the recompute can bucket them per
 * course, and resolves ALL requested courses in one query so a dashboard covering every course never degrades into a
 * query per course.
 * <p>
 * These queries live here rather than in each entity's own repository because they exist only for this dashboard: their
 * shape is dictated by the recompute, not by the Exercise/Faq/Channel domains, and no other caller wants them. This
 * follows the same consumer-owns-its-queries pattern as {@code StatisticsRepository} and
 * {@code MaintenanceEmailRecipientRepository}, which likewise query entities their module does not own. The bound
 * {@link Course} type is the subject the methods share; the inherited CRUD operations are not used.
 * <p>
 * Entities from OPTIONAL modules (lecture, lecture unit, exam) are deliberately not here: those go through their
 * module's {@code api} package so the recompute degrades gracefully when the module is disabled.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface IngestionCoverageExpectedIdsRepository extends ArtemisJpaRepository<Course, Long> {

    /**
     * Resolves the course like {@code getCourseViaExerciseGroupOrCourseMember()}: directly for course exercises, and
     * through {@code exerciseGroup -> exam -> course} for exam exercises.
     *
     * @param courseIds the courses to resolve exercises for
     * @return the (courseId, exerciseId) pairs, including exam exercises
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO(COALESCE(e.course.id, exam.course.id), e.id)
            FROM Exercise e
                LEFT JOIN e.exerciseGroup eg
                LEFT JOIN eg.exam exam
            WHERE e.course.id IN :courseIds
                OR exam.course.id IN :courseIds
            """)
    List<CourseEntityIdDTO> findExerciseIdCourseIdPairsForCourses(@Param("courseIds") Collection<Long> courseIds);

    /**
     * Every FAQ is indexed regardless of state (state is a query-time filter property, not an index gate).
     *
     * @param courseIds the courses to resolve FAQs for
     * @return the (courseId, faqId) pairs
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO(faq.course.id, faq.id)
            FROM Faq faq
            WHERE faq.course.id IN :courseIds
            """)
    List<CourseEntityIdDTO> findFaqIdCourseIdPairsForCourses(@Param("courseIds") Collection<Long> courseIds);

    /**
     * Indexable channels match {@code ChannelSearchableEntityDTO.isIndexable}: not archived AND (course-wide OR public).
     *
     * @param courseIds the courses to resolve channels for
     * @return the (courseId, channelId) pairs of indexable channels
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.core.dto.CourseEntityIdDTO(channel.course.id, channel.id)
            FROM Channel channel
            WHERE channel.course.id IN :courseIds
                AND channel.isArchived = FALSE
                AND (channel.isCourseWide = TRUE OR channel.isPublic = TRUE)
            """)
    List<CourseEntityIdDTO> findIndexableChannelIdCourseIdPairsForCourses(@Param("courseIds") Collection<Long> courseIds);
}
