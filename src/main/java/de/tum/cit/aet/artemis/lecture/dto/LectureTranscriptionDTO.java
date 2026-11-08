package de.tum.cit.aet.artemis.lecture.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LectureTranscriptionDTO(Long lectureUnitId, String language, List<LectureTranscriptionSegmentDTO> segments) {

    /**
     * Copies the stored segments into DTOs.
     *
     * @param lectureUnitId the lecture unit the transcription belongs to
     * @param language      the language of the transcription
     * @param segments      the stored segments
     * @return the transcription as it is sent to clients
     */
    public static LectureTranscriptionDTO of(Long lectureUnitId, String language, List<LectureTranscriptionSegment> segments) {
        return new LectureTranscriptionDTO(lectureUnitId, language, segments.stream().map(LectureTranscriptionSegmentDTO::of).toList());
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record LectureTranscriptionSegmentDTO(Double startTime, Double endTime, String text, int slideNumber) {

        static LectureTranscriptionSegmentDTO of(LectureTranscriptionSegment segment) {
            return new LectureTranscriptionSegmentDTO(segment.startTime(), segment.endTime(), segment.text(), segment.slideNumber());
        }
    }
}
