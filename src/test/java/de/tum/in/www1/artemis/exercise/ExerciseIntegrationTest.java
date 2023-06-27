package de.tum.in.www1.artemis.exercise;

import static org.assertj.core.api.Assertions.*;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.in.www1.artemis.AbstractSpringIntegrationBambooBitbucketJiraTest;
import de.tum.in.www1.artemis.course.CourseUtilService;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.*;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.participation.TutorParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.exam.ExamUtilService;
import de.tum.in.www1.artemis.exercise.modelingexercise.ModelingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.programmingexercise.ProgrammingExerciseUtilService;
import de.tum.in.www1.artemis.exercise.textexercise.TextExerciseUtilService;
import de.tum.in.www1.artemis.participation.ParticipationUtilService;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.user.UserUtilService;
import de.tum.in.www1.artemis.util.FileUtils;
import de.tum.in.www1.artemis.web.rest.dto.StatsForDashboardDTO;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

class ExerciseIntegrationTest extends AbstractSpringIntegrationBambooBitbucketJiraTest {

    private static final String TEST_PREFIX = "exerciseintegration";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ParticipationRepository participationRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TutorParticipationRepository tutorParticipationRepo;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, NUMBER_OF_TUTORS, 0, 1);

        // Add users that are not in exercise/course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student11");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboardWithSubmissions() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course = courses.get(0);
        TextExercise textExercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        List<Submission> submissions = new ArrayList<>();

        userUtilService.addStudents(TEST_PREFIX, 4, 7);
        for (int i = 1; i <= 6; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(ZonedDateTime.now());
            submissions.add(participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student" + (i + 1))); // student1 was already used
            if (i % 3 == 0) {
                participationUtilService.addResultToSubmission(textSubmission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
            }
            else if (i % 4 == 0) {
                participationUtilService.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
            }
        }
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isEqualTo(submissions.size() + 1);
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments().inTime()).isEqualTo(3);
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isEqualTo(1);

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExamExerciseAssessmentDashboard() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user);
        course = courseRepository.findByIdWithEagerExercisesElseThrow(course.getId());
        var exam = examRepository.findByCourseId(course.getId()).get(0);
        var textExercise = examRepository.findAllExercisesByExamId(exam.getId()).stream().filter(ex -> ex instanceof TextExercise).findFirst().orElseThrow();
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isZero();
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments().inTime()).isZero();
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isZero();

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFilterOutExercisesThatUserShouldNotSee() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> exerciseService.findOneWithDetailsForStudents(Long.MAX_VALUE, userUtilService.getUserByLogin(TEST_PREFIX + "student1")));
        var course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS).get(0); // the course with exercises
        var exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        var student = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(Set.of(), student)).isEmpty();
        var exercise = exercises.iterator().next();
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).hasSize(exercises.size() - 1);

        var tutor = userRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "tutor1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), tutor)).hasSize(exercises.size());

        course.setOnlineCourse(true);
        courseRepository.save(course);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).isEmpty();

        var additionalCourses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        var exercisesFromMultipleCourses = course.getExercises();
        for (var additionalCourse : additionalCourses) {
            exercisesFromMultipleCourses.addAll(additionalCourse.getExercises());
        }
        assertThatIllegalArgumentException().isThrownBy(() -> exerciseService.filterOutExercisesThatUserShouldNotSee(exercisesFromMultipleCourses, student));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExercise() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseServer = request.get("/api/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);

                // Test that certain properties were set correctly
                assertThat(exerciseServer.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseServer.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseServer.getMaxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseServer.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test that certain properties were filtered out as the test user is a student
                assertThat(exerciseServer.getGradingInstructions()).as("Grading instructions were filtered out").isNull();
                assertThat(exerciseServer.getTutorParticipations()).as("Tutor participations not included").isEmpty();
                assertThat(exerciseServer.getExampleSubmissions()).as("Example submissions not included").isEmpty();

                // Test presence and absence of exercise type specific properties
                if (exerciseServer instanceof FileUploadExercise fileUploadExercise) {
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    assertThat(fileUploadExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                }
                else if (exerciseServer instanceof ModelingExercise modelingExercise) {
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    assertThat(modelingExercise.getExampleSolutionModel()).as("Sample solution model was filtered out").isNull();
                    assertThat(modelingExercise.getExampleSolutionExplanation()).as("Sample solution explanation was filtered out").isNull();
                }
                else if (exerciseServer instanceof ProgrammingExercise programmingExerciseExercise) {
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                    assertThat(programmingExerciseExercise.getTemplateRepositoryUrl()).as("Template repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionRepositoryUrl()).as("Solution repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTestRepositoryUrl()).as("Test repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTemplateBuildPlanId()).as("Template build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionBuildPlanId()).as("Solution build plan was filtered out").isNull();
                }
                else if (exerciseServer instanceof QuizExercise quizExercise) {
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    assertThat(quizExercise.getQuizPointStatistic()).as("Quiz point statistic was filtered out").isNull();
                    assertThat(quizExercise.getQuizQuestions()).as("Quiz questions were filtered out").isEmpty();
                }
                else if (exerciseServer instanceof TextExercise textExercise) {
                    assertThat(textExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                }

                // Test that the exercise does not have more than one participation.
                assertThat(exerciseServer.getStudentParticipations()).as("At most one participation for exercise").hasSizeLessThanOrEqualTo(1);
                if (!exerciseServer.getStudentParticipations().isEmpty()) {
                    // Buffer participation so that null checking is easier.
                    Participation participation = exerciseServer.getStudentParticipations().iterator().next();
                    if (!participation.getSubmissions().isEmpty()) {
                        // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                        assertThat(participation.getSubmissions()).as("At most one submission for participation").hasSizeLessThanOrEqualTo(1);
                        Submission submission = participation.getSubmissions().iterator().next();
                        if (submission != null) {
                            // Test that the correct text submission was filtered.
                            if (submission instanceof TextSubmission textSubmission) {
                                assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");
                            }
                            // Test that the correct modeling submission was filtered.
                            else if (submission instanceof ModelingSubmission modelingSubmission) {
                                assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model2");
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExamExercise_asStudent_forbidden() throws Exception {
        getExamExercise();
    }

    private void getExamExercise() throws Exception {
        TextExercise textExercise = textExerciseUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        request.get("/api/exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, Exercise.class);
        request.get("/api/exercises/" + textExercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetUpcomingExercises() throws Exception {
        var now = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<Exercise> exercises = request.getList("/api/admin/exercises/upcoming", HttpStatus.OK, Exercise.class);
        for (var exercise : exercises) {
            assertThat(exercise.getDueDate()).isAfterOrEqualTo(now);
        }
        var size = exercises.size();

        // Test for exercise with upcoming due date.
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = course.getExercises().iterator().next();
        assertThat(exercise.getDueDate()).isAfterOrEqualTo(now);
        exercises = request.getList("/api/admin/exercises/upcoming", HttpStatus.OK, Exercise.class);
        assertThat(exercises).hasSize(size + 1).contains(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetUpcomingExercisesAsStudentForbidden() throws Exception {
        request.getList("/api/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetUpcomingExercisesAsInstructorForbidden() throws Exception {
        request.getList("/api/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetUpcomingExercisesAsTutorForbidden() throws Exception {
        request.getList("/api/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);

                if (exerciseWithDetails instanceof FileUploadExercise fileUploadExercise) {
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    assertThat(fileUploadExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(fileUploadExercise.getStudentParticipations()).as("Number of participations is correct").isEmpty();
                }
                else if (exerciseWithDetails instanceof ModelingExercise modelingExercise) {
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    assertThat(modelingExercise.getExampleSolutionModel()).as("Sample solution model was filtered out").isNull();
                    assertThat(modelingExercise.getExampleSolutionExplanation()).as("Sample solution explanation was filtered out").isNull();
                    assertThat(modelingExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(2);
                }
                else if (exerciseWithDetails instanceof ProgrammingExercise programmingExerciseExercise) {
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                    assertThat(programmingExerciseExercise.getTemplateRepositoryUrl()).as("Template repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionRepositoryUrl()).as("Solution repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTestRepositoryUrl()).as("Test repository url was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getTemplateBuildPlanId()).as("Template build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getSolutionBuildPlanId()).as("Solution build plan was filtered out").isNull();
                    assertThat(programmingExerciseExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(2);
                }
                else if (exerciseWithDetails instanceof QuizExercise quizExercise) {
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    assertThat(quizExercise.getQuizPointStatistic()).as("Quiz point statistic was filtered out").isNull();
                    assertThat(quizExercise.getQuizQuestions()).as("Quiz questions were filtered out").isEmpty();
                    assertThat(quizExercise.getStudentParticipations()).as("Number of participations is correct").isEmpty();
                }
                else if (exerciseWithDetails instanceof TextExercise textExercise) {
                    assertThat(textExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(textExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseExerciseForExampleSolution() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        ZonedDateTime now = ZonedDateTime.now();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {

                request.get("/api/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, Exercise.class);

                exercise.setExampleSolutionPublicationDate(now.minusHours(1));
                exerciseRepository.save(exercise);

                Exercise exerciseForExampleSolution = request.get("/api/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForExampleSolution.getExampleSolutionPublicationDate()).isBeforeOrEqualTo(now);
                if (exerciseForExampleSolution instanceof FileUploadExercise fileUploadExercise) {
                    assertThat(fileUploadExercise.getExampleSolution()).isEqualTo("Example Solution");
                }
                else if (exerciseForExampleSolution instanceof ModelingExercise modelingExercise) {
                    assertThat(modelingExercise.getExampleSolutionModel()).isEqualTo("Example solution model");
                    assertThat(modelingExercise.getExampleSolutionExplanation()).isEqualTo("Example Solution");
                }
                else if (exerciseForExampleSolution instanceof TextExercise textExercise) {
                    assertThat(textExercise.getExampleSolution()).isEqualTo("Example Solution");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "TA")
    void testGetExamExerciseForExampleSolution() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(user);
        Exam exam = course.getExams().stream().findFirst().orElseThrow();
        exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        TextExercise exercise = (TextExercise) exam.getExerciseGroups().get(0).getExercises().stream().findFirst().orElseThrow();
        request.get("/api/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, Exercise.class);

        ZonedDateTime now = ZonedDateTime.now();
        exam.setExampleSolutionPublicationDate(now.minusHours(1));
        examUtilService.addStudentExamWithUser(exam, user);
        examRepository.save(exam);

        TextExercise exerciseForExampleSolution = request.get("/api/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK, TextExercise.class);

        assertThat(exerciseForExampleSolution.getExampleSolution()).isEqualTo("This is my example solution");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_assessmentDueDate_notPassed() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                participationUtilService.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L),
                        exercise.getStudentParticipations().iterator().next());
            }
            Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
            for (StudentParticipation participation : exerciseWithDetails.getStudentParticipations()) {
                // Programming exercises should only have one automatic result
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getResults()).hasSize(1);
                    assertThat(participation.getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
                }
                else {
                    // All other exercises should not display a result at all
                    assertThat(participation.getResults()).isEmpty();
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_assessmentDueDate_passed() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether this is correctly displayed after the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                participationUtilService.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L),
                        exercise.getStudentParticipations().iterator().next());
            }
            Exercise exerciseWithDetails = request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
            for (StudentParticipation participation : exerciseWithDetails.getStudentParticipations()) {
                // Programming exercises should now how two results and the latest one is the manual result.
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(participation.getResults()).hasSize(2);
                    assertThat(participation.getResults().stream().sorted(Comparator.comparing(Result::getId).reversed()).iterator().next().getAssessmentType())
                            .isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                }
                else {
                    // All other exercises have only one visible result now
                    assertThat(participation.getResults()).hasSize(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_withExamExercise_asStudent() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseDetails_withExamExercise_asTutor() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.OK, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void filterForCourseDashboard_assessmentDueDate_notPassed() {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                exercise.getStudentParticipations().iterator().next().setResults(Set.of(participationUtilService.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC,
                        ZonedDateTime.now().minusHours(1L), exercise.getStudentParticipations().iterator().next())));
            }
            exerciseService.filterForCourseDashboard(exercise, List.copyOf(exercise.getStudentParticipations()), "student1", true);
            // Programming exercises should only have one automatic result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(exercise.getStudentParticipations().iterator().next().getResults()).hasSize(1);
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
            }
            else {
                // All other exercises have only one visible result now
                assertThat(exercise.getStudentParticipations().iterator().next().getResults()).isEmpty();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void filterForCourseDashboard_assessmentDueDate_passed() {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether this is correctly displayed after the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                Result result = participationUtilService.addResultToParticipation(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L),
                        exercise.getStudentParticipations().iterator().next());
                exercise.getStudentParticipations().iterator().next().setResults(Set.of(result));
                exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().setResults(new ArrayList<>());
                exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next().addResult(result);
            }
            exerciseService.filterForCourseDashboard(exercise, List.copyOf(exercise.getStudentParticipations()), "student1", true);
            // All exercises have one result
            assertThat(exercise.getStudentParticipations().iterator().next().getResults()).hasSize(1);
            // Programming exercises should now have one manual result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(exercise.getStudentParticipations().iterator().next().getResults().iterator().next().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExercise_forbidden() throws Exception {
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        var exercise = exerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        request.get("/api/exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, Exercise.class);
        request.get("/api/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseForAssessmentDashboard = request.get("/api/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForAssessmentDashboard.getTutorParticipations()).as("Tutor participation was created").hasSize(1);
                assertThat(exerciseForAssessmentDashboard.getExampleSubmissions()).as("Example submissions are not null").isEmpty();

                // Test that certain properties were set correctly
                assertThat(exerciseForAssessmentDashboard.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getMaxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseForAssessmentDashboard.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test presence of exercise type specific properties
                if (exerciseForAssessmentDashboard instanceof FileUploadExercise fileUploadExercise) {
                    assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                }
                else if (exerciseForAssessmentDashboard instanceof ModelingExercise modelingExercise) {
                    assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                }
                else if (exerciseForAssessmentDashboard instanceof ProgrammingExercise programmingExerciseExercise) {
                    assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                }
                else if (exerciseForAssessmentDashboard instanceof QuizExercise quizExercise) {
                    assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(10);
                    assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_submissionsWithoutAssessments() throws Exception {
        var validModel = FileUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        var course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        var exercise = exerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        var exampleSubmission = participationUtilService.generateExampleSubmission(validModel, exercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        Exercise receivedExercise = request.get("/api/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, Exercise.class);
        assertThat(receivedExercise.getExampleSubmissions()).as("Example submission without assessment is removed from exercise").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExerciseForAssessmentDashboard_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.get("/api/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_programmingExerciseWithAutomaticAssessment() throws Exception {
        var exercise = programmingExerciseUtilService.addCourseWithOneProgrammingExercise().getExercises().iterator().next();
        request.get("/api/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.BAD_REQUEST, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_exerciseWithTutorParticipation() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        var tutorParticipation = new TutorParticipation().tutor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1")).assessedExercise(exercise)
                .status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationRepo.save(tutorParticipation);
        var textExercise = request.get("/api/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, TextExercise.class);
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
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            var tutors = findTutors(course);
            for (Exercise exercise : course.getExercises()) {
                StatsForDashboardDTO stats = request.get("/api/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
                assertThat(stats.getTotalNumberOfAssessments().inTime()).as("Number of in-time assessments is correct").isZero();
                assertThat(stats.getTotalNumberOfAssessments().late()).as("Number of late assessments is correct").isZero();

                assertThat(stats.getTutorLeaderboardEntries()).as("Number of tutor leaderboard entries is correct").hasSameSizeAs(tutors);
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfAssessmentLocks()).as("Number of assessment locks are not available for exercises").isNull();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for file upload exercise is correct").isZero();
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for modeling exercise is correct").isEqualTo(2);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for programming exercise is correct").isEqualTo(1);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for quiz exercise is correct").isZero();
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for text exercise is correct").isEqualTo(1);
                }

                assertThat(stats.getNumberOfSubmissions().late()).as("Number of late submissions for exercise is correct").isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboard_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.get("/api/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExercise() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercises/" + exercise.getId() + "/reset", HttpStatus.OK);
                assertThat(exercise.getStudentParticipations()).as("Student participations have been deleted").isEmpty();
                assertThat(exercise.getTutorParticipations()).as("Tutor participations have been deleted").isEmpty();
                assertThat(participationRepository.findWithIndividualDueDateByExerciseId(exercise.getId())).isEmpty();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testResetExercise_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.delete("/api/exercises/" + exercise.getId() + "/reset", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagEnable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];

        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagDisable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setSecondCorrectionEnabled(true);
        exerciseRepository.save(exercise);
        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testSetSecondCorrectionEnabledFlagForbidden() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        request.putWithResponseBody("/api/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExerciseTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
    }

    private void testGetExerciseTitle() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setTitle("Test Exercise");
        exerciseRepository.save(exercise);

        final var title = request.get("/api/exercises/" + exercise.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exercise.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExerciseTitleForNonExistingExercise() throws Exception {
        request.get("/api/exercises/12312321321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        StudentParticipation studentParticipation2 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
        StudentParticipation studentParticipation3 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student3");

        studentParticipation2.setIndividualDueDate(exercise.getDueDate().plusHours(2));
        studentParticipation3.setIndividualDueDate(exercise.getDueDate().plusHours(4));
        participationRepository.save(studentParticipation2);
        participationRepository.save(studentParticipation3);

        ZonedDateTime latestDueDate = request.get("/api/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(studentParticipation3.getIndividualDueDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDateWhenNoIndividualDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");

        ZonedDateTime latestDueDate = request.get("/api/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(exercise.getDueDate(), within(1, ChronoUnit.SECONDS));
    }
}
