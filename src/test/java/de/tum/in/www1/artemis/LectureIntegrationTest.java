package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.TextExercise;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.TextUnit;
import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class LectureIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    LectureRepository lectureRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    TextExerciseRepository textExerciseRepository;

    @Autowired
    AttachmentUnitRepository attachmentUnitRepository;

    @Autowired
    AttachmentRepository attachmentRepository;

    @Autowired
    ExerciseUnitRepository exerciseUnitRepository;

    @Autowired
    TextUnitRepository textUnitRepository;

    @Autowired
    VideoUnitRepository videoUnitRepository;

    Attachment attachmentDirectOfLecture;

    Attachment attachmentOfAttachmentUnit;

    TextExercise textExercise;

    Course course1;

    Lecture lecture1;

    TextUnit textUnit;

    ExerciseUnit exerciseUnit;

    VideoUnit videoUnit;

    AttachmentUnit attachmentUnit;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(10, 10, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        this.course1 = this.courseRepository.findWithEagerExercisesAndLecturesById(courses.get(0).getId());
        this.lecture1 = this.course1.getLectures().stream().findFirst().get();
        this.textExercise = textExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        // Setting up a lecture with various kinds of content
        this.exerciseUnit = database.createExerciseUnit(textExercise);
        this.attachmentUnit = database.createAttachmentUnit();
        this.attachmentOfAttachmentUnit = attachmentUnit.getAttachment();
        this.videoUnit = database.createVideoUnit();
        this.textUnit = database.createTextUnit();
        addAttachmentToLecture();

        this.lecture1 = database.addLectureUnitsToLecture(this.lecture1, Set.of(this.exerciseUnit, this.attachmentUnit, this.videoUnit, this.textUnit));
    }

    private void addAttachmentToLecture() {
        this.attachmentDirectOfLecture = new Attachment().attachmentType(AttachmentType.FILE).link("files/temp/example2.txt").name("example2");
        this.attachmentDirectOfLecture.setLecture(this.lecture1);
        this.attachmentDirectOfLecture = attachmentRepository.save(this.attachmentDirectOfLecture);
        this.lecture1.addAttachments(this.attachmentDirectOfLecture);
        this.lecture1 = lectureRepository.save(this.lecture1);
    }

    private void testAllPreAuthorize() throws Exception {
        request.postWithResponseBody("/api/lectures", new Lecture(), Lecture.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody("/api/lectures", new Lecture(), Lecture.class, HttpStatus.FORBIDDEN);
        request.getList("/api/courses/" + course1.getId() + "/lectures", HttpStatus.FORBIDDEN, Lecture.class);
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @AfterEach
    public void resetDatabase() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLecture_correctRequestBody_shouldCreateLecture() throws Exception {
        Lecture lecture = new Lecture();
        lecture.title("loremIpsum");
        lecture.setCourse(this.course1);
        lecture.setDescription("loremIpsum");
        Lecture returnedLecture = request.postWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.CREATED);
        assertThat(returnedLecture).isNotNull();
        assertThat(returnedLecture.getId()).isNotNull();
        assertThat(returnedLecture.getTitle()).isEqualTo(lecture.getTitle());
        assertThat(returnedLecture.getCourse().getId()).isEqualTo(lecture.getCourse().getId());
        assertThat(returnedLecture.getDescription()).isEqualTo(lecture.getDescription());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createLecture_alreadyId_shouldReturnBadRequest() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setId(1L);
        request.postWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLecture_correctRequestBody_shouldUpdateLecture() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lecture1.getId()).get();
        originalLecture.setTitle("Updated");
        originalLecture.setDescription("Updated");
        Lecture updatedLecture = request.putWithResponseBody("/api/lectures", originalLecture, Lecture.class, HttpStatus.OK);
        assertThat(updatedLecture.getTitle()).isEqualTo("Updated");
        assertThat(updatedLecture.getDescription()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLecture_NoId_shouldReturnBadRequest() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithStudentQuestionsAndLectureUnitsAndLearningGoals(lecture1.getId()).get();
        originalLecture.setId(null);
        request.putWithResponseBody("/api/lectures", originalLecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLectureForCourse_withOutLectureUnits_shouldGetLecturesWithOutLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/courses/" + course1.getId() + "/lectures", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures.size()).isEqualTo(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().get();
        assertThat(lecture.getLectureUnits().size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void getLectureForCourse_withLectureUnits_shouldGetLecturesWithLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/courses/" + course1.getId() + "/lectures?withLectureUnits=true", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures.size()).isEqualTo(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().get();
        assertThat(lecture.getLectureUnits().size()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "student42", roles = "USER")
    public void getLecture_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN, Lecture.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_ExerciseAndAttachmentReleased_shouldGetLectureWithAllLectureUnits() throws Exception {
        Lecture receivedLecture = request.get("/api/lectures/" + lecture1.getId(), HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLecture.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLecture.getAttachments().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_ExerciseNotReleased_shouldGetLectureWithoutExerciseUnit() throws Exception {
        TextExercise exercise = textExerciseRepository.findByIdElseThrow(textExercise.getId());
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(10));
        textExerciseRepository.saveAndFlush(exercise);

        Lecture receivedLecture = request.get("/api/lectures/" + lecture1.getId(), HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLecture.getLectureUnits().size()).isEqualTo(3);
        assertThat(receivedLecture.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).collect(Collectors.toList())).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser("tutor1");
        receivedLecture = request.get("/api/lectures/" + lecture1.getId(), HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLecture.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLecture.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).collect(Collectors.toList())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_AttachmentNotReleased_shouldGetLectureWithoutAttachmentUnitAndAttachment() throws Exception {
        Attachment unitAttachment = attachmentRepository.findById(attachmentOfAttachmentUnit.getId()).get();
        unitAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        Attachment lectureAttachment = attachmentRepository.findById(attachmentDirectOfLecture.getId()).get();
        lectureAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        attachmentRepository.saveAll(Set.of(unitAttachment, lectureAttachment));

        Lecture receivedLecture = request.get("/api/lectures/" + lecture1.getId(), HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLecture.getAttachments().stream().filter(attachment -> attachment.getId().equals(lectureAttachment.getId())).findFirst().isEmpty()).isTrue();
        assertThat(receivedLecture.getLectureUnits().size()).isEqualTo(3);
        assertThat(receivedLecture.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).collect(Collectors.toList())).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser("tutor1");
        receivedLecture = request.get("/api/lectures/" + lecture1.getId(), HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLecture.getAttachments().stream().anyMatch(attachment -> attachment.getId().equals(lectureAttachment.getId()))).isTrue();
        assertThat(receivedLecture.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLecture.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).collect(Collectors.toList())).isNotEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_lectureExists_shouldDeleteLecture() throws Exception {
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional.isEmpty()).isTrue();
    }

    @Test
    @WithMockUser(username = "instructor42", roles = "INSTRUCTOR")
    public void deleteLecture_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_lectureDoesNot_shouldReturnNotFound() throws Exception {
        request.delete("/api/lectures/" + 0, HttpStatus.NOT_FOUND);
    }

}
