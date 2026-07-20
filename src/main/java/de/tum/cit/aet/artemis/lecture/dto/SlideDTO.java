package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Slide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SlideDTO(Long id, int slideNumber, ZonedDateTime hidden, Long attachmentVideoUnitId, SlideExerciseDTO exercise) {

    /**
     * Lightweight reference to the exercise a (hidden) slide is linked to. The PDF preview reads {@code slide.exercise.id}
     * to preserve the exercise-linked hide date when saving, so the response must keep this reference.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SlideExerciseDTO(Long id) {
    }

    /**
     * Convenience constructor used by the JPQL projection in {@code SlideRepository} and by the lectures-with-slides
     * endpoint, where the exercise reference is not needed.
     */
    public SlideDTO(Long id, int slideNumber, ZonedDateTime hidden, Long attachmentVideoUnitId) {
        this(id, slideNumber, hidden, attachmentVideoUnitId, null);
    }

    public static SlideDTO from(Slide slide) {
        SlideExerciseDTO exercise = slide.getExercise() != null ? new SlideExerciseDTO(slide.getExercise().getId()) : null;
        return new SlideDTO(slide.getId(), slide.getSlideNumber(), slide.getHidden(), slide.getAttachmentVideoUnit().getId(), exercise);
    }
}
