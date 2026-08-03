package de.tum.cit.aet.artemis.lecture.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.hibernate.NonUniqueResultException;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;

/**
 * Spring Data JPA repository for the Lecture Unit entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitRepository extends ArtemisJpaRepository<LectureUnit, Long> {

    /**
     * Returns the ids of the lecture units in a course that are indexed into the {@code SearchableEntities} collection:
     * the indexable subtypes (text, online, attachment/video), excluding exercise units. This mirrors
     * {@code LectureUnitSearchableEntityDTO.isIndexable}, which is the exact condition under which a unit triggers an
     * upsert to Weaviate.
     *
     * @param courseId the course id
     * @return the indexable lecture unit ids for the course
     */
    @Query("""
            SELECT lu.id
            FROM LectureUnit lu
            WHERE lu.lecture.course.id = :courseId
                AND TYPE(lu) IN (TextUnit, OnlineUnit, AttachmentVideoUnit)
            """)
    Set<Long> findIndexableUnitIdsByCourseId(@Param("courseId") long courseId);

    /**
     * Returns the ids of the attachment/video units in a course that have a PDF attachment. These are the units whose
     * slides should be ingested into the Iris {@code Lectures} content collection, so this is the expected set for the
     * slides completeness metric.
     *
     * @param courseId the course id
     * @return the ids of units that have a PDF attachment
     */
    @Query("""
            SELECT avu.id
            FROM AttachmentVideoUnit avu
            WHERE avu.lecture.course.id = :courseId
                AND avu.attachment IS NOT NULL
            """)
    Set<Long> findUnitIdsWithAttachmentByCourseId(@Param("courseId") long courseId);

    /**
     * Returns the ids of the attachment/video units in a course that have a video source. These are the units whose
     * transcript should be ingested into the Iris {@code LectureTranscriptions} content collection, so this is the
     * expected set for the transcript completeness metric.
     *
     * @param courseId the course id
     * @return the ids of units that have a video source
     */
    @Query("""
            SELECT avu.id
            FROM AttachmentVideoUnit avu
            WHERE avu.lecture.course.id = :courseId
                AND avu.videoSource IS NOT NULL
                AND avu.videoSource <> ''
            """)
    Set<Long> findUnitIdsWithVideoByCourseId(@Param("courseId") long courseId);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findById(@Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencyLinks cl
                LEFT JOIN FETCH cl.competency
                LEFT JOIN FETCH lu.exercise e
                LEFT JOIN FETCH e.competencyLinks ecl
                LEFT JOIN FETCH ecl.competency
                LEFT JOIN FETCH lu.slides
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findWithCompetenciesAndSlidesById(@Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.completedUsers
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnit> findByIdWithCompletedUsers(@Param("lectureUnitId") long lectureUnitId);

    @Query("""
            SELECT cu
            FROM LectureUnit lu
                JOIN lu.completedUsers cu
            WHERE lu.lecture.id = :lectureId
                AND cu.user.id = :userId
            """)
    Set<LectureUnitCompletion> findCompletionsForLectureAndUser(@Param("lectureId") long lectureId, @Param("userId") long userId);

    /**
     * Finds a lecture unit by name, lecture title and course id. Currently, name duplicates are allowed but this method throws an exception if multiple lecture units with the
     * same name are found.
     *
     * @param name         the name of the lecture unit
     * @param lectureTitle the title of the lecture containing the lecture unit
     * @param courseId     the id of the course containing the lecture
     * @return the lecture unit with the given name, lecture title and course id
     * @throws NonUniqueResultException if multiple lecture units with the same name in the same lecture are found
     */
    @Query("""
            SELECT lu
            FROM LectureUnit lu
                LEFT JOIN FETCH lu.competencyLinks cl
                LEFT JOIN FETCH cl.competency
            WHERE lu.name = :name
                AND lu.lecture.title = :lectureTitle
                AND lu.lecture.course.id = :courseId
            """)
    Optional<LectureUnit> findByNameAndLectureTitleAndCourseIdWithCompetencies(@Param("name") String name, @Param("lectureTitle") String lectureTitle,
            @Param("courseId") long courseId) throws NonUniqueResultException;

    /**
     * Loads all lecture units for the given IDs together with their parent lecture in a single query.
     *
     * @param ids the IDs of the lecture units to load
     * @return the lecture units with their lectures eagerly fetched; units whose ID is not found are simply absent from the result
     */
    @Query("""
            SELECT lu
            FROM LectureUnit lu
                JOIN FETCH lu.lecture
            WHERE lu.id IN :ids
            """)
    List<LectureUnit> findAllByIdsWithLecture(@Param("ids") Collection<Long> ids);

    default LectureUnit findByIdWithCompletedUsersElseThrow(long lectureUnitId) {
        return getValueElseThrow(findByIdWithCompletedUsers(lectureUnitId), lectureUnitId);
    }

    default LectureUnit findByIdWithCompetenciesAndSlidesElseThrow(long lectureUnitId) {
        return getValueElseThrow(findWithCompetenciesAndSlidesById(lectureUnitId), lectureUnitId);
    }

    default LectureUnit findByIdElseThrow(long lectureUnitId) {
        return getValueElseThrow(findById(lectureUnitId), lectureUnitId);
    }

    /**
     * Reconnects the competency links to the lecture unit to avoid issues with JPA cascading operations.
     *
     * @param lectureUnit the lecture unit whose competency links need to be reconnected
     */
    default void reconnectCompetencyLinks(LectureUnit lectureUnit) {
        if (lectureUnit.getCompetencyLinks() != null && !lectureUnit.getCompetencyLinks().isEmpty()) {
            for (var competencyLink : lectureUnit.getCompetencyLinks()) {
                // reconnect to avoid: JpaSystemException: attempted to assign id from null one-to-one property
                competencyLink.setLectureUnit(lectureUnit);
            }
        }
    }
}
