package de.tum.cit.aet.artemis.lecture.dto;

import java.time.ZonedDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureDetailsDTO(Long id, String title, String description, ZonedDateTime startDate, ZonedDateTime endDate, ZonedDateTime visibleDate,
        @JsonProperty("isTutorialLecture") boolean isTutorialLecture, CourseDTO course, List<LectureUnitDetailsDTO> lectureUnits, List<AttachmentDTO> attachments)
        implements LectureDTO {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CourseDTO(Long id, String title, String shortName, String studentGroupName, String teachingAssistantGroupName, String editorGroupName,
            String instructorGroupName) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AttachmentDTO(Long id, String name, String link, ZonedDateTime releaseDate, ZonedDateTime uploadDate, Integer version, AttachmentType attachmentType,
            String studentVersion) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyDTO(Long id, String title, CompetencyTaxonomy taxonomy) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record CompetencyLinkDTO(CompetencyDTO competency, double weight) {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureReferenceDTO(Long id) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type", visible = true)
    @JsonSubTypes({ @JsonSubTypes.Type(value = AttachmentUnitDTO.class, name = "attachment"), @JsonSubTypes.Type(value = ExerciseUnitDTO.class, name = "exercise"),
            @JsonSubTypes.Type(value = OnlineUnitDTO.class, name = "online"), @JsonSubTypes.Type(value = TextUnitDTO.class, name = "text") })
    public sealed interface LectureUnitDetailsDTO permits AttachmentUnitDTO, ExerciseUnitDTO, OnlineUnitDTO, TextUnitDTO {
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AttachmentUnitDTO(Long id, LectureReferenceDTO lecture, String name, ZonedDateTime releaseDate, boolean completed, boolean visibleToStudents,
            List<CompetencyLinkDTO> competencyLinks, AttachmentDTO attachment, String description, String videoSource, @JsonProperty("type") String type)
            implements LectureUnitDetailsDTO {

        public AttachmentUnitDTO {
            type = "attachment";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ExerciseUnitDTO(Long id, LectureReferenceDTO lecture, String name, ZonedDateTime releaseDate, boolean completed, boolean visibleToStudents,
            List<CompetencyLinkDTO> competencyLinks, Exercise exercise, @JsonProperty("type") String type) implements LectureUnitDetailsDTO {

        public ExerciseUnitDTO {
            type = "exercise";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record OnlineUnitDTO(Long id, LectureReferenceDTO lecture, String name, ZonedDateTime releaseDate, boolean completed, boolean visibleToStudents,
            List<CompetencyLinkDTO> competencyLinks, String description, String source, @JsonProperty("type") String type) implements LectureUnitDetailsDTO {

        public OnlineUnitDTO {
            type = "online";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record TextUnitDTO(Long id, LectureReferenceDTO lecture, String name, ZonedDateTime releaseDate, boolean completed, boolean visibleToStudents,
            List<CompetencyLinkDTO> competencyLinks, String content, @JsonProperty("type") String type) implements LectureUnitDetailsDTO {

        public TextUnitDTO {
            type = "text";
        }
    }
}
