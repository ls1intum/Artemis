package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;

public class LectureIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

    @Autowired
    LectureRepository lectureRepo;

    @BeforeEach
    public void initTestCase() {
        database.addUsers(1, 1, 1);

    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLectureWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        Lecture savedLecture = lectureRepo.findAll().get(0);

        Lecture receivedLecture = request.get("/api/lectures/" + lecture.getId(), HttpStatus.OK, Lecture.class);

        assertThat(savedLecture.getId()).isEqualTo(receivedLecture.getId());
        assertThat(savedLecture).isEqualTo(receivedLecture);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLectureForCourseWithPermission() throws Exception {
        database.createCourseWithLecture(true);
        List<Lecture> savedLecture = lectureRepo.findAll();
        Course course = savedLecture.get(0).getCourse();
        List<Lecture> receivedLecture = request.getList("/api/courses/" + course.getId() + "/lectures", HttpStatus.OK, Lecture.class);
        assertThat(savedLecture.get(0).getId()).isEqualTo(receivedLecture.get(0).getId());
        assertThat(savedLecture).isEqualTo(receivedLecture);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void getLectureForCourseNoPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        Course course = lectureRepo.findAll().get(0).getCourse();
        request.getList("/api/courses/" + course.getId() + "/lectures", HttpStatus.FORBIDDEN, Lecture.class);
    }

    @Test
    @WithMockUser(roles = "USER")
    public void createLectureNoPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(false);
        request.postWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLectureWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(false);
        Lecture receivedLecture = request.postWithResponseBody("/api/lectures", lecture, Lecture.class);

        assertThat(receivedLecture).isNotNull();
        assertThat(receivedLecture.getId()).isNotNull();
        assertThat(receivedLecture.getDescription()).isEqualTo("Test Lecture");

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        lecture.setDescription("UPDATE");
        Lecture receivedLecture = request.putWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.OK);
        assertThat(receivedLecture.getDescription()).isEqualTo("UPDATE");

    }

    @Test
    @WithMockUser(roles = "USER")
    public void updateLectureNoPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.putWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.OK);
        assertThat(lectureRepo.findAll().isEmpty()).isTrue();

    }

    @Test
    @WithMockUser(roles = "USER")
    public void deleteLectureNoPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(roles = "TA")
    public void deleteLectureNoPermissionAsTutor() throws Exception {
        Lecture lecture = database.createCourseWithLecture(true);
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.FORBIDDEN);

    }

}
