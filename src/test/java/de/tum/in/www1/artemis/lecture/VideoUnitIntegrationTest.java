package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationIndependentTest;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.VideoUnitRepository;
import de.tum.in.www1.artemis.user.UserUtilService;

class VideoUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "videounitintegrationtest";

    @Autowired
    private VideoUnitRepository videoUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    private Lecture lecture1;

    private VideoUnit videoUnit;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 1);
        this.lecture1 = lectureUtilService.createCourseWithLecture(true);
        this.videoUnit = new VideoUnit();
        this.videoUnit.setDescription("LoremIpsum");
        this.videoUnit.setName("LoremIpsum");
        this.videoUnit.setSource("oHg5SJYRHA0");

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/video-units/0", HttpStatus.FORBIDDEN, VideoUnit.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createVideoUnit_asInstructor_shouldCreateVideoUnit() throws Exception {
        videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        var persistedVideoUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.CREATED);
        assertThat(persistedVideoUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createVideoUnit_withId_shouldReturnBadRequest() throws Exception {
        videoUnit.setId(99L);
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createVideoUnit_invalidUrl_shouldReturnBadRequest() throws Exception {
        videoUnit.setSource("abc123");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void createVideoUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_asInstructor_shouldUpdateVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        this.videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.videoUnit.setDescription("Changed");
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.OK);
        assertThat(this.videoUnit.getDescription()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_withoutLecture_shouldReturnBadRequest() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        this.videoUnit.setLecture(null);
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistVideoUnitWithLecture();

        // Add a second lecture unit
        VideoUnit videoUnit = lectureUtilService.createVideoUnit();
        lecture1.addLectureUnit(videoUnit);
        lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistVideoUnitWithLecture() {
        this.videoUnit = videoUnitRepository.save(this.videoUnit);
        lecture1.addLectureUnit(this.videoUnit);
        lecture1 = lectureRepository.save(lecture1);
        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateVideoUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        this.videoUnit.setDescription("Changed");
        this.videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_noId_shouldReturnBadRequest() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        this.videoUnit.setId(null);
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getVideoUnit_correctId_shouldReturnVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        VideoUnit videoUnitFromRequest = request.get("/api/lectures/" + lecture1.getId() + "/video-units/" + this.videoUnit.getId(), HttpStatus.OK, VideoUnit.class);
        assertThat(this.videoUnit.getId()).isEqualTo(videoUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteVideoUnit_correctId_shouldDeleteVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow().getLectureUnits().stream().findFirst().orElseThrow();
        assertThat(this.videoUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.videoUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/video-units/" + this.videoUnit.getId(), HttpStatus.NOT_FOUND, VideoUnit.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void removeLeadingAndTrailingWhitespaces() throws Exception {
        String source = "     https://www.youtube.com/embed/8iU8LPEa4o0     ";
        videoUnit.setSource(source);
        var persistedVideoUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.CREATED);
        assertThat(persistedVideoUnit.getId()).isNotNull();
        assertThat(persistedVideoUnit.getSource()).isEqualTo(source.strip());

        persistedVideoUnit.setSource(source);
        var updatedVideoUnit = request.putWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", persistedVideoUnit, VideoUnit.class, HttpStatus.OK);
        assertThat(updatedVideoUnit.getId()).isNotNull();
        assertThat(updatedVideoUnit.getSource()).isEqualTo(source.strip());
    }
}
