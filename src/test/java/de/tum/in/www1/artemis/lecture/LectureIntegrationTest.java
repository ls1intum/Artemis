package de.tum.in.www1.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.*;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.ModelFactory;

class LectureIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "lectureintegrationtest";

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private CourseRepository courseRepository;

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
    void initTestCase() throws Exception {
        int numberOfTutors = 2;
        database.addUsers(TEST_PREFIX, 1, numberOfTutors, 0, 1);
        List<Course> courses = this.database.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, numberOfTutors);
        this.course1 = this.courseRepository.findByIdWithExercisesAndLecturesElseThrow(courses.get(0).getId());
        var lecture = this.course1.getLectures().stream().findFirst().get();
        lecture.setTitle("Lecture " + lecture.getId()); // needed for search by title
        this.lecture1 = lectureRepository.save(lecture);
        this.textExercise = textExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().get();
        // Add users that are not in the course
        database.createAndSaveUser(TEST_PREFIX + "student42");
        database.createAndSaveUser(TEST_PREFIX + "instructor42");

        // Setting up a lecture with various kinds of content
        ExerciseUnit exerciseUnit = database.createExerciseUnit(textExercise);
        AttachmentUnit attachmentUnit = database.createAttachmentUnit(true);
        this.attachmentOfAttachmentUnit = attachmentUnit.getAttachment();
        VideoUnit videoUnit = database.createVideoUnit();
        TextUnit textUnit = database.createTextUnit();
        addAttachmentToLecture();

        this.lecture1 = database.addLectureUnitsToLecture(this.lecture1, List.of(exerciseUnit, attachmentUnit, videoUnit, textUnit));
    }

    private void addAttachmentToLecture() {
        this.attachmentDirectOfLecture = ModelFactory.generateAttachment(null);
        this.attachmentDirectOfLecture.setLink("files/temp/example2.txt");
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
        request.postWithResponseBody("/api/lectures/import/" + lecture1.getId() + "?courseId=" + course1.getId(), null, Lecture.class, HttpStatus.FORBIDDEN);
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
    void createLecture_correctRequestBody_shouldCreateLecture() throws Exception {
        Course course = courseRepository.findByIdElseThrow(this.course1.getId());

        Lecture lecture = new Lecture();
        lecture.setTitle("loremIpsum");
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
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLecture_alreadyId_shouldReturnBadRequest() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setId(1L);
        request.postWithResponseBody("/api/lectures", lecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLecture_correctRequestBody_shouldUpdateLecture() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get();
        originalLecture.setTitle("Updated");
        originalLecture.setDescription("Updated");
        Lecture updatedLecture = request.putWithResponseBody("/api/lectures", originalLecture, Lecture.class, HttpStatus.OK);
        assertThat(updatedLecture.getTitle()).isEqualTo("Updated");
        assertThat(updatedLecture.getDescription()).isEqualTo("Updated");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLecture_NoId_shouldReturnBadRequest() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithLectureUnits(lecture1.getId()).get();
        originalLecture.setId(null);
        request.putWithResponseBody("/api/lectures", originalLecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_withOutLectureUnits_shouldGetLecturesWithOutLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/courses/" + course1.getId() + "/lectures", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures).hasSize(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().get();
        assertThat(lecture.getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_WithLectureUnitsWithSlides_shouldGetLecturesWithLectureUnitsWithSlides() throws Exception {
        int numberOfSlides = 2;
        Lecture lectureWithSlides = ModelFactory.generateLecture(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().plusDays(5), course1);
        lectureWithSlides = lectureRepository.save(lectureWithSlides);
        AttachmentUnit attachmentUnitWithSlides = database.createAttachmentUnitWithSlides(numberOfSlides);
        lectureWithSlides = database.addLectureUnitsToLecture(lectureWithSlides, List.of(attachmentUnitWithSlides));

        List<Lecture> returnedLectures = request.getList("/api/courses/" + course1.getId() + "/lectures-with-slides", HttpStatus.OK, Lecture.class);

        final Lecture finalLectureWithSlides = lectureWithSlides;
        Lecture filteredLecture = returnedLectures.stream().filter(lecture -> lecture.getId().equals(finalLectureWithSlides.getId())).findFirst().get();

        assertThat(filteredLecture.getLectureUnits()).hasSize(1); // we only have one lecture unit which is attachmentUnitWithSlides
        assertThat(filteredLecture.getLectureUnits()).contains(attachmentUnitWithSlides);
        AttachmentUnit attachmentUnit = (AttachmentUnit) filteredLecture.getLectureUnits().get(0);
        assertThat(attachmentUnit.getSlides()).hasSize(numberOfSlides);

        Lecture lectureWithDetails = request.get("/api/lectures/" + lectureWithSlides.getId() + "/details-with-slides", HttpStatus.OK, Lecture.class);

        assertThat(lectureWithDetails.getLectureUnits()).hasSize(1); // we only have one lecture unit which is attachmentUnitWithSlides
        assertThat(lectureWithDetails.getLectureUnits()).contains(attachmentUnitWithSlides);
        AttachmentUnit attachmentUnitDetails = (AttachmentUnit) lectureWithDetails.getLectureUnits().get(0);
        assertThat(attachmentUnitDetails.getSlides()).hasSize(numberOfSlides);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_withLectureUnits_shouldGetLecturesWithLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/courses/" + course1.getId() + "/lectures?withLectureUnits=true", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures).hasSize(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().get();
        assertThat(lecture.getLectureUnits()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getLecture_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN, Lecture.class);
        request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.FORBIDDEN, Lecture.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_ExerciseAndAttachmentReleased_shouldGetLectureWithAllLectureUnits() throws Exception {
        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4);
        assertThat(receivedLectureWithDetails.getAttachments()).hasSize(2);

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
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_ExerciseNotReleased_shouldGetLectureWithoutExerciseUnit() throws Exception {
        TextExercise exercise = textExerciseRepository.findByIdElseThrow(textExercise.getId());
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(10));
        textExerciseRepository.saveAndFlush(exercise);

        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).toList()).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser(TEST_PREFIX + "tutor1");
        receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).toList()).isNotEmpty();

        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_AttachmentNotReleased_shouldGetLectureWithoutAttachmentUnitAndAttachment() throws Exception {
        Attachment unitAttachment = attachmentRepository.findById(attachmentOfAttachmentUnit.getId()).get();
        unitAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        Attachment lectureAttachment = attachmentRepository.findById(attachmentDirectOfLecture.getId()).get();
        lectureAttachment.setReleaseDate(ZonedDateTime.now().plusDays(10));
        attachmentRepository.saveAll(Set.of(unitAttachment, lectureAttachment));

        Lecture receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments().stream().filter(attachment -> attachment.getId().equals(lectureAttachment.getId())).findFirst()).isEmpty();
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit).toList()).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        database.changeUser(TEST_PREFIX + "tutor1");
        receivedLectureWithDetails = request.get("/api/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments()).anyMatch(attachment -> attachment.getId().equals(lectureAttachment.getId()));
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4).anyMatch(lectureUnit -> lectureUnit instanceof AttachmentUnit);
        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_lectureExists_shouldDeleteLecture() throws Exception {
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional).isEmpty();
    }

    /**
     * Hibernates sometimes adds null to the list of lecture units to keep the order after a lecture unit has been deleted.
     * This should not happen any more as we have refactored the way lecture units are deleted, nevertheless we want to
     * check here that this case not causes any errors as null values could still exist in the database
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_NullInListOfLectureUnits_shouldDeleteLecture() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        List<LectureUnit> lectureUnits = lecture.getLectureUnits();
        assertThat(lectureUnits).hasSize(4);
        ArrayList<LectureUnit> lectureUnitsWithNulls = new ArrayList<>();
        for (LectureUnit lectureUnit : lectureUnits) {
            lectureUnitsWithNulls.add(null);
            lectureUnitsWithNulls.add(lectureUnit);
        }
        lecture.getLectureUnits().clear();
        lecture.getLectureUnits().addAll(lectureUnitsWithNulls);
        lectureRepository.saveAndFlush(lecture);
        lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        lectureUnits = lecture.getLectureUnits();
        assertThat(lectureUnits).hasSize(8);
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLecture_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_lectureDoesNot_shouldReturnNotFound() throws Exception {
        request.delete("/api/lectures/" + 0, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureTitleAsInstuctor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLectureTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetLectureTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    private void testGetLectureTitle() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lectureRepository.save(lecture);

        final var title = request.get("/api/lectures/" + lecture.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(lecture.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetLectureTitleForNonExistingLecture() throws Exception {
        request.get("/api/lectures/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.getSearchResult("/api/lectures/", HttpStatus.OK, Lecture.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = database.configureSearch(lecture1.getTitle());
        final var result = request.getSearchResult("/api/lectures/", HttpStatus.OK, Lecture.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        final var search = database.configureSearch(lecture1.getTitle());
        final var result = request.getSearchResult("/api/lectures/", HttpStatus.OK, Lecture.class, database.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImport() throws Exception {
        Course course2 = this.database.addEmptyCourse();

        Lecture lecture2 = request.postWithResponseBody("/api/lectures/import/" + lecture1.getId() + "?courseId=" + course2.getId(), null, Lecture.class, HttpStatus.CREATED);

        // Assert that all lecture units (except exercise units) were copied
        assertThat(lecture2.getLectureUnits().stream().map(LectureUnit::getName).toList()).containsExactlyElementsOf(
                this.lecture1.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(LectureUnit::getName).toList());

        assertThat(lecture2.getAttachments().stream().map(Attachment::getName).toList())
                .containsExactlyElementsOf(this.lecture1.getAttachments().stream().map(Attachment::getName).toList());
    }
}
