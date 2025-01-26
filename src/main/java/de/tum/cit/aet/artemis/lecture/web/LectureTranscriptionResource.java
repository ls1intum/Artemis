package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionSegmentDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LectureTranscriptionResource {

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionResource.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureRepository lectureRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureRepository lectureRepository, LectureUnitRepository lectureUnitRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureRepository = lectureRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
    }

    /**
     * POST /transcription : Create a new transcription.
     *
     * @param courseId         The id of the course
     * @param lectureId        The id of the lecture
     * @param transcriptionDTO The transcription object to create
     * @return The ResponseEntity with status 201 (Created) and with body the new transcription, or with status 400 (Bad Request) if the transcription has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping(value = "courses/{courseId}/lecture/{lectureId}/transcriptions")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<LectureTranscription> createLectureTranscription(@Valid @RequestBody LectureTranscriptionDTO transcriptionDTO, @PathVariable Long courseId,
            @PathVariable Long lectureId) throws URISyntaxException {
        Lecture lecture = lectureRepository.findById(transcriptionDTO.lectureId()).orElseThrow(() -> new EntityNotFoundException("no lecture found for this id"));

        Set<Long> lectureUnitIds = transcriptionDTO.segments().stream().map(LectureTranscriptionSegmentDTO::lectureUnitId).collect(Collectors.toSet());
        Set<LectureUnit> lectureUnits = lectureUnitRepository.findAllByIdIn(lectureUnitIds);

        List<LectureTranscriptionSegment> segments = transcriptionDTO.segments().stream().map(segment -> {
            LectureUnit lectureUnit = lectureUnits.stream().filter(lu -> lu.getId().equals(segment.lectureUnitId())).findFirst().orElseThrow();
            return new LectureTranscriptionSegment(segment.startTime(), segment.endTime(), segment.text(), lectureUnit, segment.slideNumber());
        }).toList();

        LectureTranscription lectureTranscription = new LectureTranscription(lecture, transcriptionDTO.language(), segments);
        lectureTranscription.setId(null);

        LectureTranscription result = lectureTranscriptionRepository.save(lectureTranscription);

        return ResponseEntity.created(new URI("/api/lecture/" + lectureId + "/transcriptions/" + result.getId())).body(result);
    }

    /**
     * GET /lectures/:lectureId/transcriptions/:transcriptionId : get the transcription for the transcriptionId.
     *
     * @param lectureId       the lectureId of the lecture from the transcription
     * @param transcriptionId the transcriptionId of the transcription to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the transcription, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/transcription/{transcriptionId}")
    @EnforceAtLeastStudent
    public ResponseEntity<LectureTranscription> getLectureTranscriptions(@PathVariable Long lectureId, @PathVariable Long transcriptionId) {
        log.debug("REST request to get transcription {}", transcriptionId);
        LectureTranscription transcription = lectureTranscriptionRepository.findById(transcriptionId).orElseThrow();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(transcription.getLecture(), userRepository.getUserWithGroupsAndAuthorities());

        return ResponseEntity.ok(transcription);
    }
}
