package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.connector.NebulaRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.nebula.AbstractNebulaIntegrationTest;
import de.tum.cit.aet.artemis.nebula.domain.VideoLecture;
import de.tum.cit.aet.artemis.nebula.dto.LectureVideoDTO;
import de.tum.cit.aet.artemis.nebula.repository.VideoLectureRepository;
import de.tum.cit.aet.artemis.nebula.service.NebulaVideoService;

/**
 * Integration tests for the Nebula video storage functionality.
 */
class NebulaVideoServiceIntegrationTest extends AbstractNebulaIntegrationTest {

    private static final String TEST_PREFIX = "nebulavideo";

    @Autowired
    private NebulaVideoService nebulaVideoService;

    @Autowired
    private VideoLectureRepository videoLectureRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private NebulaRequestMockProvider nebulaRequestMockProvider;

    private Lecture testLecture;

    @BeforeEach
    void setUp() {
        nebulaRequestMockProvider.enableMockingOfRequests();

        // Create a test course and lecture
        Course course = courseUtilService.createCourse();
        testLecture = lectureUtilService.createLecture(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUploadVideo_Success() {
        // Arrange
        String videoId = "test-video-id-123";
        String filename = "test-lecture.mp4";
        String playlistUrl = "/video-storage/playlist/" + videoId + "/playlist.m3u8";
        double duration = 3600.5;
        long sizeBytes = 1024 * 1024 * 500L; // 500 MB

        MockMultipartFile videoFile = new MockMultipartFile("file", filename, "video/mp4", new byte[1024]);

        nebulaRequestMockProvider.mockSuccessfulVideoUpload(videoId, filename, playlistUrl, duration, sizeBytes, once());

        // Act
        LectureVideoDTO result = nebulaVideoService.uploadLectureVideo(testLecture, videoFile);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.videoId()).isEqualTo(videoId);
        assertThat(result.filename()).isEqualTo(filename);
        assertThat(result.durationSeconds()).isEqualTo(duration);
        assertThat(result.sizeBytes()).isEqualTo(sizeBytes);

        // Verify database entry
        VideoLecture savedVideo = videoLectureRepository.findByLectureId(testLecture.getId()).orElse(null);
        assertThat(savedVideo).isNotNull();
        assertThat(savedVideo.getVideoId()).isEqualTo(videoId);
        assertThat(savedVideo.getFilename()).isEqualTo(filename);

        nebulaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testUploadVideo_AlreadyHasVideo() {
        // Arrange: Create existing video
        VideoLecture existingVideo = new VideoLecture();
        existingVideo.setLecture(testLecture);
        existingVideo.setVideoId("existing-video-id");
        existingVideo.setFilename("existing.mp4");
        existingVideo.setPlaylistUrl("/playlist/existing/playlist.m3u8");
        videoLectureRepository.save(existingVideo);

        MockMultipartFile newVideoFile = new MockMultipartFile("file", "new-video.mp4", "video/mp4", new byte[1024]);

        // Act & Assert
        assertThatThrownBy(() -> nebulaVideoService.uploadLectureVideo(testLecture, newVideoFile)).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testDeleteVideo_Success() {
        // Arrange: Create video to delete
        String videoId = "test-delete-video-id";
        VideoLecture videoToDelete = new VideoLecture();
        videoToDelete.setLecture(testLecture);
        videoToDelete.setVideoId(videoId);
        videoToDelete.setFilename("delete-me.mp4");
        videoToDelete.setPlaylistUrl("/playlist/" + videoId + "/playlist.m3u8");
        videoLectureRepository.save(videoToDelete);

        nebulaRequestMockProvider.mockSuccessfulVideoDelete(videoId, once());

        // Act
        nebulaVideoService.deleteLectureVideo(testLecture.getId());

        // Assert
        assertThat(videoLectureRepository.findByLectureId(testLecture.getId())).isEmpty();

        nebulaRequestMockProvider.verify();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor", roles = "INSTRUCTOR")
    void testDeleteVideo_NotFound() {
        // Act & Assert
        assertThatThrownBy(() -> nebulaVideoService.deleteLectureVideo(testLecture.getId())).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "STUDENT")
    void testGetVideoStreamingUrl_Success() {
        // Arrange
        String videoId = "test-stream-video-id";
        VideoLecture video = new VideoLecture();
        video.setLecture(testLecture);
        video.setVideoId(videoId);
        video.setFilename("stream-me.mp4");
        video.setPlaylistUrl("/video-storage/playlist/" + videoId + "/playlist.m3u8");
        videoLectureRepository.save(video);

        // Act
        String streamUrl = nebulaVideoService.getVideoStreamingUrl(testLecture.getId());

        // Assert
        assertThat(streamUrl).contains(videoId);
        assertThat(streamUrl).contains("playlist.m3u8");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student", roles = "STUDENT")
    void testGetVideoStreamingUrl_NotFound() {
        // Act & Assert
        assertThatThrownBy(() -> nebulaVideoService.getVideoStreamingUrl(testLecture.getId())).isInstanceOf(ResponseStatusException.class)
                .extracting(ex -> ((ResponseStatusException) ex).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
