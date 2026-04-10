package de.tum.cit.aet.artemis.atlas.dto;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureReferenceDTO(Long id) {

    @Nullable
    public static LectureReferenceDTO of(@Nullable Lecture lecture) {
        if (lecture == null) {
            return null;
        }
        return new LectureReferenceDTO(lecture.getId());
    }
}
