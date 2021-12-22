package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Objects;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.plagiarism.modeling.ModelingPlagiarismResult;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider;
import de.tum.in.www1.artemis.util.InvalidExamExerciseDatesArgumentProvider.InvalidExamExerciseDateConfiguration;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.util.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;

public class ModelingExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private CourseRepository courseRepo;

    @Autowired
    private StudentParticipationRepository studentParticipationRepository;

    private ModelingExercise classExercise;

    private List<GradingCriterion> gradingCriteria;

    @BeforeEach
    public void initTestCase() throws Exception {
        database.addUsers(2, 1, 0, 1);
        Course course = database.addCourseWithOneModelingExercise();
        classExercise = (ModelingExercise) course.getExercises().iterator().next();

        // Add users that are not in course
        userRepo.save(ModelFactory.generateActivatedUser("instructor2"));
        userRepo.save(ModelFactory.generateActivatedUser("tutor2"));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(username = "user1", roles = "USER")
    public void testGetModelingExercise_asStudent_Forbidden() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExercise_asTA() throws Exception {
        ModelingExercise receivedModelingExercise = request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK, ModelingExercise.class);
        gradingCriteria = database.addGradingInstructionsToExercise(receivedModelingExercise);
        assertThat(receivedModelingExercise.getGradingCriteria().get(0).getTitle()).isEqualTo(null);
        assertThat(receivedModelingExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("test title");

        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(gradingCriteria.get(1).getStructuredGradingInstructions().size()).isEqualTo(3);
        assertThat(gradingCriteria.get(0).getStructuredGradingInstructions().get(0).getInstructionDescription())
                .isEqualTo("created first instruction with empty criteria for testing");
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetModelingExercise_tutorNotInCourse() throws Exception {
        request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN, ModelingExercise.class);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExercise_setGradingInstructionFeedbackUsed() throws Exception {

        gradingCriteria = database.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        Feedback feedback = new Feedback();
        feedback.setGradingInstruction(gradingCriteria.get(0).getStructuredGradingInstructions().get(0));
        feedbackRepository.save(feedback);

        ModelingExercise receivedModelingExercise = request.get("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK, ModelingExercise.class);

        assertThat(receivedModelingExercise.isGradingInstructionFeedbackUsed()).isTrue();
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testGetModelingExerciseForCourse_asTA() throws Exception {
        request.get("/api/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/modeling-exercises", HttpStatus.OK, List.class);
    }

    @Test
    @WithMockUser(username = "tutor2", roles = "TA")
    public void testGetModelingExerciseForCourse_tutorNotInCourse() throws Exception {
        request.get("/api/courses/" + classExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/modeling-exercises", HttpStatus.FORBIDDEN, List.class);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testCreateModelingExercise_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);
        ModelingExercise receivedModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(receivedModelingExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().size()).isEqualTo(1);
        assertThat(receivedModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size()).isEqualTo(3);

        modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId(), 1L);
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.BAD_REQUEST);

        modelingExercise = modelingExerciseUtilService.createModelingExercise(2L);
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testCreateModelingExercise_instructorNotInCourse() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        request.post("/api/modeling-exercises", modelingExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExercise_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);

        createdModelingExercise.setGradingCriteria(gradingCriteria);
        var params = new LinkedMultiValueMap<String, String>();
        var notificationText = "notified!";
        params.add("notificationText", notificationText);
        ModelingExercise returnedModelingExercise = request.putWithResponseBodyAndParams("/api/modeling-exercises", createdModelingExercise, ModelingExercise.class, HttpStatus.OK,
                params);
        assertThat(returnedModelingExercise.getGradingCriteria().size()).isEqualTo(gradingCriteria.size());
        verify(groupNotificationService).notifyStudentAndEditorAndInstructorGroupAboutExerciseUpdate(returnedModelingExercise, notificationText);

        // use an arbitrary course id that was not yet stored on the server to get a bad request in the PUT call
        modelingExercise = modelingExerciseUtilService.createModelingExercise(100L, classExercise.getId());
        request.put("/api/modeling-exercises", modelingExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExercise_updatingCourseId_conflict() throws Exception {
        // Create a modeling exercise.
        ModelingExercise createdModelingExercise = classExercise;
        Long oldCourseId = createdModelingExercise.getCourseViaExerciseGroupOrCourseMember().getId();

        // Create a new course with different id.
        Long newCourseId = oldCourseId + 1;
        Course newCourse = database.createCourse(newCourseId);

        // Assign new course to the modeling exercise.
        createdModelingExercise.setCourse(newCourse);

        // Modeling exercise update with the new course should fail.
        ModelingExercise returnedModelingExercise = request.putWithResponseBody("/api/modeling-exercises", createdModelingExercise, ModelingExercise.class, HttpStatus.CONFLICT);
        assertThat(returnedModelingExercise).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExerciseCriteria_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);
        var currentCriteriaSize = modelingExercise.getGradingCriteria().size();
        var newCriteria = new GradingCriterion();
        newCriteria.setTitle("new");
        modelingExercise.addGradingCriteria(newCriteria);
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isEqualTo(currentCriteriaSize + 1);

        modelingExercise.getGradingCriteria().get(1).setTitle("UPDATE");
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getTitle()).isEqualTo("UPDATE");

        // If the grading criteria are deleted then their instructions should also be deleted
        modelingExercise.setGradingCriteria(null);
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isEqualTo(0);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExerciseInstructions_asInstructor() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        gradingCriteria = database.addGradingInstructionsToExercise(modelingExercise);

        var currentInstructionsSize = modelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size();
        var newInstruction = new GradingInstruction();
        newInstruction.setInstructionDescription("New Instruction");

        modelingExercise.getGradingCriteria().get(1).addStructuredGradingInstructions(newInstruction);
        ModelingExercise createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().size()).isEqualTo(currentInstructionsSize + 1);

        modelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0).setInstructionDescription("UPDATE");
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions().get(0).getInstructionDescription()).isEqualTo("UPDATE");

        modelingExercise.getGradingCriteria().get(1).setStructuredGradingInstructions(null);
        createdModelingExercise = request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
        assertThat(createdModelingExercise.getGradingCriteria().size()).isGreaterThan(0);
        assertThat(createdModelingExercise.getGradingCriteria().get(1).getStructuredGradingInstructions()).isNullOrEmpty();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testUpdateModelingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExerciseRepository.save(modelingExercise);

        request.postWithResponseBody("/api/modeling-exercises/", invalidDates.applyTo(modelingExercise), ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void updateModelingExerciseDueDate() throws Exception {
        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final ModelingSubmission submission1 = ModelFactory.generateModelingSubmission("model1", true);
            database.addModelingSubmission(classExercise, submission1, "student1");
            final ModelingSubmission submission2 = ModelFactory.generateModelingSubmission("model2", false);
            database.addModelingSubmission(classExercise, submission2, "student2");

            final var participations = studentParticipationRepository.findByExerciseId(classExercise.getId());
            assertThat(participations).hasSize(2);
            participations.get(0).setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            studentParticipationRepository.saveAll(participations);
        }

        classExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        request.put("/api/modeling-exercises/", classExercise, HttpStatus.OK);

        {
            final var participations = studentParticipationRepository.findByExerciseId(classExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.get(0).getIndividualDueDate()).isEqualToIgnoringNanos(individualDueDate);
        }
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testUpdateModelingExercise_instructorNotInCourse() throws Exception {
        request.put("/api/modeling-exercises", classExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testDeleteModelingExercise_asInstructor() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.OK);
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "tutor1", roles = "TA")
    public void testDeleteModelingExercise_asTutor_Forbidden() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testDeleteModelingExercise_notInstructorInCourse() throws Exception {
        request.delete("/api/modeling-exercises/" + classExercise.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(course2);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseWithExampleSubmissionFromCourseToCourse() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();

        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        modelingExercise = modelingExerciseRepository.save(modelingExercise);
        database.addGradingInstructionsToExercise(modelingExercise);

        // Create example submission
        var exampleSubmission = database.generateExampleSubmission("model", modelingExercise, true);
        exampleSubmission = database.addExampleSubmission(exampleSubmission);
        database.addResultToSubmission(exampleSubmission.getSubmission(), AssessmentType.MANUAL);
        var submission = submissionRepository.findWithEagerResultAndFeedbackById(exampleSubmission.getSubmission().getId()).get();
        database.addFeedbackToResult(ModelFactory.generateFeedback().stream().findFirst().get(), Objects.requireNonNull(submission.getLatestResult()));

        modelingExercise.setCourse(course2);
        var importedModelingExercise = request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class,
                HttpStatus.CREATED);

        assertThat(modelingExerciseRepository.findById(importedModelingExercise.getId())).isPresent();
        importedModelingExercise = modelingExerciseRepository.findByIdWithExampleSubmissionsAndResults(modelingExercise.getId()).get();

        var importedExampleSubmission = importedModelingExercise.getExampleSubmissions().stream().findFirst().get();
        assertThat(importedExampleSubmission.getId()).isEqualTo(exampleSubmission.getId());
        assertThat(importedExampleSubmission.getSubmission().getLatestResult()).isEqualTo(submission.getLatestResult());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseFromCourseToExam() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        modelingExercise.setReleaseDate(null);
        modelingExercise.setDueDate(null);
        modelingExercise.setAssessmentDueDate(null);
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(null);
        modelingExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    public void importModelingExerciseFromCourseToExam_forbidden() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(null);
        modelingExercise.setExerciseGroup(exerciseGroup1);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseFromExamToCourse() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(course1);
        modelingExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "TA")
    public void importModelingExerciseFromExamToCourse_forbidden() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        Course course1 = database.addEmptyCourse();
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(course1);
        modelingExercise.setExerciseGroup(null);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseFromExamToExam() throws Exception {
        ExerciseGroup exerciseGroup1 = database.addExerciseGroupWithExamAndCourse(true);
        ExerciseGroup exerciseGroup2 = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup1);
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setExerciseGroup(exerciseGroup2);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.CREATED);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void importModelingExerciseFromCourseToCourse_badRequest() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        ModelingExercise modelingExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        modelingExerciseRepository.save(modelingExercise);
        modelingExercise.setCourse(null);

        request.postWithResponseBody("/api/modeling-exercises/import/" + modelingExercise.getId(), modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExerciseForExam() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);

        String title = "New Exam Modeling Exercise";
        DifficultyLevel difficulty = DifficultyLevel.HARD;
        modelingExercise.setTitle(title);
        modelingExercise.setDifficulty(difficulty);

        ModelingExercise newModelingExercise = request.postWithResponseBody("/api/modeling-exercises/", modelingExercise, ModelingExercise.class, HttpStatus.CREATED);

        assertThat(newModelingExercise.getTitle()).as("modeling exercise title was correctly set").isEqualTo(title);
        assertThat(newModelingExercise.getDifficulty()).as("modeling exercise difficulty was correctly set").isEqualTo(difficulty);
        assertThat(!newModelingExercise.isCourseExercise()).as("course was not set for exam exercise");
        assertThat(newModelingExercise.getExerciseGroup()).as("exerciseGroup was set for exam exercise").isNotNull();
        assertThat(newModelingExercise.getExerciseGroup().getId()).as("exerciseGroupId was set correctly").isEqualTo(exerciseGroup.getId());
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ArgumentsSource(InvalidExamExerciseDatesArgumentProvider.class)
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExerciseForExam_invalidExercise_dates(InvalidExamExerciseDateConfiguration invalidDates) throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);

        request.postWithResponseBody("/api/modeling-exercises/", invalidDates.applyTo(modelingExercise), ProgrammingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExercise_setCourseAndExerciseGroup_badRequest() throws Exception {
        ExerciseGroup exerciseGroup = database.addExerciseGroupWithExamAndCourse(true);
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, exerciseGroup);
        modelingExercise.setCourse(exerciseGroup.getExam().getCourse());
        request.postWithResponseBody("/api/modeling-exercises/", modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExercise_setNeitherCourseAndExerciseGroup_badRequest() throws Exception {
        ModelingExercise modelingExercise = ModelFactory.generateModelingExerciseForExam(DiagramType.ClassDiagram, null);
        request.postWithResponseBody("/api/modeling-exercises/", modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExercise_InvalidMaxScore() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(0.0);
        request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExercise_IncludedAsBonusInvalidBonusPoints() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(1.0);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void createModelingExercise_NotIncludedInvalidBonusPoints() throws Exception {
        ModelingExercise modelingExercise = modelingExerciseUtilService.createModelingExercise(classExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        modelingExercise.setMaxPoints(10.0);
        modelingExercise.setBonusPoints(1.0);
        modelingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.postWithResponseBody("/api/modeling-exercises", modelingExercise, ModelingExercise.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = database.configureSearch("");
        final var result = request.get("/api/modeling-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        database.addCourseWithOneModelingExercise();
        database.addCourseWithOneModelingExercise("Activity Diagram");
        final var searchClassDiagram = database.configureSearch("ClassDiagram");
        final var resultClassDiagram = request.get("/api/modeling-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchClassDiagram));
        assertThat(resultClassDiagram.getResultsOnPage().size()).isEqualTo(2);

        final var searchActivityDiagram = database.configureSearch("Activity Diagram");
        final var resultActivityDiagram = request.get("/api/modeling-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(searchActivityDiagram));
        assertThat(resultActivityDiagram.getResultsOnPage().size()).isEqualTo(1);

    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void testAdminGetsResultsFromAllCourses() throws Exception {
        database.addCourseInOtherInstructionGroupAndExercise("ClassDiagram");

        final var search = database.configureSearch("ClassDiagram");
        final var result = request.get("/api/modeling-exercises/", HttpStatus.OK, SearchResultPageDTO.class, database.exerciseSearchMapping(search));
        assertThat(result.getResultsOnPage().size()).isEqualTo(2);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImport_team_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        ModelingExercise sourceExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        sourceExercise.setMode(ExerciseMode.INDIVIDUAL);
        sourceExercise = modelingExerciseRepository.save(sourceExercise);

        var exerciseToBeImported = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course2);
        exerciseToBeImported.setMode(ExerciseMode.TEAM);
        exerciseToBeImported.setCourse(course2);

        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exerciseToBeImported);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        exerciseToBeImported.setTeamAssignmentConfig(teamAssignmentConfig);

        exerciseToBeImported = request.postWithResponseBody("/api/modeling-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, ModelingExercise.class,
                HttpStatus.CREATED);

        assertEquals(course2.getId(), exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.TEAM, exerciseToBeImported.getMode());
        assertEquals(teamAssignmentConfig.getMinTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMinTeamSize());
        assertEquals(teamAssignmentConfig.getMaxTeamSize(), exerciseToBeImported.getTeamAssignmentConfig().getMaxTeamSize());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = modelingExerciseRepository.findById(sourceExercise.getId()).get();
        assertEquals(course1.getId(), sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.INDIVIDUAL, sourceExercise.getMode());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testImport_individual_modeChange() throws Exception {
        var now = ZonedDateTime.now();
        Course course1 = database.addEmptyCourse();
        Course course2 = database.addEmptyCourse();
        ModelingExercise sourceExercise = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course1);
        sourceExercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(sourceExercise);
        teamAssignmentConfig.setMinTeamSize(1);
        teamAssignmentConfig.setMaxTeamSize(10);
        sourceExercise.setTeamAssignmentConfig(teamAssignmentConfig);
        sourceExercise.setCourse(course1);

        sourceExercise = modelingExerciseRepository.save(sourceExercise);
        teamRepository.save(sourceExercise, new Team());

        var exerciseToBeImported = ModelFactory.generateModelingExercise(now.minusDays(1), now.minusHours(2), now.minusHours(1), DiagramType.ClassDiagram, course2);
        exerciseToBeImported.setMode(ExerciseMode.INDIVIDUAL);
        exerciseToBeImported.setCourse(course2);

        exerciseToBeImported = request.postWithResponseBody("/api/modeling-exercises/import/" + sourceExercise.getId(), exerciseToBeImported, ModelingExercise.class,
                HttpStatus.CREATED);

        assertEquals(course2.getId(), exerciseToBeImported.getCourseViaExerciseGroupOrCourseMember().getId(), course2.getId());
        assertEquals(ExerciseMode.INDIVIDUAL, exerciseToBeImported.getMode());
        assertNull(exerciseToBeImported.getTeamAssignmentConfig());
        assertEquals(0, teamRepository.findAllByExerciseIdWithEagerStudents(exerciseToBeImported, null).size());

        sourceExercise = modelingExerciseRepository.findById(sourceExercise.getId()).get();
        assertEquals(course1.getId(), sourceExercise.getCourseViaExerciseGroupOrCourseMember().getId());
        assertEquals(ExerciseMode.TEAM, sourceExercise.getMode());
        assertEquals(1, teamRepository.findAllByExerciseIdWithEagerStudents(sourceExercise, null).size());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResult() throws Exception {
        final Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);

        ModelingPlagiarismResult expectedResult = database.createModelingPlagiarismResultForExercise(modelingExercise);

        ModelingPlagiarismResult result = request.get("/api/modeling-exercises/" + modelingExercise.getId() + "/plagiarism-result", HttpStatus.OK, ModelingPlagiarismResult.class);
        assertThat(result.getId()).isEqualTo(expectedResult.getId());
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResultWithoutResult() throws Exception {
        final Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);
        var result = request.get("/api/modeling-exercises/" + modelingExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testGetPlagiarismResultWithoutExercise() throws Exception {
        ModelingPlagiarismResult result = request.get("/api/modeling-exercises/" + 1 + "/plagiarism-result", HttpStatus.NOT_FOUND, ModelingPlagiarismResult.class);
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateAndUpdateModelingExercise() throws Exception {

        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(classExercise, "instructor1");

        // change grading instruction score
        gradingCriteria.get(0).getStructuredGradingInstructions().get(0).setCredits(3);
        gradingCriteria.remove(1);
        classExercise.setGradingCriteria(gradingCriteria);

        ModelingExercise updatedModelingExercise = request.putWithResponseBody("/api/modeling-exercises/" + classExercise.getId() + "/re-evaluate" + "?deleteFeedback=false",
                classExercise, ModelingExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedModelingExercise);
        assertThat(updatedModelingExercise.getGradingCriteria().get(0).getStructuredGradingInstructions().get(0).getCredits()).isEqualTo(3);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(60);
        assertThat(updatedResults.get(0).getFeedbacks().get(0).getCredits()).isEqualTo(3);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateAndUpdateModelingExercise_shouldDeleteFeedbacks() throws Exception {
        List<GradingCriterion> gradingCriteria = database.addGradingInstructionsToExercise(classExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);

        database.addAssessmentWithFeedbackWithGradingInstructionsForExercise(classExercise, "instructor1");

        // remove instruction which is associated with feedbacks
        gradingCriteria.remove(1);
        gradingCriteria.remove(0);
        classExercise.setGradingCriteria(gradingCriteria);

        ModelingExercise updatedModelingExercise = request.putWithResponseBody("/api/modeling-exercises/" + classExercise.getId() + "/re-evaluate" + "?deleteFeedback=true",
                classExercise, ModelingExercise.class, HttpStatus.OK);
        List<Result> updatedResults = database.getResultsForExercise(updatedModelingExercise);
        assertThat(updatedModelingExercise.getGradingCriteria().size()).isEqualTo(1);
        assertThat(updatedResults.get(0).getScore()).isEqualTo(0);
        assertThat(updatedResults.get(0).getFeedbacks()).isEmpty();
    }

    @Test
    @WithMockUser(username = "instructor2", roles = "INSTRUCTOR")
    public void testReEvaluateAndUpdateModelingExercise_isNotAtLeastInstructorInCourse_forbidden() throws Exception {
        Course course = database.addCourseWithOneModelingExercise();
        classExercise = (ModelingExercise) course.getExercises().iterator().next();
        course.setInstructorGroupName("test");
        courseRepo.save(course);
        request.put("/api/modeling-exercises/" + classExercise.getId() + "/re-evaluate", classExercise, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateAndUpdateModelingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        ModelingExercise modelingExerciseToBeConflicted = modelingExerciseRepository.findByIdElseThrow(classExercise.getId());
        modelingExerciseToBeConflicted.setId(123456789L);
        modelingExerciseRepository.save(modelingExerciseToBeConflicted);

        request.put("/api/modeling-exercises/" + classExercise.getId() + "/re-evaluate", modelingExerciseToBeConflicted, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = "instructor1", roles = "INSTRUCTOR")
    public void testReEvaluateAndUpdateModelingExercise_notFound() throws Exception {
        request.put("/api/modeling-exercises/" + 123456789 + "/re-evaluate", classExercise, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteModelingExerciseClustersAndElementsAsAdmin() throws Exception {
        final Course course = database.addCourseWithOneModelingExercise();
        ModelingExercise modelingExercise = modelingExerciseRepository.findByCourseId(course.getId()).get(0);
        request.delete("/api/modeling-exercises/" + modelingExercise.getId() + "/clusters", HttpStatus.OK);
    }
}
