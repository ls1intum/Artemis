package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

public class LectureIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private Attachment attachmentDirectOfLecture;

    private Attachment attachmentOfAttachmentUnit;

    private TextExercise textExercise;

    private Course course1;

    private Lecture lecture1;

    @BeforeEach
    public void initTestCase() throws Exception {
        this.database.addUsers(10, 10, 0, 10);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(true);
        this.course1 = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        this.lecture1 = this.course1.getLectures().stream().findFirst().get();
        this.textExercise = textExerciseRepository.findByCourseId(course1.getId()).stream().findFirst().get();
        // Add users that are not in the course
        userRepository.save(ModelFactory.generateActivatedUser("student42"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor42"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor42"));

        // Setting up a lecture with various kinds of content
        ExerciseUnit exerciseUnit = database.createExerciseUnit(textExercise);
        AttachmentUnit attachmentUnit = database.createAttachmentUnit();
        this.attachmentOfAttachmentUnit = attachmentUnit.getAttachment();
        VideoUnit videoUnit = database.createVideoUnit();
        TextUnit textUnit = database.createTextUnit();
        addAttachmentToLecture();

        this.lecture1 = database.addLectureUnitsToLecture(this.lecture1, Set.of(exerciseUnit, attachmentUnit, videoUnit, textUnit));
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
        Course course = courseRepository.findByIdElseThrow(this.course1.getId());

        Lecture lecture = new Lecture();
        lecture.title("loremIpsum");
        lecture.setCourse(course);
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
        Lecture originalLecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture1.getId()).get();
        originalLecture.setTitle("Updated");
        originalLecture.setDescription("Updated");
        Lecture updatedLecture = request.putWithResponseBody("/api/lectures", originalLecture, Lecture.class, HttpStatus.OK);
        assertThat(updatedLecture.getTitle()).isEqualTo("Updated");
        assertThat(updatedLecture.getDescription()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateLecture_NoId_shouldReturnBadRequest() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoals(lecture1.getId()).get();
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
        request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.FORBIDDEN, Lecture.class);
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_ExerciseAndAttachmentReleased_shouldGetLectureWithAllLectureUnits() throws Exception {
        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLectureWithDetails.getAttachments().size()).isEqualTo(2);

        testGetLecture(lecture1.getId());
    }

    private void testGetLecture(Long lectureId) throws Exception {
        Lecture receivedLecture = request.get("/api/lectures/" + lectureId, HttpStatus.OK, Lecture.class);
        assertThat(receivedLecture.getId()).isEqualTo(lectureId);
        // should not fetch lecture units or posts
        assertThat(receivedLecture.getLectureUnits()).isNullOrEmpty();
        assertThat(receivedLecture.getPosts()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_ExerciseNotReleased_shouldGetLectureWithoutExerciseUnit() throws Exception {
        TextExercise exercise = textExerciseRepository.findByIdElseThrow(textExercise.getId());
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(10));
        textExerciseRepository.saveAndFlush(exercise);

        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits().size()).isEqualTo(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).collect(Collectors.toList())).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser("tutor1");
        receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).collect(Collectors.toList())).isNotEmpty();

        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = "student1", roles = "USER")
    public void getLecture_AttachmentNotReleased_shouldGetLectureWithoutAttachmentUnitAndAttachment() throws Exception {
        Attachment unitAttachment = attachmentRepository.findById(attachmentOfAttachmentUnit.getId()).get();
        unitAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        Attachment lectureAttachment = attachmentRepository.findById(attachmentDirectOfLecture.getId()).get();
        lectureAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        attachmentRepository.saveAll(Set.of(unitAttachment, lectureAttachment));

        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments().stream().filter(attachment -> attachment.getId().equals(lectureAttachment.getId())).findFirst().isEmpty()).isTrue();
        assertThat(receivedLectureWithDetails.getLectureUnits().size()).isEqualTo(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).collect(Collectors.toList())).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser("tutor1");
        receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments().stream().anyMatch(attachment -> attachment.getId().equals(lectureAttachment.getId()))).isTrue();
        assertThat(receivedLectureWithDetails.getLectureUnits().size()).isEqualTo(4);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).collect(Collectors.toList())).isNotEmpty();

        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_lectureExists_shouldDeleteLecture() throws Exception {
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional.isEmpty()).isTrue();
    }

    /**
     * Hibernates sometimes adds null to the list of lecture units to keep the order after a lecture unit has been deleted.
     * This should not happen any more as we have refactored the way lecture units are deleted, nevertheless we want to
     * check here that this case not causes any errors as null values could still exist in the database
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLecture_NullInListOfLectureUnits_shouldDeleteLecture() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        List<LectureUnit> lectureUnits = lecture.getLectureUnits();
        assertThat(lectureUnits.size()).isEqualTo(4);
        ArrayList<LectureUnit> lectureUnitsWithNulls = new ArrayList<>();
        for (LectureUnit lectureUnit : lectureUnits) {
            lectureUnitsWithNulls.add(null);
            lectureUnitsWithNulls.add(lectureUnit);
        }
        lecture.getLectureUnits().clear();
        lecture.getLectureUnits().addAll(lectureUnitsWithNulls);
        lectureRepository.saveAndFlush(lecture);
        lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        lectureUnits = lecture.getLectureUnits();
        assertThat(lectureUnits.size()).isEqualTo(8);
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional.isEmpty()).isTrue();
    }

    /**
     * We have to make sure to reorder the list of lecture units when we delete a lecture unit to prevent hibernate
     * from entering nulls into the list to keep the order of lecture units
     */
    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void deleteLectureUnit_FirstLectureUnit_ShouldReorderList() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits().size()).isEqualTo(4);
        LectureUnit firstLectureUnit = lecture.getLectureUnits().stream().findFirst().get();
        request.delete("/api/lectures/" + lecture1.getId() + "/lecture-units/" + firstLectureUnit.getId(), HttpStatus.OK);
        lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits().size()).isEqualTo(3);
        boolean nullFound = false;
        for (LectureUnit lectureUnit : lecture.getLectureUnits()) {
            if (Objects.isNull(lectureUnit)) {
                nullFound = true;
                break;
            }
        }
        assertThat(nullFound).isFalse();

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

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetLectureTitleAsInstuctor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetLectureTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetLectureTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    private void testGetLectureTitle() throws Exception {
        Lecture lecture = new Lecture();
        lecture.title("Test Lecture");
        lectureRepository.save(lecture);

        final var title = request.get("/api/lectures/" + lecture.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(lecture.getTitle());
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetLectureTitleForNonExistingLecture() throws Exception {
        request.get("/api/lectures/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }
}
