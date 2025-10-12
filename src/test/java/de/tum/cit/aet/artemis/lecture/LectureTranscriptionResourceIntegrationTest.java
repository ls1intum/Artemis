package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscriptionSegment;
import de.tum.cit.aet.artemis.lecture.domain.TranscriptionStatus;
import de.tum.cit.aet.artemis.lecture.dto.LectureTranscriptionDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureTranscriptionRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureTranscriptionResourceIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lecturetranscriptionresource";

    @Autowired
    private MockMvc restLectureTranscriptionMockMvc;

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
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createLectureTranscription_success() throws Exception {
        // Create transcription DTO
        var segments = List.of(new LectureTranscriptionSegment(0.0, 5.0, "Hello world", 1), new LectureTranscriptionSegment(5.0, 10.0, "This is a test", 1));
        var transcriptionDTO = new LectureTranscriptionDTO(lectureUnit.getId(), "en", segments);

        // Perform POST request
        restLectureTranscriptionMockMvc
                .perform(post("/api/lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcription", lecture.getId(), lectureUnit.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transcriptionDTO)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.language").value("en")).andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments[0].startTime").value(0.0)).andExpect(jsonPath("$.segments[0].endTime").value(5.0))
                .andExpect(jsonPath("$.segments[0].text").value("Hello world"));

        // Verify transcription was saved
        var savedTranscriptions = lectureTranscriptionRepository.findAll();
        assertThat(savedTranscriptions).hasSize(1);
        assertThat(savedTranscriptions.get(0).getLanguage()).isEqualTo("en");
        assertThat(savedTranscriptions.get(0).getSegments()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createLectureTranscription_wrongLecture_badRequest() throws Exception {
        // Create another lecture
        var otherLecture = new Lecture();
        otherLecture.setTitle("Other Lecture");
        otherLecture.setCourse(course);
        otherLecture = lectureRepository.save(otherLecture);

        var transcriptionDTO = new LectureTranscriptionDTO(lectureUnit.getId(), "en", List.of());

        // Try to create transcription with wrong lecture ID
        restLectureTranscriptionMockMvc.perform(post("/api/lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcription", otherLecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(transcriptionDTO))).andExpect(status().isBadRequest());

        // Verify no transcription was saved
        assertThat(lectureTranscriptionRepository.findAll()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void createLectureTranscription_replacesExisting() throws Exception {
        // Create existing transcription
        var existingTranscription = new LectureTranscription("de", List.of(), lectureUnit);
        existingTranscription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
        lectureTranscriptionRepository.save(existingTranscription);

        // Create new transcription DTO
        var segments = List.of(new LectureTranscriptionSegment(0.0, 5.0, "New content", 1));
        var transcriptionDTO = new LectureTranscriptionDTO(lectureUnit.getId(), "en", segments);

        // Perform POST request
        restLectureTranscriptionMockMvc.perform(post("/api/lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcription", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(transcriptionDTO))).andExpect(status().isCreated())
                .andExpect(jsonPath("$.language").value("en"));

        // Verify old transcription was replaced
        var savedTranscriptions = lectureTranscriptionRepository.findAll();
        assertThat(savedTranscriptions).hasSize(1);
        assertThat(savedTranscriptions.get(0).getLanguage()).isEqualTo("en");
        assertThat(savedTranscriptions.get(0).getSegments().get(0).text()).isEqualTo("New content");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTranscript_success() throws Exception {
        // Create transcription
        var segments = List.of(new LectureTranscriptionSegment(0.0, 5.0, "Hello world", 1), new LectureTranscriptionSegment(5.0, 10.0, "This is a test", 1));
        var transcription = new LectureTranscription("en", segments, lectureUnit);
        transcription.setTranscriptionStatus(TranscriptionStatus.COMPLETED);
        lectureTranscriptionRepository.save(transcription);

        // Perform GET request
        restLectureTranscriptionMockMvc.perform(get("/api/lecture/lecture-unit/{lectureUnitId}/transcript", lectureUnit.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.lectureUnitId").value(lectureUnit.getId())).andExpect(jsonPath("$.language").value("en")).andExpect(jsonPath("$.segments").isArray())
                .andExpect(jsonPath("$.segments[0].startTime").value(0.0)).andExpect(jsonPath("$.segments[0].endTime").value(5.0))
                .andExpect(jsonPath("$.segments[0].text").value("Hello world")).andExpect(jsonPath("$.segments[1].startTime").value(5.0))
                .andExpect(jsonPath("$.segments[1].endTime").value(10.0)).andExpect(jsonPath("$.segments[1].text").value("This is a test"));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void getTranscript_notFound() throws Exception {
        // Perform GET request for non-existent transcription
        restLectureTranscriptionMockMvc.perform(get("/api/lecture/lecture-unit/{lectureUnitId}/transcript", lectureUnit.getId())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void createLectureTranscription_asStudent_forbidden() throws Exception {
        var transcriptionDTO = new LectureTranscriptionDTO(lectureUnit.getId(), "en", List.of());

        // Students should not be able to create transcriptions
        restLectureTranscriptionMockMvc.perform(post("/api/lecture/{lectureId}/lecture-unit/{lectureUnitId}/transcription", lecture.getId(), lectureUnit.getId())
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(transcriptionDTO))).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "USER")
    void getTranscript_asStudent_forbidden() throws Exception {
        // Students should not be able to access transcripts without proper permissions
        restLectureTranscriptionMockMvc.perform(get("/api/lecture/lecture-unit/{lectureUnitId}/transcript", lectureUnit.getId())).andExpect(status().isForbidden());
    }
}
