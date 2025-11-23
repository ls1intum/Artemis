package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.NebulaTranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionInitResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionStatusResponseDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private LectureTranscriptionRepository transcriptionRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    private LectureTranscription createTranscription(String jobId, TranscriptionStatus status) {
        var transcription = new LectureTranscription();
        transcription.setJobId(jobId);
        transcription.setTranscriptionStatus(status);
        return transcription;
    }

    @BeforeEach
    void setUp() {
        // Set configuration values via reflection (consider making these configurable via test properties instead)
        ReflectionTestUtils.setField(lectureTranscriptionService, "nebulaBaseUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(lectureTranscriptionService, "nebulaSecretToken", "test-token");
    }

    @Test
    void processTranscription_done_savesCompleteTranscription() {
        // Real lecture + unit, but persist the unit THROUGH the lecture's list to set the order/index
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        // Important: add to the lecture's list so @OrderColumn (or similar) is set
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);  // persists unit with a non-null list index

        // Re-read the persisted unit (id assigned now)
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);

        var jobId = "job-123";
        var t = createTranscription(jobId, TranscriptionStatus.PENDING);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        NebulaTranscriptionStatusResponseDTO response = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.DONE, null, "en", List.of());

        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/status/" + jobId), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(response, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

        var saved = transcriptionRepository.findByJobId(jobId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(saved.get().getLanguage()).isEqualTo("en");
    }

    @Test
    void processTranscription_error_marksFailedAndSaves() {
        var t = createTranscription("job-err", TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);

        NebulaTranscriptionStatusResponseDTO errorResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.ERROR, "Boom!", null, null);

        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-err"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(errorResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        var saved = transcriptionRepository.findByJobId("job-err");
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
    }

    @Test
    void processTranscription_running_noStatusChange() {
        var t = createTranscription("job-running", TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);
        Long id = t.getId();

        NebulaTranscriptionStatusResponseDTO runningResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.RUNNING, null, null, null);

        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-running"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(runningResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

        var unchanged = transcriptionRepository.findById(id);
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void processTranscription_processing_noStatusChange() {
        var t = createTranscription("job-processing", TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);
        Long id = t.getId();

        NebulaTranscriptionStatusResponseDTO processingResponse = new NebulaTranscriptionStatusResponseDTO(NebulaTranscriptionStatus.PROCESSING, null, null, null);

        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-processing"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(processingResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

        var unchanged = transcriptionRepository.findById(id);
        assertThat(unchanged).isPresent();
        assertThat(unchanged.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void startNebulaTranscription_deletesExistingAndCreatesPending() {
        var lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lecture = lectureRepository.save(lecture);
        Long lectureId = lecture.getId();

        var unit = new AttachmentVideoUnit();
        unit.setName("Test Unit");
        unit.setLecture(lecture);
        unit = lectureUnitRepository.save(unit);
        Long unitId = unit.getId();

        // Create existing transcription that should be deleted
        var existing = createTranscription("old-job", TranscriptionStatus.PENDING);
        existing.setLectureUnit(unit);
        existing = transcriptionRepository.save(existing);
        Long existingId = existing.getId();

        // Mock Nebula start response
        var nebulaResponse = new NebulaTranscriptionInitResponseDTO("job-new", "pending");
        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/start"), eq(HttpMethod.POST), any(HttpEntity.class), eq(NebulaTranscriptionInitResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(nebulaResponse, HttpStatus.OK));

        // Start transcription through public API
        var request = new NebulaTranscriptionRequestDTO("http://example.com/video.mp4", lectureId, unitId);
        var jobId = lectureTranscriptionService.startNebulaTranscription(lectureId, unitId, request);

        // Verify old transcription was deleted and new one created
        assertThat(jobId).isEqualTo("job-new");
        assertThat(transcriptionRepository.findById(existingId)).isEmpty();

        var newTranscription = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(newTranscription).isPresent();
        assertThat(newTranscription.get().getJobId()).isEqualTo("job-new");
        assertThat(newTranscription.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(newTranscription.get().getLectureUnit().getId()).isEqualTo(unitId);
    }

    @Test
    void startNebulaTranscription_wrongLecture_throws() {
        var lecture1 = new Lecture();
        lecture1.setTitle("Lecture 23791");
        lecture1 = lectureRepository.save(lecture1);
        Long lectureId = lecture1.getId();

        var lecture2 = new Lecture();
        lecture2.setTitle("Lecture 2");
        lecture2 = lectureRepository.save(lecture2);

        var unit = new AttachmentVideoUnit();
        unit.setName("Unit for Lecture 2");
        unit.setLecture(lecture2);
        unit = lectureUnitRepository.save(unit);
        Long unitId = unit.getId();

        // Mock Nebula start response (even though we won't reach it)
        var nebulaResponse = new NebulaTranscriptionInitResponseDTO("job-z", "pending");
        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/start"), eq(HttpMethod.POST), any(HttpEntity.class), eq(NebulaTranscriptionInitResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(nebulaResponse, HttpStatus.OK));

        var request = new NebulaTranscriptionRequestDTO("http://example.com/video.mp4", lectureId, unitId);

        // Should throw because unit doesn't belong to lecture1
        assertThatThrownBy(() -> lectureTranscriptionService.startNebulaTranscription(lectureId, unitId, request)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Lecture Unit does not belong to the Lecture");

        var transcriptions = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(transcriptions).isEmpty();
    }

    @Test
    void cancelNebulaTranscription_success() {
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);

        var jobId = "job-cancel-123";
        var t = createTranscription(jobId, TranscriptionStatus.PENDING);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        // Mock Nebula cancel response
        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/cancel/" + jobId), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        lectureTranscriptionService.cancelNebulaTranscription(unit.getId());

        // Verify transcription was deleted
        var transcriptions = transcriptionRepository.findByLectureUnit_Id(unit.getId());
        assertThat(transcriptions).isEmpty();
    }

    @Test
    void cancelNebulaTranscription_notFound() {
        assertThatThrownBy(() -> lectureTranscriptionService.cancelNebulaTranscription(999L)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No transcription found");
    }

    @Test
    void cancelNebulaTranscription_completedTranscription_throwsBadRequest() {
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);
        final Long unitId = unit.getId();

        var jobId = "job-completed";
        var t = createTranscription(jobId, TranscriptionStatus.COMPLETED);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        assertThatThrownBy(() -> lectureTranscriptionService.cancelNebulaTranscription(unitId)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot cancel a completed transcription");
    }

    @Test
    void cancelNebulaTranscription_failedTranscription_throwsBadRequest() {
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);
        final Long unitId = unit.getId();

        var jobId = "job-failed";
        var t = createTranscription(jobId, TranscriptionStatus.FAILED);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        assertThatThrownBy(() -> lectureTranscriptionService.cancelNebulaTranscription(unitId)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Cannot cancel a failed transcription");
    }

    @Test
    void cancelNebulaTranscription_noJobId_throwsBadRequest() {
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);
        final Long unitId = unit.getId();

        var t = new LectureTranscription();
        t.setJobId(null);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        assertThatThrownBy(() -> lectureTranscriptionService.cancelNebulaTranscription(unitId)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Transcription has no job ID");
    }

    @Test
    void cancelNebulaTranscription_nebulaError_throwsInternalServerError() {
        var lecture = new Lecture();
        lecture.setTitle("L1");
        lecture = lectureRepository.saveAndFlush(lecture);

        var unit = new AttachmentVideoUnit();
        unit.setName("U1");
        unit.setLecture(lecture);
        lecture.addLectureUnit(unit);
        lecture = lectureRepository.saveAndFlush(lecture);
        unit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);
        final Long unitId = unit.getId();

        var jobId = "job-error";
        var t = createTranscription(jobId, TranscriptionStatus.PENDING);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        // Mock Nebula cancel error
        when(nebulaRestTemplate.exchange(eq("http://localhost:8080/transcribe/cancel/" + jobId), eq(HttpMethod.POST), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(new RuntimeException("Nebula service error"));

        assertThatThrownBy(() -> lectureTranscriptionService.cancelNebulaTranscription(unitId)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Failed to cancel transcription");
    }
}
