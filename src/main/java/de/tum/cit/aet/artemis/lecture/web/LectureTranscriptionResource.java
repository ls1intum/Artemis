package de.tum.cit.aet.artemis.lecture.web;

import java.util.Optional;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastEditorInLectureUnit;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastStudentInLectureUnit;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;

@Conditional(LectureEnabled.class)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class LectureTranscriptionResource {

    private final LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi;

    private final AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    private final AuthorizationCheckService authorizationCheckService;

    public LectureTranscriptionResource(LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi, AttachmentVideoUnitRepository attachmentVideoUnitRepository,
            AuthorizationCheckService authorizationCheckService) {
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
        this.attachmentVideoUnitRepository = attachmentVideoUnitRepository;
        this.authorizationCheckService = authorizationCheckService;
    }

    /**
     * Retrieves the transcript for a given lecture unit.
     *
     * <p>
     * This endpoint returns the transcript associated with the specified {@code lectureUnitId}, including
     * the language and individual transcript segments.
     * </p>
     *
     * @param lectureUnitId the ID of the lecture unit for which to retrieve the transcript
     * @return {@link ResponseEntity} containing the {@link LectureTranscriptionDTO} if found, or 404 Not Found if no transcript exists
     */
    @GetMapping({ "lecture-units/{lectureUnitId}/transcript", "lecture-unit/{lectureUnitId}/transcript" })
    @EnforceAtLeastStudentInLectureUnit
    public ResponseEntity<LectureTranscriptionDTO> getTranscript(@PathVariable Long lectureUnitId) {
        // The role check only covers the course, so the unit itself has to be available for this request
        Optional<AttachmentVideoUnit> unit = attachmentVideoUnitRepository.findWithLectureAndCourseAndAttachmentById(lectureUnitId);
        if (unit.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!authorizationCheckService.isAllowedToSeeLectureUnit(unit.get(), null)) {
            throw new AccessForbiddenException("AttachmentVideoUnit", lectureUnitId);
        }
        Optional<LectureTranscriptionDTO> dtoOpt = lectureTranscriptionsRepositoryApi.getTranscript(lectureUnitId);

        if (dtoOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(dtoOpt.get());
    }

    /**
     * GET /lecture-unit/{lectureUnitId}/transcript/status : Get the status of a transcription for a lecture unit.
     *
     * @param lectureUnitId the ID of the lecture unit to check
     * @return ResponseEntity with the transcription status (PENDING, COMPLETED, FAILED) or 404 if no transcription exists
     */
    @GetMapping({ "lecture-units/{lectureUnitId}/transcript/status", "lecture-unit/{lectureUnitId}/transcript/status" })
    @EnforceAtLeastEditorInLectureUnit
    public ResponseEntity<String> getTranscriptStatus(@PathVariable Long lectureUnitId) {
        Optional<String> statusOpt = lectureTranscriptionsRepositoryApi.getTranscriptStatus(lectureUnitId);

        if (statusOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statusOpt.get());
    }

}
