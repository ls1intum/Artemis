package de.tum.cit.aet.artemis.lecture.repository;

import java.time.ZonedDateTime;
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

    Slide findSlideByAttachmentVideoUnitIdAndSlideNumberAndSupersededIsFalse(long attachmentVideoUnitId, int slideNumber);

    /**
     * The slides of an attachment video unit, in slide order.
     * <p>
     * Ordered explicitly. As a derived query this returned rows in whatever order the database produced them, while
     * callers do treat the result as ordered: the splitter iterates it to renumber and re-hide slides, and
     * SlideSplitterServiceTest asserts the slide number by list position. That assumption held until it did not - the
     * test failed in CI with "expected: 1 but was: 3" on the first element, which is an unordered read rather than a
     * wrong split. Ordering here fixes every caller at once and cannot break one that never cared.
     *
     * @param attachmentUnitId the attachment video unit whose slides are returned
     * @return the slides, ascending by slide number
     */
    @Query("""
            SELECT slide
            FROM Slide slide
            WHERE slide.attachmentVideoUnit.id = :attachmentUnitId
                AND slide.superseded = FALSE
            ORDER BY slide.slideNumber ASC
            """)
    List<Slide> findAllByAttachmentVideoUnitId(@Param("attachmentUnitId") Long attachmentUnitId);

    /**
     * Find all slides with non-null hidden field but only returns the id and hidden fields
     *
     * @return list containing only slide ids and hidden timestamps
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.SlideUnhideDTO(s.id, s.hidden)
            FROM Slide s
            WHERE s.hidden IS NOT NULL
                AND s.superseded = FALSE
            """)
    List<SlideUnhideDTO> findHiddenSlidesProjection();

    /**
     * Find slides for a specific attachment video unit where the hidden field is not null
     * (these are the hidden slides)
     *
     * @param attachmentUnitId The ID of the attachment video unit
     * @return List of hidden slides for the attachment video unit
     */
    List<Slide> findByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(Long attachmentUnitId);

    /**
     * Checks whether the current slide deck of an attachment video unit hides any slide. Superseded slides belong to an earlier file and do not count.
     *
     * @param attachmentUnitId the id of the attachment video unit
     * @return whether at least one current slide is hidden
     */
    boolean existsByAttachmentVideoUnitIdAndHiddenNotNullAndSupersededIsFalse(Long attachmentUnitId);

    /**
     * Finds which of the given attachment video units hide at least one slide of their current deck.
     *
     * @param attachmentUnitIds the ids of the attachment video units to check
     * @return the ids of the units with at least one current hidden slide
     */
    @Query("""
            SELECT DISTINCT s.attachmentVideoUnit.id
            FROM Slide s
            WHERE s.attachmentVideoUnit.id IN :attachmentUnitIds
                AND s.hidden IS NOT NULL
                AND s.superseded = FALSE
            """)
    Set<Long> findAttachmentVideoUnitIdsWithHiddenSlides(@Param("attachmentUnitIds") Set<Long> attachmentUnitIds);

    /**
     * Marks the current slides of a unit as superseded when its file is replaced by a basic upload, but only while the unit still holds the attachment revision that replaced it.
     * <p>
     * The revision check is part of this single statement on purpose. When a newer upload has already saved its attachment revision, the statement matches no slide, so it
     * cannot retire the deck that the newer upload owns. The split job of the older upload then skips itself as obsolete.
     *
     * @param attachmentVideoUnitId the id of the attachment video unit whose deck is replaced
     * @param attachmentId          the id of the attachment the replacement was saved to
     * @param attachmentVersion     the version of the replacement revision
     * @param attachmentSha256Hash  the SHA-256 hash of the replacement revision, may be null
     * @return the number of slides marked as superseded
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Slide s
            SET s.superseded = TRUE
            WHERE s.attachmentVideoUnit.id = :attachmentVideoUnitId
                AND s.superseded = FALSE
                AND EXISTS (
                    SELECT a.id
                    FROM Attachment a
                    WHERE a.id = :attachmentId
                        AND a.attachmentVideoUnit.id = :attachmentVideoUnitId
                        AND a.version = :attachmentVersion
                        AND (a.sha256Hash = :attachmentSha256Hash OR (:attachmentSha256Hash IS NULL AND a.sha256Hash IS NULL))
                )
            """)
    int supersedeCurrentSlidesIfAttachmentRevisionMatches(@Param("attachmentVideoUnitId") long attachmentVideoUnitId, @Param("attachmentId") long attachmentId,
            @Param("attachmentVersion") Integer attachmentVersion, @Param("attachmentSha256Hash") String attachmentSha256Hash);

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
     * Unhides a slide by setting its hidden property to null, but only if its hidden date has passed.
     * A scheduled unhide task that is already running when the hidden date is moved to a later point therefore does not unhide the slide early.
     *
     * @param slideId The ID of the slide to unhide
     * @param now     the current time, the hidden date must not be after it
     * @return the number of updated slides, 0 if the slide is not hidden or its hidden date has not passed yet
     */
    @Transactional // ok because of modifying query
    @Modifying
    @Query("""
            UPDATE Slide s
            SET s.hidden = NULL
            WHERE s.id = :slideId
                AND s.hidden IS NOT NULL
                AND s.hidden <= :now
            """)
    int unhideSlideIfDue(@Param("slideId") Long slideId, @Param("now") ZonedDateTime now);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.lecture.dto.SlideDTO(s.id, s.slideNumber, s.hidden, s.attachmentVideoUnit.id)
            FROM Slide s
            WHERE s.attachmentVideoUnit.id IN :attachmentVideoUnitIds
                AND s.superseded = FALSE
                AND (s.hidden IS NULL OR s.hidden < CURRENT_TIMESTAMP())
            """)
    Set<SlideDTO> findVisibleSlidesByAttachmentVideoUnits(@Param("attachmentVideoUnitIds") Set<Long> attachmentVideoUnitIds);
}
