package de.tum.in.www1.artemis;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.FileUploadExercise;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;

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
        Lecture lecture = database.createCourseWithLecture();
        lectureRepo.save(lecture);
        Lecture savedLecture = lectureRepo.findAll().get(0);

        Lecture receivedLecture = request.get("/api/lectures/" + lecture.getId(), HttpStatus.OK, Lecture.class);

        assertThat(savedLecture.getId()).isEqualTo(receivedLecture.getId());
        assertThat(savedLecture).isEqualTo(receivedLecture);

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLectureForCourseWithPermission() throws Exception {

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLectureWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture();
        Lecture receivedLecture = request.postWithResponseBody("/api/lectures", lecture, Lecture.class);

        assertThat(receivedLecture).isNotNull();
        assertThat(receivedLecture.getId()).isNotNull();
        assertThat(receivedLecture.getDescription()).isEqualTo("Test Lecture");

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLectureWithPermission() throws Exception {

    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getDeleteWithPermission() throws Exception {
        Lecture lecture = database.createCourseWithLecture();
        lectureRepo.save(lecture);
        request.delete("/api/lectures/" + lecture.getId(), HttpStatus.OK);
        assertThat(lectureRepo.findAll().isEmpty()).isTrue();

    }

}
