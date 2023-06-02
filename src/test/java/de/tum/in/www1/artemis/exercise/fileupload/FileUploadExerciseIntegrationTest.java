package de.tum.in.www1.artemis.exercise.fileupload;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.IncludedInOverallScore;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.web.rest.dto.CourseForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

class FileUploadExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "fileuploaderxercise";

    @Autowired
    private FileUploadTestService fileUploadTestService;

    private final String creationFilePattern = "png, pdf, jPg      , r, DOCX";

    @BeforeEach
    void initTestCase() {
        database.addUsers(TEST_PREFIX, 2, 1, 1, 1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise("Example file pattern");
        fileUploadExercise.setId(1L);

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFailsIfAlreadyCreated() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise("Example file pattern");

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidMaxScore() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise("Example file pattern");
        fileUploadExercise.setMaxPoints(0.0);

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_InvalidInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise(creationFilePattern);
        // make sure the instructor is not instructor for this course anymore by changing the courses' instructor group name
        fileUploadTestService.changeInstructorGroupName(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), "cool new name");

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_AlmostEmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise(" ");

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseFails_EmptyFilePattern() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise("");

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_NotIncludedInvalidBonusPoints() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setMaxPoints(10.0);
        fileUploadExercise.setBonusPoints(1.0);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);

        request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setFilePattern(creationFilePattern);
        var gradingCriteria = fileUploadTestService.addGradingInstructionsToExercise(fileUploadExercise, false);

        FileUploadExercise receivedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.CREATED);

        assertThat(receivedFileUploadExercise).isNotNull();
        assertThat(receivedFileUploadExercise.getId()).isNotNull();
        assertThat(receivedFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId()).as("exerciseGroupId was set correctly")
                .isEqualTo(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId());

        assertThat(receivedFileUploadExercise.getGradingCriteria().get(0).getTitle()).isNull();
        assertThat(receivedFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createExamActiveFileUploadExercise(creationFilePattern);
        var gradingCriteria = fileUploadTestService.addGradingInstructionsToExercise(fileUploadExercise, false);

        FileUploadExercise createdFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);

        assertThat(createdFileUploadExercise).isNotNull();
        assertThat(createdFileUploadExercise.getId()).isNotNull();
        assertThat(createdFileUploadExercise.getFilePattern()).isEqualTo(creationFilePattern.toLowerCase().replaceAll("\\s+", ""));
        assertThat(createdFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(createdFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(createdFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(fileUploadExercise.getExerciseGroup().getId());

        assertThat(createdFileUploadExercise.getGradingCriteria().get(0).getTitle()).isNull();
        assertThat(createdFileUploadExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions()).hasSize(1);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createExamActiveFileUploadExercise(creationFilePattern);

        request.postWithResponseBody("/api/file-upload-exercises", invalidDates.applyTo(fileUploadExercise), FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createExamActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setCourse(null);
        fileUploadExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise("very cool pattern");

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(fileUploadExercise.getId()).isEqualTo(receivedFileUploadExercise.getId());
        assertThat(fileUploadExercise).isEqualTo(receivedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getExamFileUploadExercise_asStudent_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getExamFileUploadExercise_asTutor_forbidden() throws Exception {
        getExamFileUploadExercise();
    }

    private void getExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);
        request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(receivedFileUploadExercise).as("exercise was retrieved").isNotNull();
        assertThat(receivedFileUploadExercise.getId()).as("exercise with the right id was retrieved").isEqualTo(fileUploadExercise.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getFileUploadExerciseFails_wrongId() throws Exception {
        fileUploadTestService.createAndSaveActiveFileUploadExercise("filePattern");
        request.get("/api/file-upload-exercises/" + 555555, HttpStatus.NOT_FOUND, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getExamFileUploadExercise_InstructorNotInGroup() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);
        fileUploadTestService.changeInstructorGroupName(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), "new-instructor-group-name");

        request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetFileUploadExercise_setGradingInstructionFeedbackUsed() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        var gradingCriteria = fileUploadTestService.addGradingInstructionsToExercise(fileUploadExercise, true);
        fileUploadTestService.createAndSaveFeedback(gradingCriteria.get(0).getStructuredGradingInstructions().get(0));

        FileUploadExercise receivedFileUploadExercise = request.get("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK, FileUploadExercise.class);
        assertThat(receivedFileUploadExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(fileUploadTestService.findFileUploadExercise(fileUploadExercise.getId())).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteFileUploadExercise_asStudent() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN);
        assertThat(fileUploadTestService.findFileUploadExercise(fileUploadExercise.getId())).isEqualTo(fileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_WithWrongId() throws Exception {
        fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        request.delete("/api/file-upload-exercises/" + 5555555, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        fileUploadTestService.changeInstructorGroupName(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), "new-instructor-group-name");

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.FORBIDDEN);
        assertThat(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getExercises()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteExamFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);

        request.delete("/api/file-upload-exercises/" + fileUploadExercise.getId(), HttpStatus.OK);
        assertThat(fileUploadTestService.findFileUploadExercise(fileUploadExercise.getId())).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        final ZonedDateTime dueDate = ZonedDateTime.now().plusDays(10);
        fileUploadExercise.setDueDate(dueDate);
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));

        FileUploadExercise receivedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "?notificationText=notification",
                fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        assertThat(receivedFileUploadExercise.getDueDate()).isEqualToIgnoringNanos(dueDate);
        assertThat(receivedFileUploadExercise.getCourseViaExerciseGroupOrCourseMember()).as("course was set for normal exercise").isNotNull();
        assertThat(receivedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was not set for normal exercise").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseFails_InstructorNotInGroup() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setDueDate(ZonedDateTime.now().plusDays(10));
        fileUploadExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(11));

        // remove instructor from the course
        fileUploadTestService.changeInstructorGroupName(fileUploadExercise.getCourseViaExerciseGroupOrCourseMember(), "new-instructor-group-name");

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);
        String newTitle = "New file upload exercise title";
        fileUploadExercise.setTitle(newTitle);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise,
                FileUploadExercise.class, HttpStatus.OK);

        assertThat(updatedFileUploadExercise.getTitle()).isEqualTo(newTitle);
        assertThat(updatedFileUploadExercise.isCourseExercise()).as("course was not set for exam exercise").isFalse();
        assertThat(updatedFileUploadExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(updatedFileUploadExercise.getExerciseGroup().getId()).as("exerciseGroupId was not updated").isEqualTo(fileUploadExercise.getExerciseGroup().getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseForExam_invalid_dates(InvalidExamExerciseDateConfiguration dates) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), dates.applyTo(fileUploadExercise), FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_setBothCourseAndExerciseGroupOrNeither_badRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);
        fileUploadExercise.setCourse(fileUploadExercise.getExerciseGroup().getExam().getCourse());

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setExerciseGroup(null);
        fileUploadExercise.setCourse(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExercise_conversionBetweenCourseAndExamExercise_badRequest() throws Exception {
        FileUploadExercise fileUploadExerciseWithCourse = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        FileUploadExercise fileUploadExerciseWithExerciseGroup = fileUploadTestService.createAndSaveExamActiveFileUploadExercise(creationFilePattern);

        fileUploadExerciseWithCourse.setCourse(null);
        fileUploadExerciseWithCourse.setExerciseGroup(fileUploadExerciseWithExerciseGroup.getExerciseGroup());

        fileUploadExerciseWithExerciseGroup.setCourse(fileUploadExerciseWithCourse.getCourseViaExerciseGroupOrCourseMember());
        fileUploadExerciseWithExerciseGroup.setExerciseGroup(null);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExerciseWithCourse.getId(), fileUploadExerciseWithCourse, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExerciseWithExerciseGroup.getId(), fileUploadExerciseWithExerciseGroup, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateFileUploadExerciseDueDate() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final FileUploadSubmission submission1 = FileUploadTestFactory.generateFileUploadSubmission(true);
            database.addFileUploadSubmission(fileUploadExercise, submission1, TEST_PREFIX + "student1");

            final FileUploadSubmission submission2 = FileUploadTestFactory.generateFileUploadSubmission(true);
            database.addFileUploadSubmission(fileUploadExercise, submission2, TEST_PREFIX + "student2");

            fileUploadTestService.setIndividualDueDate(fileUploadExercise, new ArrayList<>(List.of(ZonedDateTime.now().plusHours(2), individualDueDate)));
        }

        fileUploadExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/file-upload-exercises/" + fileUploadExercise.getId(), fileUploadExercise, HttpStatus.OK);

        {
            final var participations = fileUploadTestService.getParticipationsOfExercise(fileUploadExercise);
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourse_asInstructor() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        Course course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        List<FileUploadExercise> receivedFileUploadExercises = request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.OK,
                FileUploadExercise.class);

        // this seems to be a flaky test, based on the execution order, the following line has a problem with authentication, this should fix it
        database.changeUser(TEST_PREFIX + "instructor1");
        assertThat(receivedFileUploadExercises).hasSameSizeAs(course.getExercises());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllFileUploadExercisesForCourseFails_InstructorNotInGroup() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        Course course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();

        fileUploadTestService.changeInstructorGroupName(course, "new-group-name");

        request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllFileUploadExercisesForCourse_asStudent() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        Course course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();

        request.getList("/api/courses/" + course.getId() + "/file-upload-exercises", HttpStatus.FORBIDDEN, FileUploadExercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        var gradingCriteria = fileUploadTestService.addGradingInstructionsToExercise(fileUploadExercise, true);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // change grading instruction score
        gradingCriteria.get(0).getStructuredGradingInstructions().get(0).setCredits(3);
        gradingCriteria.remove(1);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=false", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getCredits()).isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_shouldDeleteFeedbacks() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        var gradingCriteria = fileUploadTestService.addGradingInstructionsToExercise(fileUploadExercise, true);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(fileUploadExercise, TEST_PREFIX + "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.remove(1);
        gradingCriteria.remove(0);
        fileUploadExercise.setGradingCriteria(gradingCriteria);

        FileUploadExercise updatedFileUploadExercise = request.putWithResponseBody(
                "/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate" + "?deleteFeedback=true", fileUploadExercise, FileUploadExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedFileUploadExercise);
        assertThat(updatedFileUploadExercise.getGradingCriteria()).hasSize(1);
        assertThat(updatedResults.get(0).getScore()).isZero();
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testReEvaluateAndUpdateFileUploadExercise_isNotAtLeastEditorInCourse_forbidden() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndAndSaveFileUploadExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1),
                creationFilePattern);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExercise, FileUploadExercise.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndAndSaveFileUploadExercise(ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1),
                creationFilePattern);

        FileUploadExercise fileUploadExerciseToBeConflicted = fileUploadTestService.findFileUploadExercise(fileUploadExercise.getId());
        fileUploadExerciseToBeConflicted.setId(123456789L);

        request.putWithResponseBody("/api/file-upload-exercises/" + fileUploadExercise.getId() + "/re-evaluate", fileUploadExerciseToBeConflicted, FileUploadExercise.class,
                HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testReEvaluateAndUpdateFileUploadExercise_notFound() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        request.putWithResponseBody("/api/file-upload-exercises/" + 123456789 + "/re-evaluate", fileUploadExercise, FileUploadExercise.class, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setInvalidExampleSolutionPublicationDate_badRequest() throws Exception {
        final var baseTime = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(3));
        fileUploadExercise.setDueDate(null);
        fileUploadExercise.setExampleSolutionPublicationDate(baseTime.plusHours(2));

        request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createFileUploadExercise_setValidExampleSolutionPublicationDate() throws Exception {
        final var baseTime = ZonedDateTime.now();
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        fileUploadExercise.setId(null);
        fileUploadExercise.setAssessmentDueDate(null);
        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_COMPLETELY);

        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(2));
        var exampleSolutionPublicationDate = baseTime.plusHours(3);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        var result = request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        fileUploadExercise.setReleaseDate(baseTime.plusHours(1));
        fileUploadExercise.setDueDate(baseTime.plusHours(3));
        exampleSolutionPublicationDate = baseTime.plusHours(2);
        fileUploadExercise.setExampleSolutionPublicationDate(exampleSolutionPublicationDate);

        result = request.postWithResponseBody("/api/file-upload-exercises/", fileUploadExercise, FileUploadExercise.class, HttpStatus.CREATED);
        assertThat(result.getExampleSolutionPublicationDate()).isEqualTo(exampleSolutionPublicationDate);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetFileUploadExercise_asStudent_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(true, TEST_PREFIX + "student1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseAsEditorSuccess() throws Exception {
        FileUploadExercise expectedFileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        Course course = database.addEmptyCourse();

        expectedFileUploadExercise.setCourse(course);

        Long sourceExerciseId = expectedFileUploadExercise.getId();
        var importedFileUploadExercise = request.postWithResponseBody("/api/file-upload-exercises/import/" + sourceExerciseId, expectedFileUploadExercise, FileUploadExercise.class,
                HttpStatus.CREATED);
        assertThat(importedFileUploadExercise).usingRecursiveComparison()
                .ignoringFields("id", "course", "shortName", "releaseDate", "dueDate", "assessmentDueDate", "exampleSolutionPublicationDate").isEqualTo(expectedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseFromCourseToCourseNegativeCourseIdBadRequest() throws Exception {
        FileUploadExercise expectedFileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        Course course = database.addEmptyCourse();
        expectedFileUploadExercise.setCourse(course);

        request.postWithResponseBody("/api/file-upload-exercises/import/" + -1, expectedFileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testImportFileUploadExerciseCourseNotSetBadRequest() throws Exception {
        FileUploadExercise expectedFileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);
        expectedFileUploadExercise.setCourse(null);

        request.postWithResponseBody("/api/file-upload-exercises/import/" + expectedFileUploadExercise.getId(), expectedFileUploadExercise, FileUploadExercise.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetAllExercisesOnPageAsEditorSuccess() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        final var searchTerm = database.configureSearch(fileUploadExercise.getTitle());
        SearchResultPageDTO<Exercise> result = request.getSearchResult("/api/file-upload-exercises", HttpStatus.OK, Exercise.class, database.searchMapping(searchTerm));
        assertThat(result.getResultsOnPage()).hasSize(1);
        assertThat(result.getNumberOfPages()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "ta1", roles = "TA")
    void testImportFileUploadExerciseAsTeachingAssistantFails() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndSaveActiveFileUploadExercise(creationFilePattern);

        request.postWithResponseBody("/api/file-upload-exercises/import/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testExamExerciseNotIncludedInScoreReturnsBadRequest() throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createExamActiveFileUploadExercise(creationFilePattern);

        fileUploadExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/file-upload-exercises/import/" + fileUploadExercise.getId(), fileUploadExercise, FileUploadExercise.class, HttpStatus.BAD_REQUEST);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetFileUploadExercise_asInstructor_exampleSolutionVisibility() throws Exception {
        testGetFileUploadExercise_exampleSolutionVisibility(false, TEST_PREFIX + "instructor1");
    }

    private void testGetFileUploadExercise_exampleSolutionVisibility(boolean isStudent, String username) throws Exception {
        FileUploadExercise fileUploadExercise = fileUploadTestService.createAndAndSaveFileUploadExercise(ZonedDateTime.now().minusDays(1), ZonedDateTime.now().minusHours(2),
                creationFilePattern);

        // Utility function to avoid duplication
        Function<Course, FileUploadExercise> fileUploadExerciseGetter = c -> (FileUploadExercise) c.getExercises().stream()
                .filter(e -> e.getId().equals(fileUploadExercise.getId())).findAny().get();

        fileUploadExercise.setExampleSolution("Sample<br>solution");

        if (isStudent) {
            database.createAndSaveParticipationForExercise(fileUploadExercise, username);
        }

        // Test example solution publication date not set.
        fileUploadTestService.setExampleSolutionPublicationDateAndSave(fileUploadExercise, null);

        CourseForDashboardDTO courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard",
                HttpStatus.OK, CourseForDashboardDTO.class);
        Course course = courseForDashboard.course();
        FileUploadExercise fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }

        // Test example solution publication date in the past.
        fileUploadTestService.setExampleSolutionPublicationDateAndSave(fileUploadExercise, ZonedDateTime.now().minusHours(1));

        courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());

        // Test example solution publication date in the future.
        fileUploadTestService.setExampleSolutionPublicationDateAndSave(fileUploadExercise, ZonedDateTime.now().plusHours(1));

        courseForDashboard = request.get("/api/courses/" + fileUploadExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/for-dashboard", HttpStatus.OK,
                CourseForDashboardDTO.class);
        course = courseForDashboard.course();
        fileUploadExerciseFromApi = fileUploadExerciseGetter.apply(course);

        if (isStudent) {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isNull();
        }
        else {
            assertThat(fileUploadExerciseFromApi.getExampleSolution()).isEqualTo(fileUploadExercise.getExampleSolution());
        }
    }
}
