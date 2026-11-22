package de.tum.cit.aet.artemis.exam.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.core.util.ServedFileUrl;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ExamUserAttendanceCheckDTO(Long id, String studentImagePath, String login, String registrationNumber, String signingImagePath, Boolean started, Boolean submitted) {

    /**
     * Both images come out of their columns as filenames, so they are turned into the paths the client requests them under. The conversion is idempotent.
     */
    public ExamUserAttendanceCheckDTO {
        studentImagePath = ServedFileUrl.examUserImage(id, studentImagePath);
        signingImagePath = ServedFileUrl.examUserSignature(id, signingImagePath);
    }
}
