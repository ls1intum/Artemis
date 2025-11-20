package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLectureUnit.EnforceAtLeastInstructorInLectureUnit;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.api.LectureTranscriptionsRepositoryApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class LectureTranscriptionResource {

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureUnitRepository lectureUnitRepository,
            LectureTranscriptionsRepositoryApi lectureTranscriptionsRepositoryApi) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.lectureTranscriptionsRepositoryApi = lectureTranscriptionsRepositoryApi;
    }

    // TODO: this must either be moved into an Admin Resource or used differently
    /**
     * POST /transcription : Create a new transcription.
     *
     * @param lectureId        The id of the lecture
     * @param lectureUnitId    The id of the lecture unit
     * @param transcriptionDTO The transcription object to create
     * @return The ResponseEntity with status 201 (Created) and with body the new transcription, or with status 400 (Bad Request) if invalid lectureId or lectureUnitId are given
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "{lectureId}/lecture-unit/{lectureUnitId}/transcription")
    @EnforceAdmin
    @ManualConfig
    public ResponseEntity<LectureTranscription> createLectureTranscription(@Valid @RequestBody LectureTranscriptionDTO transcriptionDTO, @PathVariable Long lectureId,
            @PathVariable Long lectureUnitId) throws URISyntaxException {
        LectureUnit lectureUnit = lectureUnitRepository.findByIdElseThrow(lectureUnitId);
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.wrongLectureError", "lectureDoesNotMatchCourse"))
                    .body(null);
        }

        var existingTranscription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        existingTranscription.ifPresent(lectureTranscription -> lectureTranscriptionRepository.deleteById(lectureTranscription.getId()));

        LectureTranscription lectureTranscription = new LectureTranscription(transcriptionDTO.language(), transcriptionDTO.segments(), lectureUnit);

        LectureTranscription result = lectureTranscriptionRepository.save(lectureTranscription);

        return ResponseEntity.created(new URI("/api/lecture/" + lectureId + "/transcriptions/" + result.getId())).body(result);
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
    @GetMapping("lecture-unit/{lectureUnitId}/transcript")
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<LectureTranscriptionDTO> getTranscript(@PathVariable Long lectureUnitId) {
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
     * @return ResponseEntity with the transcription status (PENDING, PROCESSING, COMPLETED, FAILED) or 404 if no transcription exists
     */
    @GetMapping("lecture-unit/{lectureUnitId}/transcript/status")
    @EnforceAtLeastInstructorInLectureUnit
    public ResponseEntity<String> getTranscriptStatus(@PathVariable Long lectureUnitId) {
        Optional<String> statusOpt = lectureTranscriptionsRepositoryApi.getTranscriptStatus(lectureUnitId);

        if (statusOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statusOpt.get());
    }

}
