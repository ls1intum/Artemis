package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Slide;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record SlideDTO(Long id, int slideNumber, ZonedDateTime hidden, Long attachmentUnitId) {

    public static SlideDTO from(Slide slide) {
        return new SlideDTO(slide.getId(), slide.getSlideNumber(), slide.getHidden(), slide.getAttachmentUnit().getId());
    }
}
