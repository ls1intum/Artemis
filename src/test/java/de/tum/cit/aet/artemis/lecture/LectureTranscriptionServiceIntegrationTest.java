package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.NebulaTranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionStatusResponseDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private LectureTranscriptionService service;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LectureTranscriptionRepository transcriptionRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    // Deep-stubbed RestClient so we can call get().uri(...).retrieve().body(...)
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        restClient = mock(RestClient.class, RETURNS_DEEP_STUBS);
        // swap RestClient and ObjectMapper inside the already-constructed bean
        ReflectionTestUtils.setField(service, "restClient", restClient);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
    }

    @Test
    void processTranscription_done_savesCompleteTranscription() {
        var jobId = "job-123";
        var t = new LectureTranscription();
        t.setJobId(jobId);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);

        // Mock Nebula status response using the new DTO
        NebulaTranscriptionStatusResponseDTO response = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.DONE, null, "en", List.of());

        when(restClient.get().uri(eq("/transcribe/status/" + jobId)).retrieve().body(eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(response);

        service.processTranscription(t);

        // Verify the transcription was saved with COMPLETED status
        var saved = transcriptionRepository.findByJobId(jobId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(saved.get().getLanguage()).isEqualTo("en");
    }

    @Test
    void processTranscription_error_marksFailedAndSaves() {
        var t = new LectureTranscription();
        t.setJobId("job-err");
        t = transcriptionRepository.save(t);

        NebulaTranscriptionStatusResponseDTO errorResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.ERROR, "Boom!", null, null);

        when(restClient.get().uri(eq("/transcribe/status/job-err")).retrieve().body(eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(errorResponse);

        service.processTranscription(t);

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        // Check that it was saved by fetching from DB
        var saved = transcriptionRepository.findByJobId("job-err");
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
    }

    @Test
    void processTranscription_running_noStatusChange() {
        var t = new LectureTranscription();
        t.setJobId("job-running");
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);
        Long id = t.getId();

        NebulaTranscriptionStatusResponseDTO runningResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.RUNNING, null, null, null);

        when(restClient.get().uri(eq("/transcribe/status/job-running")).retrieve().body(eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(runningResponse);

        service.processTranscription(t);

        // Status should still be PENDING (not changed)
        var unchanged = transcriptionRepository.findById(id);
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void processTranscription_processing_noStatusChange() {
        var t = new LectureTranscription();
        t.setJobId("job-processing");
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);
        Long id = t.getId();

        NebulaTranscriptionStatusResponseDTO processingResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.PROCESSING, null, null, null);

        when(restClient.get().uri(eq("/transcribe/status/job-processing")).retrieve().body(eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(processingResponse);

        service.processTranscription(t);

        // Status should still be PENDING (not changed)
        var unchanged = transcriptionRepository.findById(id);
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void saveFinalTranscriptionResult_setsFieldsAndSaves() {
        var jobId = "job-done-1";
        var existing = new LectureTranscription();
        existing.setJobId(jobId);
        existing.setTranscriptionStatus(TranscriptionStatus.PENDING);
        existing = transcriptionRepository.save(existing);

        var dto = new LectureTranscriptionDTO(null, "en", java.util.List.of());

        service.saveFinalTranscriptionResult(jobId, dto);

        var saved = transcriptionRepository.findByJobId(jobId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(saved.get().getLanguage()).isEqualTo("en");
        assertThat(saved.get().getSegments()).isNotNull();
    }

    @Test
    void markTranscriptionAsFailed_setsStatusAndSaves() {
        var t = new LectureTranscription();
        t.setJobId("job-x");
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);

        service.markTranscriptionAsFailed(t, "nope");

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        var saved = transcriptionRepository.findByJobId("job-x");
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
    }

    @Test
    void createEmptyTranscription_deletesExistingAndCreatesPending() {
        // Create a real lecture
        var lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lecture = lectureRepository.save(lecture);
        Long lectureId = lecture.getId();

        // Create a real lecture unit (using AttachmentVideoUnit which is concrete)
        var unit = new AttachmentVideoUnit();
        unit.setName("Test Unit");
        unit.setLecture(lecture);
        unit = lectureUnitRepository.save(unit);
        Long unitId = unit.getId();

        // Create an existing transcription
        var existing = new LectureTranscription();
        existing.setLectureUnit(unit);
        existing.setJobId("old-job");
        existing.setTranscriptionStatus(TranscriptionStatus.PENDING);
        existing = transcriptionRepository.save(existing);
        Long existingId = existing.getId();

        // Call the service
        service.createEmptyTranscription(lectureId, unitId, "job-new");

        // Verify the old one was deleted
        assertThat(transcriptionRepository.findById(existingId)).isEmpty();

        // Verify a new one was created
        var newTranscription = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(newTranscription).isPresent();
        assertThat(newTranscription.get().getJobId()).isEqualTo("job-new");
        assertThat(newTranscription.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(newTranscription.get().getLectureUnit().getId()).isEqualTo(unitId);
    }

    @Test
    void createEmptyTranscription_wrongLecture_throws() {
        // Create two different lectures
        var lecture1 = new Lecture();
        lecture1.setTitle("Lecture 1");
        lecture1 = lectureRepository.save(lecture1);
        Long lectureId = lecture1.getId();

        var lecture2 = new Lecture();
        lecture2.setTitle("Lecture 2");
        lecture2 = lectureRepository.save(lecture2);

        // Create a unit belonging to lecture2 (using AttachmentVideoUnit which is concrete)
        var unit = new AttachmentVideoUnit();
        unit.setName("Unit for Lecture 2");
        unit.setLecture(lecture2);
        unit = lectureUnitRepository.save(unit);
        Long unitId = unit.getId();

        // Try to create transcription with mismatched lecture ID (should throw)
        assertThatThrownBy(() -> service.createEmptyTranscription(lectureId, unitId, "job-z")).isInstanceOf(IllegalArgumentException.class);

        // Verify no transcription was created
        var transcriptions = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(transcriptions).isEmpty();
    }
}
