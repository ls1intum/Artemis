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
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionStatus;
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
        lecture.getLectureUnits().add(unit);
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
    void saveFinalTranscriptionResult_setsFieldsAndSaves() {
        var jobId = "job-done-1";
        var existing = createTranscription(jobId, TranscriptionStatus.PENDING);
        existing = transcriptionRepository.save(existing);

        var dto = new LectureTranscriptionDTO(null, "en", java.util.List.of());

        ReflectionTestUtils.invokeMethod(lectureTranscriptionService, "saveFinalTranscriptionResult", jobId, dto);

        var saved = transcriptionRepository.findByJobId(jobId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(saved.get().getLanguage()).isEqualTo("en");
        assertThat(saved.get().getSegments()).isNotNull();
    }

    @Test
    void markTranscriptionAsFailed_setsStatusAndSaves() {
        var t = createTranscription("job-x", TranscriptionStatus.PENDING);
        t = transcriptionRepository.save(t);

        ReflectionTestUtils.invokeMethod(lectureTranscriptionService, "markTranscriptionAsFailed", t, "nope");

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        var saved = transcriptionRepository.findByJobId("job-x");
        assertThat(saved).isPresent();
        assertThat(saved.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
    }

    @Test
    void createEmptyTranscription_deletesExistingAndCreatesPending() {
        var lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lecture = lectureRepository.save(lecture);
        Long lectureId = lecture.getId();

        var unit = new AttachmentVideoUnit();
        unit.setName("Test Unit");
        unit.setLecture(lecture);
        unit = lectureUnitRepository.save(unit);
        Long unitId = unit.getId();

        var existing = createTranscription("old-job", TranscriptionStatus.PENDING);
        existing.setLectureUnit(unit);
        existing = transcriptionRepository.save(existing);
        Long existingId = existing.getId();

        ReflectionTestUtils.invokeMethod(lectureTranscriptionService, "createEmptyTranscription", lectureId, unitId, "job-new");

        assertThat(transcriptionRepository.findById(existingId)).isEmpty();

        var newTranscription = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(newTranscription).isPresent();
        assertThat(newTranscription.get().getJobId()).isEqualTo("job-new");
        assertThat(newTranscription.get().getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(newTranscription.get().getLectureUnit().getId()).isEqualTo(unitId);
    }

    @Test
    void createEmptyTranscription_wrongLecture_throws() {
        var lecture1 = new Lecture();
        lecture1.setTitle("Lecture 1");
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

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(lectureTranscriptionService, "createEmptyTranscription", lectureId, unitId, "job-z"))
                .isInstanceOf(ResponseStatusException.class);

        var transcriptions = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(transcriptions).isEmpty();
    }
}
