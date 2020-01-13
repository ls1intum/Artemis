package de.tum.in.www1.artemis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.AssessmentType;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.util.DatabaseUtilService;
import de.tum.in.www1.artemis.util.RequestUtilService;
import de.tum.in.www1.artemis.web.rest.dto.StatsForInstructorDashboardDTO;

public class ExerciseIntegrationTest extends AbstractSpringIntegrationTest {

    @Autowired
    DatabaseUtilService database;

    @Autowired
    RequestUtilService request;

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

    @BeforeEach
    public void init() {
        database.addUsers(10, 5, 1);
    }

    @AfterEach
    public void tearDown() {
        database.resetDatabase();
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForTutorExerciseDashboardTest() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
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
        assertThat(statsForInstructorDashboardDTO.getNumberOfSubmissions()).isEqualTo(submissions.size() + 1);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAssessments()).isEqualTo(3);
        assertThat(statsForInstructorDashboardDTO.getNumberOfAutomaticAssistedAssessments()).isEqualTo(1);

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
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
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
                    assertThat(fileUploadExercise.getSampleSolution()).as("Sample solution was filtered out").isNull();
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
                    assertThat(quizExercise.getQuizPointStatistic().getId()).as("Quiz point statistic was filtered out").isNull();
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
    @WithMockUser(value = "student1", roles = "USER")
    public void testGetExerciseDetails() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);

                if (exerciseWithDetails instanceof FileUploadExercise) {
                    FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseWithDetails;
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    assertThat(fileUploadExercise.getSampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(fileUploadExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(0);
                }
                if (exerciseWithDetails instanceof ModelingExercise) {
                    ModelingExercise modelingExercise = (ModelingExercise) exerciseWithDetails;
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    assertThat(modelingExercise.getSampleSolutionModel()).as("Sample solution model was filtered out").isNull();
                    assertThat(modelingExercise.getSampleSolutionExplanation()).as("Sample solution explanation was filtered out").isNull();
                    assertThat(modelingExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(1);
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
                    assertThat(quizExercise.getQuizPointStatistic().getId()).as("Quiz point statistic was filtered out").isNull();
                    assertThat(quizExercise.getQuizQuestions().size()).as("Quiz questions were filtered out").isZero();
                    assertThat(quizExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(0);
                }
                if (exerciseWithDetails instanceof TextExercise) {
                    TextExercise textExercise = (TextExercise) exerciseWithDetails;
                    assertThat(textExercise.getSampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(textExercise.getStudentParticipations().size()).as("Number of participations is correct").isEqualTo(2);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetExerciseForTutorDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseForTutorDashboard = request.get("/api/exercises/" + exercise.getId() + "/for-tutor-dashboard", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForTutorDashboard.getTutorParticipations().size()).as("Tutor participation was created").isEqualTo(1);
                assertThat(exerciseForTutorDashboard.getExampleSubmissions().size()).as("Example submissions are not null").isZero();

                // Test that certain properties were set correctly
                assertThat(exerciseForTutorDashboard.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseForTutorDashboard.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseForTutorDashboard.getMaxScore()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseForTutorDashboard.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test presence of exercise type specific properties
                if (exerciseForTutorDashboard instanceof FileUploadExercise) {
                    FileUploadExercise fileUploadExercise = (FileUploadExercise) exerciseForTutorDashboard;
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                }
                if (exerciseForTutorDashboard instanceof ModelingExercise) {
                    ModelingExercise modelingExercise = (ModelingExercise) exerciseForTutorDashboard;
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                }
                if (exerciseForTutorDashboard instanceof ProgrammingExercise) {
                    ProgrammingExercise programmingExerciseExercise = (ProgrammingExercise) exerciseForTutorDashboard;
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                }
                if (exerciseForTutorDashboard instanceof QuizExercise) {
                    QuizExercise quizExercise = (QuizExercise) exerciseForTutorDashboard;
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "tutor1", roles = "TA")
    public void testGetStatsForTutorExerciseDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-tutor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                assertThat(stats.getNumberOfAssessments()).as("Number of assessments is correct").isEqualTo(0);
                assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(5);
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints should not be available to tutor").isNull();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests should not be available to tutor").isNull();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for file upload exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for modeling exercise is correct").isEqualTo(1);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for programming exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for quiz exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for text exercise is correct").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testGetStatsForInstructorExerciseDashboard() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                StatsForInstructorDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-instructor-dashboard", HttpStatus.OK,
                        StatsForInstructorDashboardDTO.class);
                assertThat(stats.getNumberOfAssessments()).as("Number of assessments is correct").isEqualTo(0);
                assertThat(stats.getTutorLeaderboardEntries().size()).as("Number of tutor leaderboard entries is correct").isEqualTo(5);
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints is zero").isZero();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests is zero").isZero();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for file upload exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for modeling exercise is correct").isEqualTo(1);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for programming exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for quiz exercise is correct").isEqualTo(0);
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions()).as("Number of submissions for text exercise is correct").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testResetExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
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
    @WithMockUser(value = "instructor1", roles = "INSTRUCTOR")
    public void testCleanupExercise() throws Exception {
        List<Course> courses = database.createCoursesWithExercisesAndLectures();
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
}
