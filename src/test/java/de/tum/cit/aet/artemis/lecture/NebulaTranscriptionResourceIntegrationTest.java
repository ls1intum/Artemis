package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionInitResponseDTO;
import de.tum.cit.aet.artemis.lecture.dto.NebulaTranscriptionRequestDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class NebulaTranscriptionResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "nebulatranscriptionresource";

    @Autowired
    private MockMvc restNebulaTranscriptionMockMvc;

    @Autowired
    private LectureTranscriptionRepository lectureTranscriptionRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private UserTestRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Course course;

    private Lecture lecture;

    private AttachmentVideoUnit lectureUnit;

    private User instructor;

    @BeforeEach
    void initTestCase() {
        lectureTranscriptionRepository.deleteAll();

        // Create test data
        course = courseUtilService.createCourse();
        instructor = userUtilService.createAndSaveUser(TEST_PREFIX + "instructor");
        instructor.setGroups(Set.of(course.getInstructorGroupName()));
        userRepository.save(instructor);

        lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lecture.setCourse(course);
        lecture = lectureRepository.save(lecture);

        lectureUnit = new AttachmentVideoUnit();
        lectureUnit.setName("Test Video Unit");
        lectureUnit.setLecture(lecture);
        lecture.getLectureUnits().add(lectureUnit);
        lecture = lectureRepository.saveAndFlush(lecture);
        lectureUnit = (AttachmentVideoUnit) lecture.getLectureUnits().get(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void startNebulaTranscription_success() throws Exception {
        // Mock Nebula API response
        var nebulaResponse = new NebulaTranscriptionInitResponseDTO("test-job-id-123", "started");
        when(nebulaRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(NebulaTranscriptionInitResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(nebulaResponse, HttpStatus.OK));

        // Create request
        var request = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", lecture.getId(), lectureUnit.getId());

        // Perform POST request
        restNebulaTranscriptionMockMvc.perform(post("/api/lecture/nebula/{lectureId}/lecture-unit/{lectureUnitId}/transcriber", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());

        // Verify transcription was created with PENDING status
        var transcriptions = lectureTranscriptionRepository.findAll();
        assertThat(transcriptions).hasSize(1);
        assertThat(transcriptions.get(0).getJobId()).isEqualTo("test-job-id-123");
        assertThat(transcriptions.get(0).getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
        assertThat(transcriptions.get(0).getLectureUnit().getId()).isEqualTo(lectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void startNebulaTranscription_replacesExisting() throws Exception {
        // Create existing transcription
        var existingTranscription = new de.tum.cit.aet.artemis.lecture.domain.LectureTranscription();
        existingTranscription.setLectureUnit(lectureUnit);
        existingTranscription.setJobId("old-job-id");
        existingTranscription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
        lectureTranscriptionRepository.save(existingTranscription);

        // Mock Nebula API response
        var nebulaResponse = new NebulaTranscriptionInitResponseDTO("new-job-id-456", "started");
        when(nebulaRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(NebulaTranscriptionInitResponseDTO.class)))
                .thenReturn(new ResponseEntity<>(nebulaResponse, HttpStatus.OK));

        var request = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", lecture.getId(), lectureUnit.getId());

        // Perform POST request
        restNebulaTranscriptionMockMvc.perform(post("/api/lecture/nebula/{lectureId}/lecture-unit/{lectureUnitId}/transcriber", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isOk());

        // Verify old transcription was replaced
        var transcriptions = lectureTranscriptionRepository.findAll();
        assertThat(transcriptions).hasSize(1);
        assertThat(transcriptions.get(0).getJobId()).isEqualTo("new-job-id-456");
        assertThat(transcriptions.get(0).getTranscriptionStatus()).isEqualTo(TranscriptionStatus.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void startNebulaTranscription_nebulaServiceError() throws Exception {
        // Mock Nebula API to throw exception
        when(nebulaRestTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(NebulaTranscriptionInitResponseDTO.class)))
                .thenThrow(new RestClientException("Nebula service unavailable"));

        var request = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", lecture.getId(), lectureUnit.getId());

        // Should return 500 when Nebula service fails
        restNebulaTranscriptionMockMvc.perform(post("/api/lecture/nebula/{lectureId}/lecture-unit/{lectureUnitId}/transcriber", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isInternalServerError());

        // Verify no transcription was created
        assertThat(lectureTranscriptionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void startNebulaTranscription_asStudent_forbidden() throws Exception {
        var request = new NebulaTranscriptionRequestDTO("https://example.com/video.mp4", lecture.getId(), lectureUnit.getId());

        // Students should not be able to start transcriptions
        restNebulaTranscriptionMockMvc.perform(post("/api/lecture/nebula/{lectureId}/lecture-unit/{lectureUnitId}/transcriber", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getTumLivePlaylist_notTumLiveUrl_notFound() throws Exception {
        // Mock TUM Live service to return empty for non-TUM-Live URLs
        when(tumLiveService.getTumLivePlaylistLink(anyString())).thenReturn(java.util.Optional.empty());

        // Non-TUM-Live URLs should return 404
        restNebulaTranscriptionMockMvc.perform(get("/api/lecture/nebula/video-utils/tum-live-playlist").param("url", "https://youtube.com/watch?v=123"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getTumLivePlaylist_invalidUrl_notFound() throws Exception {
        // Mock TUM Live service to return empty for invalid URL formats
        when(tumLiveService.getTumLivePlaylistLink(anyString())).thenReturn(java.util.Optional.empty());

        // Invalid TUM Live URL format should return 404
        restNebulaTranscriptionMockMvc.perform(get("/api/lecture/nebula/video-utils/tum-live-playlist").param("url", "https://tum.live/invalid-format"))
                .andExpect(status().isNotFound());
    }
}
