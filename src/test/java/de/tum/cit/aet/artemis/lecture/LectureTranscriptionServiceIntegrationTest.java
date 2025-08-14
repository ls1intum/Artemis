package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureTranscriptionService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionServiceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    @Autowired
    private LectureTranscriptionService service;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private LectureTranscriptionRepository transcriptionRepository;

    @MockBean
    private LectureUnitRepository lectureUnitRepository;

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
    void processTranscription_done_callsSaveFinalTranscriptionResult() {
        var jobId = "job-123";
        var t = new LectureTranscription();
        t.setJobId(jobId);

        // Nebula status payload → objectMapper.convertValue(...) → DTO
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "done");
        payload.put("language", "en");
        payload.put("segments", java.util.List.of());

        when(restClient.get().uri(eq("/transcribe/status/" + jobId)).retrieve().body(any(ParameterizedTypeReference.class))).thenReturn(payload);

        // Spy to assert the handoff to saveFinalTranscriptionResult(...) (don’t hit DB)
        LectureTranscriptionService spy = spy(service);
        // re-inject the same collaborators into the spy
        ReflectionTestUtils.setField(spy, "restClient", restClient);
        ReflectionTestUtils.setField(spy, "objectMapper", objectMapper);

        doNothing().when(spy).saveFinalTranscriptionResult(eq(jobId), any(LectureTranscriptionDTO.class));

        spy.processTranscription(t);

        verify(spy).saveFinalTranscriptionResult(eq(jobId), any(LectureTranscriptionDTO.class));
        verify(restClient.get()).uri(eq("/transcribe/status/" + jobId));
    }

    @Test
    void processTranscription_error_marksFailedAndSaves() {
        var t = new LectureTranscription();
        t.setJobId("job-err");

        when(restClient.get().uri(eq("/transcribe/status/job-err")).retrieve().body(any(ParameterizedTypeReference.class))).thenReturn(Map.of("status", "error", "error", "Boom!"));

        service.processTranscription(t);

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        verify(transcriptionRepository).save(t);
    }

    @Test
    void processTranscription_running_noRepositoryInteraction() {
        var t = new LectureTranscription();
        t.setJobId("job-running");

        when(restClient.get().uri(eq("/transcribe/status/job-running")).retrieve().body(any(ParameterizedTypeReference.class))).thenReturn(Map.of("status", "running"));

        service.processTranscription(t);

        verifyNoInteractions(transcriptionRepository);
    }

    @Test
    void saveFinalTranscriptionResult_setsFieldsAndSaves() {
        var jobId = "job-done-1";
        var existing = new LectureTranscription();
        existing.setJobId(jobId);

        when(transcriptionRepository.findByJobId(jobId)).thenReturn(Optional.of(existing));

        var dto = new LectureTranscriptionDTO(null, "en", java.util.List.of());

        service.saveFinalTranscriptionResult(jobId, dto);

        assertThat(existing.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.COMPLETED);
        assertThat(existing.getLanguage()).isEqualTo("en");
        assertThat(existing.getSegments()).isNotNull();
        verify(transcriptionRepository).save(existing);
    }

    @Test
    void markTranscriptionAsFailed_setsStatusAndSaves() {
        var t = new LectureTranscription();
        t.setJobId("job-x");

        service.markTranscriptionAsFailed(t, "nope");

        assertThat(t.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.FAILED);
        verify(transcriptionRepository).save(t);
    }

    @Test
    void createEmptyTranscription_deletesExistingAndCreatesPending() {
        Long lectureId = 9L, unitId = 99L;

        var lecture = new Lecture();
        lecture.setId(lectureId);

        LectureUnit unit = mock(LectureUnit.class, RETURNS_DEEP_STUBS);
        when(unit.getId()).thenReturn(unitId);
        when(unit.getLecture()).thenReturn(lecture);

        var existing = new LectureTranscription();
        existing.setId(777L);

        when(lectureUnitRepository.findByIdElseThrow(unitId)).thenReturn(unit);
        when(transcriptionRepository.findByLectureUnit_Id(unitId)).thenReturn(Optional.of(existing));

        service.createEmptyTranscription(lectureId, unitId, "job-new");

        verify(transcriptionRepository).deleteById(existing.getId());
        verify(transcriptionRepository).flush();

        var captor = org.mockito.ArgumentCaptor.forClass(LectureTranscription.class);
        verify(transcriptionRepository).save(captor.capture());
        var saved = captor.getValue();

        assertThat(saved.getLectureUnit()).isSameAs(unit);
        assertThat(saved.getJobId()).isEqualTo("job-new");
        assertThat(saved.getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    void createEmptyTranscription_wrongLecture_throws() {
        Long lectureId = 1L, unitId = 10L;

        var otherLecture = new Lecture();
        otherLecture.setId(2L);

        LectureUnit unit = mock(LectureUnit.class, RETURNS_DEEP_STUBS);
        when(unit.getId()).thenReturn(unitId);
        when(unit.getLecture()).thenReturn(otherLecture);

        when(lectureUnitRepository.findByIdElseThrow(unitId)).thenReturn(unit);

        assertThatThrownBy(() -> service.createEmptyTranscription(lectureId, unitId, "job-z")).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(transcriptionRepository);
    }
}
