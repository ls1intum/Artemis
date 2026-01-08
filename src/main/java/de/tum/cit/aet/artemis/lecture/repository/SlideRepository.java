package de.tum.cit.aet.artemis.lecture.repository;

import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.Slide;
import de.tum.cit.aet.artemis.lecture.dto.SlideDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO;

/**
 * Spring Data JPA repository for the Attachment Unit entity.
 */
@Conditional(LectureEnabled.class)
@Lazy
@Repository
public interface SlideRepository extends ArtemisJpaRepository<Slide, Long> {

    Slide findSlideByAttachmentVideoUnitIdAndSlideNumber(long attachmentVideoUnitId, int slideNumber);

    List<Slide> findAllByAttachmentVideoUnitId(Long attachmentUnitId);

    /**
     * Find all slides with non-null hidden field but only returns the id and hidden fields
     *
     * @return list containing only slide ids and hidden timestamps
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO(s.id, s.hidden)
            FROM Slide s
            WHERE s.hidden IS NOT NULL
            """)
    List<SlideUnhideDTO> findHiddenSlidesProjection();

    /**
     * Find slides for a specific attachment video unit where the hidden field is not null
     * (these are the hidden slides)
     *
     * @param attachmentUnitId The ID of the attachment video unit
     * @return List of hidden slides for the attachment video unit
     */
    List<Slide> findByAttachmentVideoUnitIdAndHiddenNotNull(Long attachmentUnitId);

    /**
     * Find all slides associated with a specific exercise
     *
     * @param exerciseId The ID of the exercise
     * @return List of slides associated with the exercise
     */
    @Query("""
            SELECT s
            FROM Slide s
            WHERE s.exercise.id = :exerciseId
            """)
    List<Slide> findByExerciseId(@Param("exerciseId") Long exerciseId);

    /**
     * Unhides a slide by setting its hidden property to null.
     *
     * @param slideId The ID of the slide to unhide
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Slide s
            SET s.hidden = NULL
            WHERE s.id = :slideId
            """)
    void unhideSlide(@Param("slideId") Long slideId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.SlideDTO(s.id, s.slideNumber, s.hidden, s.attachmentVideoUnit.id)
            FROM Slide s
            WHERE s.attachmentVideoUnit.id IN :attachmentVideoUnitIds
                AND (s.hidden IS NULL OR s.hidden < CURRENT_TIMESTAMP())
            """)
    Set<SlideDTO> findVisibleSlidesByAttachmentVideoUnits(@Param("attachmentVideoUnitIds") Set<Long> attachmentVideoUnitIds);
}
