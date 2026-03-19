package de.tum.cit.aet.artemis.iris.service.pyris.dto.lectureingestionwebhook;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record PyrisLectureTranscriptionDTO(Long id, @Nullable String language, List<LectureTranscriptionSegment> segments, @Nullable String jobId,
        TranscriptionStatus transcriptionStatus) {

    /**
     * Creates a DTO from a {@link LectureTranscription} entity.
     *
     * @param transcription the lecture transcription entity to convert
     * @return the corresponding DTO
     */
    public static PyrisLectureTranscriptionDTO of(LectureTranscription transcription) {
        return new PyrisLectureTranscriptionDTO(transcription.getId(), transcription.getLanguage(), transcription.getSegments(), transcription.getJobId(),
                transcription.getTranscriptionStatus());
    }
}
