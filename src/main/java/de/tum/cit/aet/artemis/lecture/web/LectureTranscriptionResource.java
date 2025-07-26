package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.ManualConfig;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionInitResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureService;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.lecture.service.TumLiveService;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class LectureTranscriptionResource {

    private static final String ENTITY_NAME = "lecture transcription";

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionResource.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    private final UserRepository userRepository;

    private final AuthorizationCheckService authCheckService;

    private final LectureRepository lectureRepository;

    private final RestClient.Builder restClientBuilder;

    private final LectureTranscriptionService lectureTranscriptionService;

    private final TumLiveService tumLiveService;

    @Value("${artemis.nebula.base-url}")
    private String nebulaBaseUrl;

    @Value("${artemis.nebula.secret-token}")
    private String nebulaSecretToken;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureService lectureService;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureUnitRepository lectureUnitRepository,
            AuthorizationCheckService authCheckService, UserRepository userRepository, LectureRepository lectureRepository, LectureService lectureService,
            RestClient.Builder restClientBuilder, LectureTranscriptionService lectureTranscriptionService, TumLiveService tumLiveService) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
        this.restClientBuilder = restClientBuilder;
        this.lectureTranscriptionService = lectureTranscriptionService;
        this.tumLiveService = tumLiveService;
    }

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
     * POST lecture/{lectureId}/lecture-unit/{lectureUnitId}/ingest-transcription
     * This endpoint is for starting the ingestion of all lectures or only one lecture when triggered in Artemis.
     *
     * @param lectureId     The id of the lecture of the transcription
     * @param lectureUnitId The id of the lectureUnit that should be ingested
     * @return the ResponseEntity with status 200 (OK) and a message success or null if the operation failed
     */
    @Profile(PROFILE_IRIS)
    @PutMapping("{lectureId}/lecture-unit/{lectureUnitId}/ingest-transcription")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> ingestTranscriptions(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(lecture, user);
        Course course = lecture.getCourse();
        authCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitDoesNotMatchLecture")).body(null);
        }
        Optional<LectureTranscription> transcription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        if (transcription.isEmpty()) {
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "noTranscription"))
                    .body(null);
        }
        if (!(lectureUnit instanceof AttachmentVideoUnit)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitIsNotAAttachmentVideoUnit"))
                    .body(null);
        }
        LectureTranscription transcriptionToIngest = transcription.get();
        lectureService.ingestTranscriptionInPyris(transcriptionToIngest, course, lecture, (AttachmentVideoUnit) lectureUnit);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /lecture/:lectureId/lecture-unit/:lectureUnitId : delete the "id" lecture transcription.
     *
     * @param lectureId     the id of the lecture containing the lecture transcription
     * @param lectureUnitId the id of the lecture unit containing the lecture transcription
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("{lectureId}/lecture-unit/{lectureUnitId}/transcription")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteLectureTranscription(@PathVariable Long lectureId, @PathVariable Long lectureUnitId) {
        LectureUnit lectureUnit = lectureUnitRepository.findById(lectureUnitId).orElseThrow();
        if (!lectureUnit.getLecture().getId().equals(lectureId)) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "lectureUnitDoesNotMatchLecture")).body(null);
        }

        Course course = lectureUnit.getLecture().getCourse();
        authCheckService.checkIsAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course.getId());

        Optional<LectureTranscription> lectureTranscription = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);
        if (lectureTranscription.isEmpty()) {
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.transcriptionIngestionError", "noTranscriptionForId")).body(null);
        }
        log.debug("REST request to delete Lecture Transcription : {}", lectureTranscription.get().getId());
        lectureService.deleteLectureTranscriptionInPyris(lectureTranscription.get());
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureTranscription.get().getId().toString())).build();
    }

    /**
     * POST /lecture/{lectureId}/lecture-unit/{lectureUnitId}/nebula-transcriber :
     * Start and complete the transcription process for a lecture video using Nebula.
     * This method sends the video URL to Nebula, waits for the transcription result,
     * and saves the transcription immediately into the system.
     *
     * @param lectureId     the ID of the lecture
     * @param lectureUnitId the ID of the lecture unit
     * @param request       the request containing the video URL and any additional options
     * @return the ResponseEntity with status 200 (OK) and the saved transcription,
     *         or 500 (Internal Server Error) if an error occurs
     */

    @PostMapping("{lectureId}/lecture-unit/{lectureUnitId}/nebula-transcriber")
    @EnforceAtLeastInstructor
    public ResponseEntity<?> startNebulaTranscriptionAndSave(@PathVariable Long lectureId, @PathVariable Long lectureUnitId,
            @RequestBody @Valid NebulaTranscriptionRequestDTO request) {

        try {
            RestClient nebulaRestClient = restClientBuilder.baseUrl(nebulaBaseUrl).build();

            NebulaTranscriptionInitResponseDTO response = nebulaRestClient.post().uri("/transcribe/start").header("Content-Type", "application/json")
                    .header("Authorization", nebulaSecretToken).body(request).retrieve().body(NebulaTranscriptionInitResponseDTO.class);

            // Null or invalid response check
            if (response.transcriptionId() == null) {
                log.error("Nebula returned null or missing transcription ID for Lecture ID {}, Unit ID {}", lectureId, lectureUnitId);
                return ResponseEntity.internalServerError().body("Nebula did not return a valid transcription ID.");
            }

            // Create placeholder transcription for async processing
            lectureTranscriptionService.createEmptyTranscription(lectureId, lectureUnitId, response.transcriptionId());

            log.info("Transcription started for Lecture ID {}, Unit ID {}, Job ID: {}", lectureId, lectureUnitId, response.transcriptionId());
            return ResponseEntity.ok("Transcription started. Job ID: " + response.transcriptionId());

        }
        catch (Exception e) {
            log.error("Error initiating transcription for Lecture ID: {}, Unit ID: {} → {}", lectureId, lectureUnitId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body("Failed to start transcription: " + e.getMessage());
        }
    }

    /**
     * REST endpoint to fetch the TUM Live playlist URL for a given TUM Live video page URL.
     * <p>
     * This endpoint checks whether a playlist (e.g., an .m3u8 stream) is available for the
     * specified video URL from TUM Live and returns it if found.
     * </p>
     *
     * @param url the full TUM Live video page URL
     * @return {@code 200 OK} with the playlist URL if available,
     *         or {@code 404 Not Found} if no playlist could be retrieved.
     */
    @GetMapping("video-utils/tum-live-playlist")
    public ResponseEntity<String> getTumLivePlaylist(@RequestParam String url) {
        log.info("Received request to fetch playlist for TUM Live URL: {}", url);

        Optional<String> playlistUrl = tumLiveService.getTumLivePlaylistLink(url);

        if (playlistUrl.isPresent()) {
            log.info("Playlist URL found: {}", playlistUrl.get());
            return ResponseEntity.ok(playlistUrl.get());
        }
        else {
            log.warn("No playlist URL found for: {}", url);
            return ResponseEntity.notFound().build();
        }
    }

}
