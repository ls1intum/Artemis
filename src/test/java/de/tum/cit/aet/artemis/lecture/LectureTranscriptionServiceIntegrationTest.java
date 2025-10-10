package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.client.RestTemplate;

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

    @MockitoBean
    private LectureTranscriptionService lectureTranscriptionService;

    @Autowired
    private LectureTranscriptionRepository transcriptionRepository;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @MockitoBean
    private RestTemplate restTemplate;

    @BeforeEach
    void setUp() {
        // Setup will be done in each test method for the mocked service
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
        var t = new LectureTranscription();
        t.setJobId(jobId);
        t.setTranscriptionStatus(TranscriptionStatus.PENDING);
        t.setLectureUnit(unit);
        t = transcriptionRepository.saveAndFlush(t);

        // Mock the service behavior to simulate successful transcription processing
        doAnswer(invocation -> {
            LectureTranscription transcription = invocation.getArgument(0);
            transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
            transcription.setLanguage("en");
            transcriptionRepository.save(transcription);
            return null;
        }).when(lectureTranscriptionService).processTranscription(any(LectureTranscription.class));

        lectureTranscriptionService.processTranscription(t);

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

        when(restTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-err"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(errorResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
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

        when(restTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-running"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(runningResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

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

        when(restTemplate.exchange(eq("http://localhost:8080/transcribe/status/job-processing"), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(NebulaTranscriptionStatusResponseDTO.class))).thenReturn(new ResponseEntity<>(processingResponse, HttpStatus.OK));

        lectureTranscriptionService.processTranscription(t);

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

        lectureTranscriptionService.saveFinalTranscriptionResult(jobId, dto);

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

        lectureTranscriptionService.markTranscriptionAsFailed(t, "nope");

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

        var existing = new LectureTranscription();
        existing.setLectureUnit(unit);
        existing.setJobId("old-job");
        existing.setTranscriptionStatus(TranscriptionStatus.PENDING);
        existing = transcriptionRepository.save(existing);
        Long existingId = existing.getId();

        lectureTranscriptionService.createEmptyTranscription(lectureId, unitId, "job-new");

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

        assertThatThrownBy(() -> lectureTranscriptionService.createEmptyTranscription(lectureId, unitId, "job-z")).isInstanceOf(IllegalArgumentException.class);

        var transcriptions = transcriptionRepository.findByLectureUnit_Id(unitId);
        assertThat(transcriptions).isEmpty();
    }
}
