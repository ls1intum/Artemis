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
import de.tum.cit.aet.artemis.lecture.domain.ProcessingPhase;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitMaterialVersionsDTO;

/**
 * Spring Data JPA repository for the Lecture Unit entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface LectureUnitRepository extends ArtemisJpaRepository<LectureUnit, Long> {

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

    /**
     * Loads the versions of the material Iris has ingested for the given lecture units, used to pin a citation to the material it was generated from.
     * <p>
     * The versions are only reported once processing reached {@link ProcessingPhase#DONE}: while a unit is being reprocessed, the vector database still serves the previous
     * revision, so the live versions would not describe the material a citation was actually generated from.
     *
     * @param ids the IDs of the lecture units to load
     * @return one entry per lecture unit found; units whose ID is not found are simply absent from the result
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.LectureUnitIngestedVersionsDTO(
                lu.id,
                CASE WHEN ps.phase = ProcessingPhase.DONE THEN ps.attachmentVersion ELSE NULL END,
                CASE WHEN ps.phase = ProcessingPhase.DONE AND t.id IS NOT NULL THEN ps.transcriptionVersion ELSE NULL END)
            FROM LectureUnit lu
                LEFT JOIN LectureUnitProcessingState ps ON ps.lectureUnit.id = lu.id
                LEFT JOIN LectureTranscription t ON t.lectureUnit.id = lu.id
            WHERE lu.id IN :ids
            """)
    List<LectureUnitIngestedVersionsDTO> findIngestedVersionsByIds(@Param("ids") Collection<Long> ids);

    /**
     * Loads the versions of the material a lecture unit currently offers.
     * <p>
     * Iris citations are pinned to the version of the material they were generated from. Fetching the current versions at the moment a citation is clicked — rather than
     * carrying them along with the chat — is what makes the comparison reflect the material as it is right now.
     * <p>
     * The transcription version is only reported for a {@link TranscriptionStatus#COMPLETED} transcription, because the version describes the last completed one: a run in
     * progress writes its raw segments as {@link TranscriptionStatus#PENDING} and the version only follows once the enriched result arrives. Reporting it in between would
     * let a citation of the previous video compare equal and jump to a timestamp that no longer describes what is said there — for the length of the run, or indefinitely
     * if it never completes.
     *
     * @param lectureUnitId the ID of the lecture unit
     * @return the current versions, or empty if the unit does not exist
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.LectureUnitMaterialVersionsDTO(
                a.version,
                CASE WHEN t.id IS NOT NULL THEN ps.transcriptionVersion ELSE NULL END,
                CASE WHEN avu.videoSource IS NOT NULL THEN TRUE ELSE FALSE END)
            FROM LectureUnit lu
                LEFT JOIN AttachmentVideoUnit avu ON avu.id = lu.id
                LEFT JOIN avu.attachment a
                LEFT JOIN LectureUnitProcessingState ps ON ps.lectureUnit.id = lu.id
                LEFT JOIN LectureTranscription t ON t.lectureUnit.id = lu.id
                    AND t.transcriptionStatus = TranscriptionStatus.COMPLETED
            WHERE lu.id = :lectureUnitId
            """)
    Optional<LectureUnitMaterialVersionsDTO> findMaterialVersionsById(@Param("lectureUnitId") long lectureUnitId);

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
