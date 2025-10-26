package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.connector.NebulaRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.VideoLecture;
import de.tum.cit.aet.artemis.lecture.dto.LectureVideoDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.VideoLectureRepository;
import de.tum.cit.aet.artemis.nebula.AbstractNebulaIntegrationTest;

/**
 * Integration tests for the LectureVideoResource REST controller.
 */
class LectureVideoResourceIntegrationTest extends AbstractNebulaIntegrationTest {

    private static final String TEST_PREFIX = "lecturevideoapi";

    @Autowired
    private VideoLectureRepository videoLectureRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private NebulaRequestMockProvider nebulaRequestMockProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private Lecture testLecture;

    private Course testCourse;

    @BeforeEach
    void setUp() {
        nebulaRequestMockProvider.enableMockingOfRequests();

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        testCourse = courseUtilService.createCourse();
        testLecture = lectureUtilService.createLecture(testCourse);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUploadLectureVideo_Success() throws Exception {
        // Arrange
        String videoId = "test-video-123";
        String filename = "lecture-video.mp4";
        String playlistUrl = "/video-storage/playlist/" + videoId + "/playlist.m3u8";
        double duration = 1800.5;
        long sizeBytes = 500 * 1024 * 1024L; // 500 MB

        MockMultipartFile videoFile = new MockMultipartFile("file", filename, "video/mp4", new byte[1024]);

        nebulaRequestMockProvider.mockSuccessfulVideoUpload(videoId, filename, playlistUrl, duration, sizeBytes, org.springframework.test.web.client.ExpectedCount.once());

        // Act
        MvcResult result = request
                .performMvcRequest(multipart("/api/lecture/lectures/" + testLecture.getId() + "/video").file(videoFile).contentType(MediaType.MULTIPART_FORM_DATA))
                .andExpect(status().isCreated()).andReturn();

        // Assert
        LectureVideoDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), LectureVideoDTO.class);
        assertThat(response).isNotNull();
        assertThat(response.videoId()).isEqualTo(videoId);
        assertThat(response.filename()).isEqualTo(filename);
        assertThat(response.durationSeconds()).isEqualTo(duration);
        assertThat(response.sizeBytes()).isEqualTo(sizeBytes);
        assertThat(response.playlistUrl()).isEqualTo(playlistUrl);

        // Verify database
        VideoLecture savedVideo = videoLectureRepository.findByLectureId(testLecture.getId()).orElse(null);
        assertThat(savedVideo).isNotNull();
        assertThat(savedVideo.getVideoId()).isEqualTo(videoId);

        nebulaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUploadLectureVideo_FileRequired() throws Exception {
        // Act & Assert - No file provided
        request.performMvcRequest(multipart("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUploadLectureVideo_AlreadyExists() throws Exception {
        // Arrange - Create existing video
        VideoLecture existingVideo = new VideoLecture();
        existingVideo.setLecture(testLecture);
        existingVideo.setVideoId("existing-video-id");
        existingVideo.setFilename("existing.mp4");
        existingVideo.setPlaylistUrl("/playlist/existing/playlist.m3u8");
        videoLectureRepository.save(existingVideo);

        MockMultipartFile videoFile = new MockMultipartFile("file", "new-video.mp4", "video/mp4", new byte[1024]);

        // Act & Assert
        request.performMvcRequest(multipart("/api/lecture/lectures/" + testLecture.getId() + "/video").file(videoFile)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testUploadLectureVideo_ForbiddenForStudent() throws Exception {
        // Arrange
        MockMultipartFile videoFile = new MockMultipartFile("file", "video.mp4", "video/mp4", new byte[1024]);

        // Act & Assert
        request.performMvcRequest(multipart("/api/lecture/lectures/" + testLecture.getId() + "/video").file(videoFile)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetLectureVideo_Success() throws Exception {
        // Arrange
        String videoId = "test-video-get";
        VideoLecture video = new VideoLecture();
        video.setLecture(testLecture);
        video.setVideoId(videoId);
        video.setFilename("lecture.mp4");
        video.setPlaylistUrl("/video-storage/playlist/" + videoId + "/playlist.m3u8");
        video.setDurationSeconds(3600.0);
        video.setSizeBytes(1024 * 1024 * 1024L);
        videoLectureRepository.save(video);

        // Act
        MvcResult result = request.performMvcRequest(get("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isOk()).andReturn();

        // Assert
        LectureVideoDTO response = objectMapper.readValue(result.getResponse().getContentAsString(), LectureVideoDTO.class);
        assertThat(response).isNotNull();
        assertThat(response.videoId()).isEqualTo(videoId);
        assertThat(response.filename()).isEqualTo("lecture.mp4");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetLectureVideo_NotFound() throws Exception {
        // Act & Assert
        request.performMvcRequest(get("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testGetVideoStreamUrl_Success() throws Exception {
        // Arrange
        String videoId = "test-stream-video";
        VideoLecture video = new VideoLecture();
        video.setLecture(testLecture);
        video.setVideoId(videoId);
        video.setFilename("stream.mp4");
        video.setPlaylistUrl("/video-storage/playlist/" + videoId + "/playlist.m3u8");
        videoLectureRepository.save(video);

        // Act
        MvcResult result = request.performMvcRequest(get("/api/lecture/lectures/" + testLecture.getId() + "/video/stream-url")).andExpect(status().isOk()).andReturn();

        // Assert
        String streamUrl = result.getResponse().getContentAsString();
        assertThat(streamUrl).contains(videoId);
        assertThat(streamUrl).contains("playlist.m3u8");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLectureVideo_Success() throws Exception {
        // Arrange
        String videoId = "test-delete-video";
        VideoLecture video = new VideoLecture();
        video.setLecture(testLecture);
        video.setVideoId(videoId);
        video.setFilename("delete-me.mp4");
        video.setPlaylistUrl("/video-storage/playlist/" + videoId + "/playlist.m3u8");
        videoLectureRepository.save(video);

        nebulaRequestMockProvider.mockSuccessfulVideoDelete(videoId, org.springframework.test.web.client.ExpectedCount.once());

        // Act
        request.performMvcRequest(delete("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isNoContent());

        // Assert
        assertThat(videoLectureRepository.findByLectureId(testLecture.getId())).isEmpty();

        nebulaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeleteLectureVideo_ForbiddenForEditor() throws Exception {
        // Arrange
        VideoLecture video = new VideoLecture();
        video.setLecture(testLecture);
        video.setVideoId("video-id");
        video.setFilename("video.mp4");
        video.setPlaylistUrl("/playlist/video-id/playlist.m3u8");
        videoLectureRepository.save(video);

        // Act & Assert
        request.performMvcRequest(delete("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteLectureVideo_NotFound() throws Exception {
        // Act & Assert
        request.performMvcRequest(delete("/api/lecture/lectures/" + testLecture.getId() + "/video")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUploadLectureVideo_InvalidFileType() throws Exception {
        // Arrange
        MockMultipartFile textFile = new MockMultipartFile("file", "document.txt", "text/plain", new byte[1024]);

        nebulaRequestMockProvider.mockFailedVideoUpload(HttpStatus.BAD_REQUEST, org.springframework.test.web.client.ExpectedCount.once());

        // Act & Assert
        request.performMvcRequest(multipart("/api/lecture/lectures/" + testLecture.getId() + "/video").file(textFile)).andExpect(status().isBadRequest());
    }
}
