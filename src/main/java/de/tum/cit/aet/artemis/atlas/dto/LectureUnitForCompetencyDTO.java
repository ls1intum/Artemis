package de.tum.cit.aet.artemis.atlas.dto;

import java.time.ZonedDateTime;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureUnitForCompetencyDTO(Long id, LectureReferenceDTO lecture, String name, @Nullable ZonedDateTime releaseDate, Boolean completed, Boolean visibleToStudents,
        String type, @Nullable String description, @Nullable String source, @Nullable String content, @Nullable AttachmentForCompetencyDTO attachment,
        @Nullable String videoSource) {

    @Nullable
    /**
     * Maps a lecture unit to a competency lecture unit DTO.
     *
     * @param lectureUnit the lecture unit to map
     * @return the DTO or null if the lecture unit is null
     */
    public static LectureUnitForCompetencyDTO of(@Nullable LectureUnit lectureUnit) {
        if (lectureUnit == null) {
            return null;
        }

        String description = null;
        String source = null;
        String content = null;
        AttachmentForCompetencyDTO attachment = null;
        String videoSource = null;

        if (lectureUnit instanceof AttachmentVideoUnit attachmentVideoUnit) {
            description = attachmentVideoUnit.getDescription();
            attachment = AttachmentForCompetencyDTO.of(attachmentVideoUnit.getAttachment());
            videoSource = attachmentVideoUnit.getVideoSource();
        }
        else if (lectureUnit instanceof OnlineUnit onlineUnit) {
            description = onlineUnit.getDescription();
            source = onlineUnit.getSource();
        }
        else if (lectureUnit instanceof TextUnit textUnit) {
            content = textUnit.getContent();
        }

        return new LectureUnitForCompetencyDTO(lectureUnit.getId(), LectureReferenceDTO.of(Objects.requireNonNull(lectureUnit.getLecture())), lectureUnit.getName(),
                lectureUnit.getReleaseDate(), lectureUnit.isCompleted(), lectureUnit.isVisibleToStudents(), lectureUnit.getType(), description, source, content, attachment,
                videoSource);
    }
}
