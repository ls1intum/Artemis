package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.util.ModelFactory;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;

public class ExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    @Autowired
    UserRepository userRepository;

    @Autowired
    CourseRepository courseRepository;

    @Autowired
    ExerciseRepository exerciseRepository;

    @Autowired
    ParticipationRepository participationRepository;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    ResultRepository resultRepository;

    @Autowired
    ExampleSubmissionRepository exampleSubmissionRepo;

    @Autowired
    TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    StudentParticipationRepository studentParticipationRepo;

    @Autowired
    ExerciseService exerciseService;

    @BeforeEach
    public void init() {
        database.addUsers(10, 5, 1);

        // Add users that are not in exercise/course
        userRepository.save(ModelFactory.generateActivatedUser("student11"));
        userRepository.save(ModelFactory.generateActivatedUser("tutor6"));
        userRepository.save(ModelFactory.generateActivatedUser("instructor2"));
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForExerciseAssessmentDashboardTest() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        Course course = courses.get(0);
        TextExercise textExercise = (TextExercise) course.getExercises().stream().filter(e -> e instanceof TextExercise).findFirst().get();
        List<Submission> submissions = new ArrayList<>();
        for (int i = 1; i <= 6; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(ZonedDateTime.now());
            submissions.add(database.addSubmission(textExercise, textSubmission, "student" + (i + 1))); // student1 was already used
            if (i % 3 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.MANUAL, database.getUserByLogin("instructor1"));
            }
            else if (i % 4 == 0) {
                database.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, database.getUserByLogin("instructor1"));
            }
        }
        StatsForInstructorDashboardDTO statsForInstructorDashboardDTO = request.get("/api/exercises/" + textExercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                StatsForInstructorDashboardDTO.class);
        assertThat(statsForInstructorDashboardDTO.getNumberOfSubmissions().getInTime()).isEqualTo(submissions.size() + 1);
        assertThat(statsForInstructorDashboardDTO.getTotalNumberOfAssessments().getInTime()).isEqualTo(3);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAutomaticAssistedAssessments().getInTime()).isEqualTo(1);

        for (Exercise exercise : course.getExercises()) {
            StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                    StatsForInstructorDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isEqualTo(0);
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isEqualTo(0);
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseServer = request.get("/api/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);

                // Test that certain properties were set correctly
                assertThat(exerciseServer.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseServer.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseServer.getMaxScore()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseServer.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test that certain properties were filtered out as the test user is a student
                assertThat(exerciseServer.getGradingInstructions()).as("Grading instructions were filtered out").isNull();
                assertThat(exerciseServer.getTutorParticipations().size()).as("Tutor participations not included").isZero();
                assertThat(exerciseServer.getExampleSubmissions().size()).as("Example submissions not included").isZero();

                // Test presence and absence of exercise type specific properties
                if (exerciseServer instanceof FileUploadExercise) {
                    FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseServer;
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    assertThat(fileUploadExercise.getSampleSolution()).as("Sample solution was set correctly").isNotNull();
                }
                if (exerciseServer instanceof ModelingExercise) {
                    ModelingExercise modelingExercise = (ModelingExercise) exerciseServer;
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    assertThat(modelingExercise.getSampleSolutionModel()).as("Sample solution model was filtered out").isNull();
                    assertThat(modelingExercise.getSampleSolutionExplanation()).as("Sample solution explanation was filtered out").isNull();
                }
                if (exerciseServer instanceof ProgrammingExercise) {
                    ProgrammingExercise programmingExerciseExercise = (ProgrammingExercise) exerciseServer;
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                    assertThat(programmingExerciseExercise.getTemplateRepositoryUrl()).as("Template repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionRepositoryUrl()).as("Solution repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTestRepositoryUrl()).as("Test repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTemplateBuildPlanId()).as("Template build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionBuildPlanId()).as("Solution build plan was filtered out").isNull();
                }
                if (exerciseServer instanceof QuizExercise) {
                    QuizExercise quizExercise = (QuizExercise) exerciseServer;
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    assertThat(quizExercise.getQuizPointStatistic()).as("Quiz point statistic was filtered out").isNull();
                    assertThat(quizExercise.getQuizQuestions().size()).as("Quiz questions were filtered out").isZero();
                }
                if (exerciseServer instanceof TextExercise) {
                    TextExercise textExercise = (TextExercise) exerciseServer;
                    assertThat(textExercise.getSampleSolution()).as("Sample solution was filtered out").isNull();
                }

                // Test that the exercise does not have more than one participation.
                assertThat(exerciseServer.getStudentParticipations().size()).as("At most one participation for exercise").isLessThanOrEqualTo(1);
                if (exerciseServer.getStudentParticipations().size() > 0) {
                    // Buffer participation so that null checking is easier.
                    Participation participation = exerciseServer.getStudentParticipations().iterator().next();
                    if (participation.getSubmissions().size() > 0) {
                        // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                        assertThat(participation.getSubmissions().size()).as("At most one submission for participation").isLessThanOrEqualTo(1);
                        Submission submission = participation.getSubmissions().iterator().next();
                        if (submission != null) {
                            // Test that the correct text submission was filtered.
                            if (submission instanceof TextSubmission) {
                                TextSubmission textSubmission = (TextSubmission) submission;
                                assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                            }
                            // Test that the correct modeling submission was filtered.
                            if (submission instanceof ModelingSubmission) {
                                ModelingSubmission modelingSubmission = (ModelingSubmission) submission;
                                assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model2");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "student11", roles = "USER")
    public void testGetExercise_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId(), HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "student11", roles = "USER")
    public void testGetExamExercise_asStudent_forbidden() throws Exception {
        getExamExercise();
    }

    private void getExamExercise() throws Exception {
        TextExercise textExercise = database.addCourseExamExerciseGroupWithOneTextExercise();
        request.get("/api/exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, Exercise.class);
        request.get("/api/exercises/" + textExercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "admin", roles = "ADMIN")
    public void testGetUpcomingExercises() throws Exception {
        List<Exercise> exercises = request.getList("/api/exercises/upcoming", HttpStatus.OK, Exercise.class);
        assertThat(exercises.size()).isEqualTo(0);

        // Test for exercise with upcoming due date.
        Course course = database.addCourseWithOneProgrammingExercise();
        exercises = request.getList("/api/exercises/upcoming", HttpStatus.OK, Exercise.class);
        assertThat(exercises.size()).isEqualTo(1);
        assertThat(exercises).contains(course.getExercises().stream().findFirst().get());
    }

    @Test
    @WithMockUser(value = "student11", roles = "USER")
    public void testGetUpcomingExercisesAsStudentForbidden() throws Exception {
        request.getList("/api/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "instructor2", roles = "INSTRUCTOR")
    public void testGetUpcomingExercisesAsInstructorForbidden() throws Exception {
        request.getList("/api/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "tutor6", roles = "TA")
    public void testGetUpcomingExercisesAsTutorForbidden() throws Exception {
        request.getList("/api/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExerciseDetails() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);

                if (exerciseWithDetails instanceof FileUploadExercise) {
                    FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseWithDetails;
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    assertThat(fileUploadExercise.getSampleSolution()).as("Sample solution was set correctly").isNotNull();
                    assertThat(fileUploadExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(0);
                }
                if (exerciseWithDetails instanceof ModelingExercise) {
                    ModelingExercise modelingExercise = (ModelingExercise) exerciseWithDetails;
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    assertThat(modelingExercise.getSampleSolutionModel()).as("Sample solution model was filtered out").isNull();
                    assertThat(modelingExercise.getSampleSolutionExplanation()).as("Sample solution explanation was filtered out").isNull();
                    assertThat(modelingExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(2);
                }
                if (exerciseWithDetails instanceof ProgrammingExercise) {
                    ProgrammingExercise programmingExerciseExercise = (ProgrammingExercise) exerciseWithDetails;
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                    assertThat(programmingExerciseExercise.getTemplateRepositoryUrl()).as("Template repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionRepositoryUrl()).as("Solution repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTestRepositoryUrl()).as("Test repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTemplateBuildPlanId()).as("Template build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionBuildPlanId()).as("Solution build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(0);
                }
                if (exerciseWithDetails instanceof QuizExercise) {
                    QuizExercise quizExercise = (QuizExercise) exerciseWithDetails;
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    assertThat(quizExercise.getQuizPointStatistic()).as("Quiz point statistic was filtered out").isNull();
                    assertThat(quizExercise.getQuizQuestions().size()).as("Quiz questions were filtered out").isZero();
                    assertThat(quizExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(0);
                }
                if (exerciseWithDetails instanceof TextExercise) {
                    TextExercise textExercise = (TextExercise) exerciseWithDetails;
                    assertThat(textExercise.getSampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(textExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExerciseDetails_assessmentDueDate_notPassed() throws Exception {
        Course course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L), exercise.getStudentParticipations().iterator().next());
            }
            Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
            for (StudentParticipation participation : exerciseWithDetails.getStudentParticipations()) {
                // Programming exercises should only have one automatic result
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getResults().size()).isEqualTo(1);
                    assertThat(participation.getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
                }
                // Quiz exercises should only have one automatic result
                else if (exercise instanceof QuizExercise) {
                    assertThat(participation.getResults().size()).isEqualTo(1);
                }
                else {
                    // All other exercises should not display a result at all
                    assertThat(participation.getResults().size()).isEqualTo(0);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExerciseDetails_assessmentDueDate_passed() throws Exception {
        Course course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add an manual result, to check whether this is correctly displayed after the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L), exercise.getStudentParticipations().iterator().next());
            }
            Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
            for (StudentParticipation participation : exerciseWithDetails.getStudentParticipations()) {
                // Programming exercises should now how two results and the latest one is the manual result.
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getResults().size()).isEqualTo(2);
                    assertThat(participation.getResults().stream().sorted(Comparator.comparing(Result::getId).reversed()).iterator().next().getAssessmentType())
                            .isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                }
                else {
                    // All other exercises have only one visible result now
                    assertThat(participation.getResults().size()).isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void filterForCourseDashboard_assessmentDueDate_notPassed() {
        Course course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                exercise.getStudentParticipations().iterator().next().setResults(Set.of(database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC,
                        ZonedDateTime.now().minusHours(1L), exercise.getStudentParticipations().iterator().next())));
            }
            exerciseService.filterForCourseDashboard(exercise, List.copyOf(exercise.getStudentParticipations()), "student1", true);
            // Programming exercises should only have one automatic result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().size()).isEqualTo(1);
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            }
            else if (exercise instanceof QuizExercise) {
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().size()).isEqualTo(1);
            }
            else {
                // All other exercises have only one visible result now
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().size()).isEqualTo(0);
            }
        }
    }

    @Test
    @WithMockUser(value = "student1", roles = "USER")
    public void filterForCourseDashboard_assessmentDueDate_passed() {
        Course course = database.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add an manual result, to check whether this is correctly displayed after the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                Result result = database.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L),
                        exercise.getStudentParticipations().iterator().next());
                exercise.getStudentParticipations().iterator().next().setResults(Set.of(result));
                exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().setResults(new ArrayList<>());
                exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().addResult(result);
            }
            exerciseService.filterForCourseDashboard(exercise, List.copyOf(exercise.getStudentParticipations()), "student1", true);
            // All exercises have one result
            assertThat(exercise.getStudentParticipations().iterator().next().getResults().size()).isEqualTo(1);
            // Programming exercises should now have one manual result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
            }
        }
    }

    @Test
    @WithMockUser(value = "student11", roles = "USER")
    public void testGetExerciseDetails_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForAssessmentDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseForAssessmentDashboard = request.get("/api/exercises/" + exercise.getId() + "/for-tutor-dashboard", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForAssessmentDashboard.getTutorParticipations().size()).as("Tutor participation was created").isEqualTo(1);
                assertThat(exerciseForAssessmentDashboard.getExampleSubmissions().size()).as("Example submissions are not null").isZero();

                // Test that certain properties were set correctly
                assertThat(exerciseForAssessmentDashboard.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getMaxScore()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseForAssessmentDashboard.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test presence of exercise type specific properties
                if (exerciseForAssessmentDashboard instanceof FileUploadExercise) {
                    FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseForAssessmentDashboard;
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                }
                if (exerciseForAssessmentDashboard instanceof ModelingExercise) {
                    ModelingExercise modelingExercise = (ModelingExercise) exerciseForAssessmentDashboard;
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                }
                if (exerciseForAssessmentDashboard instanceof ProgrammingExercise) {
                    ProgrammingExercise programmingExerciseExercise = (ProgrammingExercise) exerciseForAssessmentDashboard;
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                }
                if (exerciseForAssessmentDashboard instanceof QuizExercise) {
                    QuizExercise quizExercise = (QuizExercise) exerciseForAssessmentDashboard;
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForAssessmentDashboard_submissionsWithoutAssessments() throws Exception {
        var validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        database.addCourseWithOneModelingExercise();
        var exercise = exerciseRepository.findAll().get(0);
        var exampleSubmission = database.generateExampleSubmission(validModel, exercise, true);
        database.addExampleSubmission(exampleSubmission);
        Exercise receivedExercise = request.get("/api/exercises/" + exercise.getId() + "/for-tutor-dashboard", HttpStatus.OK, Exercise.class);
        assertThat(receivedExercise.getExampleSubmissions()).as("Example submission without assessment is removed from exercise").isEmpty();
    }

    @Test
    @WithMockUser(value = "tutor6", roles = "TA")
    public void testGetExerciseForAssessmentDashboard_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/for-tutor-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForAssessmentDashboard_programmingExerciseWithAutomaticAssessment() throws Exception {
        database.addCourseWithOneProgrammingExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/for-tutor-dashboard", HttpStatus.BAD_REQUEST, Exercise.class);
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForAssessmentDashboard_exerciseWithTutorParticipation() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        var exercise = exerciseRepository.findAll().get(0);
        var tutorParticipation = new TutorParticipation().tutor(database.getUserByLogin("tutor1")).assessedExercise(exercise)
                .status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationRepo.save(tutorParticipation);
        var textExercise = request.get("/api/exercises/" + exercise.getId() + "/for-tutor-dashboard", HttpStatus.OK, TextExercise.class);
        assertThat(textExercise.getTutorParticipations().iterator().next().getStatus()).as("Status was changed to trained").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    private List<User> findTutors(Course course) {
        List<User> tutors = new ArrayList<>();
        Page<User> allUsers = userRepository.findAllWithGroups(Pageable.unpaged());
        for (User user : allUsers) {
            if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                tutors.add(user);
            }
        }
        return tutors;
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForExerciseAssessmentDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            var tutors = findTutors(course);
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                assertThat(stats.getTotalNumberOfAssessments().getInTime()).as("Number of in-time assessments is correct").isEqualTo(0);
                assertThat(stats.getTotalNumberOfAssessments().getLate()).as("Number of late assessments is correct").isEqualTo(0);

                assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(tutors.size());
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfAssessmentLocks()).as("Number of assessment locks should be available to tutor").isNotNull();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for file upload exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for modeling exercise is correct").isEqualTo(2);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for programming exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for quiz exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for text exercise is correct").isEqualTo(1);
                }

                assertThat(stats.getNumberOfSubmissions().getLate()).as("Number of late submissions for exercise is correct").isEqualTo(0);
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor6", roles = "TA")
    public void testGetStatsForExerciseAssessmentDashboard_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/stats-for-tutor-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetStatsForInstructorExerciseDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            var tutors = findTutors(course);
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-instructor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                assertThat(stats.getTotalNumberOfAssessments().getInTime()).as("Number of in-time assessments is correct").isEqualTo(0);
                assertThat(stats.getTotalNumberOfAssessments().getLate()).as("Number of late assessments is correct").isEqualTo(0);
                assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(tutors.size());
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints is zero").isZero();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests is zero").isZero();
                assertThat(stats.getNumberOfAssessmentLocks()).as("Number of assessment locks should be available to instructor").isNotNull();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for file upload exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for modeling exercise is correct").isEqualTo(2);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for programming exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for quiz exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions().getInTime()).as("Number of in-time submissions for text exercise is correct").isEqualTo(1);
                }

                assertThat(stats.getNumberOfSubmissions().getLate()).as("Number of late submissions for exercise is correct").isEqualTo(0);
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor2", roles = "INSTRUCTOR")
    public void testGetStatsForInstructorExerciseDashboard_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.get("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/stats-for-instructor-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testResetExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId() + "/reset", HttpStatus.OK);
                assertThat(exercise.getStudentParticipations().size()).as("Student participations have been deleted").isZero();
                assertThat(exercise.getTutorParticipations().size()).as("Tutor participations have been deleted").isZero();
            }
        }
        assertThat(participationRepository.findAll()).hasSize(0);
    }

    @Test
    @WithMockUser(value = "instructor2", roles = "INSTRUCTOR")
    public void testResetExercise_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.delete("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/reset", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCleanupExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures(true);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId() + "/cleanup", HttpStatus.OK);
                if (exercise instanceof ProgrammingExercise) {
                    for (StudentParticipation participation : exercise.getStudentParticipations()) {
                        ProgrammingExerciseStudentParticipation programmingExerciseParticipation = (ProgrammingExerciseStudentParticipation) participation;
                        assertThat(programmingExerciseParticipation.getBuildPlanId()).as("Build plan id has been removed").isNull();
                    }
                }
            }
        }
        // NOTE: for some reason, the cleanup does not work properly in this case.
        // Therefore we have some additional cleanup code here

        tutorParticipationRepo.deleteAll();
        exampleSubmissionRepo.deleteAll();
        resultRepository.deleteAll();
        submissionRepository.deleteAll();
        exerciseRepository.deleteAll();
    }

    @Test
    @WithMockUser(value = "instructor2", roles = "INSTRUCTOR")
    public void testCleanupExercise_forbidden() throws Exception {
        database.addCourseWithOneReleasedTextExercise();
        request.delete("/api/exercises/" + exerciseRepository.findAll().get(0).getId() + "/cleanup", HttpStatus.FORBIDDEN);
    }
}
