package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final String ENTITY_NAME = "lecture transcription";

    private static final Logger log = LoggerFactory.getLogger(LectureTranscriptionResource.class);

    private final LectureTranscriptionRepository lectureTranscriptionRepository;

    private final LectureUnitRepository lectureUnitRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public LectureTranscriptionResource(LectureTranscriptionRepository transcriptionRepository, LectureUnitRepository lectureUnitRepository) {
        this.lectureTranscriptionRepository = transcriptionRepository;
        this.lectureUnitRepository = lectureUnitRepository;
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
        Optional<LectureTranscription> transcriptionOpt = lectureTranscriptionRepository.findByLectureUnit_Id(lectureUnitId);

        if (transcriptionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        LectureTranscription transcription = transcriptionOpt.get();
        LectureTranscriptionDTO dto = new LectureTranscriptionDTO(lectureUnitId, transcription.getLanguage(), transcription.getSegments());

        return ResponseEntity.ok(dto);
    }

    /**
     * POST /lecture/{lectureId}/lecture-unit/{lectureUnitId}/nebula-transcriber : Create a transcription from Nebula.
     *
     * @param lectureId     the ID of the lecture
     * @param lectureUnitId the ID of the lecture unit
     * @param requestBody   the request containing the video URL
     * @return the created transcription
     * @throws Exception if the Nebula call fails
     */
    @PostMapping("{lectureId}/lecture-unit/{lectureUnitId}/nebula-transcriber")
    public ResponseEntity<LectureTranscription> createFromNebula(@PathVariable Long lectureId, @PathVariable Long lectureUnitId, @RequestBody Map<String, String> requestBody)
            throws Exception {

        // Step 1: Get video URL
        String videoUrl = requestBody.get("videoUrl");

        // Step 2: Prepare request to Nebula
        HttpClient httpClient = HttpClient.newHttpClient();
        ObjectMapper objectMapper = new ObjectMapper();

        Map<String, Object> nebulaRequest = new HashMap<>();
        nebulaRequest.put("video_url", videoUrl);
        nebulaRequest.put("lecture_id", lectureId);

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create("http://localhost:5000/transcribe")).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(nebulaRequest))).build();

        // Step 3: Send request to Nebula and parse response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Nebula call failed: " + response.body());
        }

        // Step 4: Deserialize into LectureTranscriptionDTO
        LectureTranscriptionDTO transcriptionDTO = objectMapper.readValue(response.body(), LectureTranscriptionDTO.class);

        // Step 5: Reuse the existing method to save the transcription
        return createLectureTranscription(transcriptionDTO, lectureId, lectureUnitId);
    }

}
