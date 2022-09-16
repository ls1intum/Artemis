package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.VideoUnitRepository;

class VideoUnitIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VideoUnitRepository videoUnitRepository;

    @Autowired
    private LectureRepository lectureRepository;

    private Lecture lecture1;

    private VideoUnit videoUnit;

    @BeforeEach
    void initTestCase() throws Exception {
        this.database.addUsers(1, 1, 0, 1);
        this.lecture1 = this.database.createCourseWithLecture(true);
        this.videoUnit = new VideoUnit();
        this.videoUnit.setDescription("LoremIpsum");
        this.videoUnit.setName("LoremIpsum");
        this.videoUnit.setSource("oHg5SJYRHA0");

        // Add users that are not in the course
        database.createAndSaveUser("student42");
        database.createAndSaveUser("tutor42");
        database.createAndSaveUser("instructor42");
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, HttpStatus.FORBIDDEN);
        request.post("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, HttpStatus.FORBIDDEN);
        request.get("/api/lectures/" + lecture1.getId() + "/video-units/0", HttpStatus.FORBIDDEN, VideoUnit.class);
    }

    @AfterEach
    void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void createVideoUnit_asInstructor_shouldCreateVideoUnit() throws Exception {
        videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        var persistedVideoUnit = request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.CREATED);
        assertThat(persistedVideoUnit.getId()).isNotNull();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void createVideoUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        request.postWithResponseBody("/api/lectures/" + this.lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_asInstructor_shouldUpdateVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
        this.videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.videoUnit.setDescription("Changed");
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.OK);
        assertThat(this.videoUnit.getDescription()).isEqualTo("Changed");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_asInstructor_shouldKeepOrdering() throws Exception {
        persistVideoUnitWithLecture();

        // Add a second lecture unit
        VideoUnit videoUnit = database.createVideoUnit();
        lecture1.addLectureUnit(videoUnit);
        lectureRepository.save(lecture1);

        List<LectureUnit> orderedUnits = lecture1.getLectureUnits();

        // Updating the lecture unit should not change order attribute
        request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", videoUnit, VideoUnit.class, HttpStatus.OK);

        List<LectureUnit> updatedOrderedUnits = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits();
        assertThat(updatedOrderedUnits).containsExactlyElementsOf(orderedUnits);
    }

    private void persistVideoUnitWithLecture() {
        this.videoUnit = videoUnitRepository.save(this.videoUnit);
        lecture1.addLectureUnit(this.videoUnit);
        lecture1 = lectureRepository.save(lecture1);
        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    void updateVideoUnit_InstructorNotInCourse_shouldReturnForbidden() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
        this.videoUnit.setDescription("Changed");
        this.videoUnit.setSource("https://www.youtube.com/embed/8iU8LPEa4o0");
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void updateVideoUnit_noId_shouldReturnBadRequest() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
        this.videoUnit.setId(null);
        this.videoUnit = request.putWithResponseBody("/api/lectures/" + lecture1.getId() + "/video-units", this.videoUnit, VideoUnit.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void getVideoUnit_correctId_shouldReturnVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
        VideoUnit videoUnitFromRequest = request.get("/api/lectures/" + lecture1.getId() + "/video-units/" + this.videoUnit.getId(), HttpStatus.OK, VideoUnit.class);
        assertThat(this.videoUnit.getId()).isEqualTo(videoUnitFromRequest.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    void deleteVideoUnit_correctId_shouldDeleteVideoUnit() throws Exception {
        persistVideoUnitWithLecture();

        this.videoUnit = (VideoUnit) lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get().getLectureUnits().stream().findFirst().get();
        assertThat(this.videoUnit.getId()).isNotNull();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + this.videoUnit.getId(), HttpStatus.OK);
        request.get("/api/lectures/" + lecture1.getId() + "/video-units/" + this.videoUnit.getId(), HttpStatus.NOT_FOUND, VideoUnit.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
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
