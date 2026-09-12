package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.UserCourseRole;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.test_repository.UserCourseRoleTestRepository;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.TeamAssignmentConfig;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseManagementStatisticsDTO;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseResponseDTO;
import de.tum.cit.aet.artemis.exercise.dto.UpcomingExerciseDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentBatchTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

class ExerciseIntegrationTest extends AbstractSpringIntegrationIndependentBatchTest {

    private static final String TEST_PREFIX = "exerciseintegration";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private JsonMapper objectMapper;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepo;

    @Autowired
    private ExerciseService exerciseService;

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

    @Autowired
    private UserCourseRoleTestRepository userCourseRoleTestRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    static final int NUMBER_OF_TUTORS = 1;

    private static final String TEST_REPOSITORY_URI = "http://localhost:8080/git/TSTEXC/tstexc-tests.git";

    /**
     * The programming scalars the entity put on the wire before the migration, pinned by value in the contract test.
     */
    private static final List<String> PROGRAMMING_SCALARS = List.of("allowOnlineEditor", "allowOfflineIde", "allowOnlineIde", "staticCodeAnalysisEnabled",
            "maxStaticCodeAnalysisPenalty", "showTestNamesToStudents", "buildAndTestStudentSubmissionsAfterDueDate", "releaseTestsWithExampleSolution", "programmingLanguage",
            "projectType", "packageName", "projectKey", "testRepositoryUri", "testCasesChanged", "defaultTestCaseVisibility");

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, NUMBER_OF_TUTORS, 0, 1);
        userUtilService.addAdmin(TEST_PREFIX);
        // Outsider users (student11, tutor6, instructor2) are created inside the individual
        // test methods that need them to avoid accidental enrollment when other tests create
        // a prefix-enrolled course in their own bodies.
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboardWithSubmissions() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course = courses.getFirst();
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
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
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercise/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isEqualTo(submissions.size() + 1);
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments()).isEqualTo(3);
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isEqualTo(1);

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExamExerciseAssessmentDashboard() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, user);
        course = courseRepository.findByIdWithEagerExercisesElseThrow(course.getId());
        var exam = examRepository.findByCourseId(course.getId()).getFirst();
        var textExercise = examRepository.findAllExercisesWithDetailsByExamId(exam.getId()).stream().filter(ex -> ex instanceof TextExercise).findFirst().orElseThrow();
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercise/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isZero();
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments()).isZero();
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isZero();

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentParticipationCountByIdFiltersNonStudentParticipants() throws Exception {
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1), null);

        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "instructor1");

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("exerciseId", textExercise.getId().toString());
        ExerciseManagementStatisticsDTO statistics = request.get("/api/core/management/statistics/exercise-statistics", HttpStatus.OK, ExerciseManagementStatisticsDTO.class,
                parameters);

        assertThat(statistics.numberOfParticipations()).isEqualTo(1L);
        assertThat(statistics.numberOfStudentsOrTeamsInCourse()).isEqualTo(3L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentParticipationCountByIdFiltersNonStudentParticipantsForExamExercise() throws Exception {
        TextExercise textExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);

        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(textExercise, TEST_PREFIX + "instructor1");

        LinkedMultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
        parameters.add("exerciseId", textExercise.getId().toString());
        ExerciseManagementStatisticsDTO statistics = request.get("/api/core/management/statistics/exercise-statistics", HttpStatus.OK, ExerciseManagementStatisticsDTO.class,
                parameters);

        assertThat(statistics.numberOfParticipations()).isEqualTo(1L);
        assertThat(statistics.numberOfStudentsOrTeamsInCourse()).isEqualTo(3L);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFilterOutExercisesThatUserShouldNotSee() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> exerciseService.findOneWithDetailsForStudents(Long.MAX_VALUE, userUtilService.getUserByLogin(TEST_PREFIX + "student1")));
        var course = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS).getFirst(); // the course with exercises
        var exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        var student = userTestRepository.getUserWithAuthorities(TEST_PREFIX + "student1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(Set.of(), student)).isEmpty();
        var exercise = exercises.iterator().next();
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).hasSize(exercises.size() - 1);

        var tutor = userTestRepository.getUserWithAuthorities(TEST_PREFIX + "tutor1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), tutor)).hasSize(exercises.size());

        course.setOnlineCourse(true);
        courseRepository.save(course);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).isEmpty();

        var additionalCourses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        var exercisesFromMultipleCourses = course.getExercises();
        for (var additionalCourse : additionalCourses) {
            exercisesFromMultipleCourses.addAll(additionalCourse.getExercises());
        }
        assertThatIllegalArgumentException().isThrownBy(() -> exerciseService.filterOutExercisesThatUserShouldNotSee(exercisesFromMultipleCourses, student));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExercise() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                ExerciseResponseDTO exerciseServer = request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.OK, ExerciseResponseDTO.class);

                // Test that certain properties were set correctly
                assertThat(exerciseServer.type()).as("Discriminator is present").isEqualTo(exercise.getType());
                assertThat(exerciseServer.releaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseServer.dueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseServer.maxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseServer.difficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);
                assertThat(exerciseServer.course()).as("Course context is present").isNotNull();
                assertThat(exerciseServer.course().id()).as("Course id was set correctly").isEqualTo(course.getId());

                // Test that certain properties were filtered out as the test user is a student
                assertThat(exerciseServer.gradingInstructions()).as("Grading instructions were filtered out").isNull();
                assertThat(exerciseServer.gradingCriteria()).as("Grading criteria were filtered out").isNull();
                assertThat(exerciseServer.tutorParticipations()).as("Tutor participations not included").isNull();
                assertThat(exerciseServer.exampleSubmissions()).as("Example submissions not included").isNull();

                // Test presence and absence of exercise type specific properties
                switch (exercise) {
                    case FileUploadExercise ignored -> {
                        assertThat(exerciseServer.filePattern()).as("File pattern was set correctly").isEqualTo("png");
                        assertThat(exerciseServer.exampleSolution()).as("Example solution was filtered out").isNull();
                    }
                    case ModelingExercise ignored -> {
                        assertThat(exerciseServer.diagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                        assertThat(exerciseServer.exampleSolutionModel()).as("Example solution model was filtered out").isNull();
                        assertThat(exerciseServer.exampleSolutionExplanation()).as("Example solution explanation was filtered out").isNull();
                    }
                    case ProgrammingExercise ignored -> {
                        assertThat(exerciseServer.projectKey()).as("Project key was set").isNotNull();
                        assertThat(exerciseServer.allowOfflineIde()).as("Offline IDE was set correctly").isTrue();
                        assertThat(exerciseServer.templateParticipation()).as("Template participation not loaded by this endpoint").isNull();
                    }
                    case QuizExercise ignored -> {
                        assertThat(exerciseServer.duration()).as("Duration was set correctly").isEqualTo(120);
                        assertThat(exerciseServer.allowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    }
                    case TextExercise ignored -> assertThat(exerciseServer.exampleSolution()).as("Sample solution was filtered out").isNull();
                    default -> {
                    }
                }
            }
        }
    }

    /**
     * The management header reads {@code categories}, the example assessment editors read {@code gradingCriteria} with
     * their structured instructions, and the team dialog reads {@code teamAssignmentConfig}. All three reach the DTO
     * through initialization guards over a fetch-joined query, so pin them for a tutor here.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExercise_asTutorCarriesCategoriesGradingCriteriaAndTeamAssignmentConfig() throws Exception {
        var course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        exercise.setCategories(new HashSet<>(Set.of("homework", "bonus")));
        exercise.setMode(ExerciseMode.TEAM);
        var teamAssignmentConfig = new TeamAssignmentConfig();
        teamAssignmentConfig.setExercise(exercise);
        teamAssignmentConfig.setMinTeamSize(2);
        teamAssignmentConfig.setMaxTeamSize(4);
        exercise.setTeamAssignmentConfig(teamAssignmentConfig);
        exerciseRepository.save(exercise);
        gradingCriterionRepository.saveAll(exerciseUtilService.addGradingInstructionsToExercise(exercise));

        ExerciseResponseDTO response = request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.OK, ExerciseResponseDTO.class);

        assertThat(response.categories()).as("Categories the management header renders").containsExactlyInAnyOrder("homework", "bonus");
        assertThat(response.teamAssignmentConfig()).as("Team assignment config the team dialog reads").isNotNull();
        assertThat(response.teamAssignmentConfig().minTeamSize()).isEqualTo(2);
        assertThat(response.teamAssignmentConfig().maxTeamSize()).isEqualTo(4);
        assertThat(response.gradingCriteria()).as("Grading criteria the example assessment editors read").isNotEmpty();
        assertThat(response.gradingCriteria()).allSatisfy(criterion -> assertThat(criterion.structuredGradingInstructions()).isNotEmpty());
    }

    /**
     * The exercise-by-id route is reachable with a SCORPIO tool token from a client that cannot be audited in this
     * tree, and used to hand out the {@code Course} entity. The key set of the nested course therefore has to stay the
     * key set the entity serialized. It is compared against the entity itself rather than against a hand-written list,
     * because a list is exactly what silently falls behind when {@code Course} gains a column.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseCarriesTheCourseKeySetTheEntityUsedToSerialize() throws Exception {
        var course = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        // Every nullable scalar needs a value: NON_EMPTY hides an unset one on both sides, which would let a dropped
        // component pass unnoticed.
        course.setDescription("A course description");
        course.setSemester("WS24/25");
        course.setLanguage(Language.ENGLISH);
        course.setDefaultProgrammingLanguage(ProgrammingLanguage.JAVA);
        course.setColor("#691b0b");
        course.setCourseIcon("/api/core/files/course/icons/1/icon.png");
        course.setMaxPoints(42);
        course.setPresentationScore(3);
        course.setTimeZone("Europe/Berlin");
        course.setEnrollmentEnabled(true);
        course.setEnrollmentStartDate(ZonedDateTime.now().minusMonths(3));
        course.setEnrollmentEndDate(ZonedDateTime.now().minusMonths(1));
        course.setUnenrollmentEndDate(ZonedDateTime.now().plusMonths(1));
        course.setEnrollmentConfirmationMessage("Welcome");
        course.setCourseInformationSharingMessagingCodeOfConduct("Be nice");
        course.setCourseArchivePath("Course_archive.zip");
        course.setOnboardingDone(true);
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);

        Map<String, Object> courseNode = mapOf(getJsonMap("/api/exercise/exercises/" + exercise.getId()), "course");

        assertThat(courseNode.keySet()).containsExactlyInAnyOrderElementsOf(serializedKeysOf(courseRepository.findByIdElseThrow(course.getId())));
        assertThat(courseNode.get("description")).isEqualTo("A course description");
        assertThat(courseNode.get("semester")).isEqualTo("WS24/25");
        assertThat(courseNode.get("language")).isEqualTo(Language.ENGLISH.name());
        assertThat(courseNode.get("defaultProgrammingLanguage")).isEqualTo(ProgrammingLanguage.JAVA.name());
        assertThat(courseNode.get("maxPoints")).isEqualTo(42);
        assertThat(courseNode.get("courseArchivePath")).isEqualTo("Course_archive.zip");
        assertThat(courseNode.get("learningPathsEnabled")).isEqualTo(true);
    }

    /**
     * The same route hands an exam exercise its {@code ExerciseGroup} with the {@code Exam}. Both key sets are pinned
     * against the entities for the same reason.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExamExerciseCarriesTheExamKeySetTheEntityUsedToSerialize() throws Exception {
        TextExercise exercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Exam exam = exercise.getExerciseGroup().getExam();
        exam.setExamWithAttendanceCheck(true);
        exam.setNumberOfExercisesInExam(3);
        exam.setExamMaxPoints(90);
        exam.setGracePeriod(120);
        exam.setRandomizeExerciseOrder(true);
        exam.setStartText("Good luck");
        exam.setEndText("Well done");
        exam.setConfirmationStartText("I am ready");
        exam.setConfirmationEndText("I am done");
        exam.setExaminer("Prof. Krusche");
        exam.setModuleNumber("IN0001");
        exam.setCourseName("Introduction to Software Engineering");
        exam.setExamArchivePath("Exam_archive.zip");
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        exam.setExamStudentReviewStart(ZonedDateTime.now().plusDays(2));
        exam.setExamStudentReviewEnd(ZonedDateTime.now().plusDays(3));
        exam.setExamSummaryPublicationDate(ZonedDateTime.now().plusDays(4));
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().plusDays(5));
        exam = examRepository.save(exam);

        Map<String, Object> groupNode = mapOf(getJsonMap("/api/exercise/exercises/" + exercise.getId()), "exerciseGroup");

        assertThat(groupNode.keySet()).containsExactlyInAnyOrder("id", "title", "isMandatory", "exam");
        Map<String, Object> examNode = mapOf(groupNode, "exam");
        assertThat(examNode.keySet()).containsExactlyInAnyOrderElementsOf(serializedKeysOf(examRepository.findByIdElseThrow(exam.getId())));
        assertThat(examNode.get("examWithAttendanceCheck")).isEqualTo(true);
        assertThat(examNode.get("numberOfExercisesInExam")).isEqualTo(3);
        assertThat(examNode.get("examMaxPoints")).isEqualTo(90);
        assertThat(examNode.get("examiner")).isEqualTo("Prof. Krusche");
        assertThat(examNode.get("examArchivePath")).isEqualTo("Exam_archive.zip");
    }

    /**
     * The same route is the one a SCORPIO client reads a programming exercise through, and it used to hand out the
     * {@code ProgrammingExercise} entity. Every programming scalar the entity serialized is pinned here by value, not
     * only by key, against the reloaded entity, because a component that silently turns null passes a key check.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetProgrammingExerciseCarriesTheProgrammingScalarsTheEntityUsedToSerialize() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // Every nullable scalar needs a value: NON_EMPTY hides an unset one on both sides, which would let a dropped
        // component pass unnoticed. The test repository uri is the one a tutor is meant to keep.
        exercise.setTestRepositoryUri(TEST_REPOSITORY_URI);
        exercise.setAllowOnlineEditor(true);
        exercise.setAllowOfflineIde(true);
        exercise.setAllowOnlineIde(true);
        exercise.setStaticCodeAnalysisEnabled(true);
        exercise.setMaxStaticCodeAnalysisPenalty(20);
        exercise.setShowTestNamesToStudents(true);
        exercise.setBuildAndTestStudentSubmissionsAfterDueDate(ZonedDateTime.now().plusDays(1));
        exercise.setReleaseTestsWithExampleSolution(true);
        exercise.setTestCasesChanged(true);
        exercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        exercise.setProjectType(ProjectType.PLAIN_GRADLE);
        exercise.setPackageName("de.tum.cit.ase");
        exerciseRepository.save(exercise);

        Map<String, Object> response = getJsonMap("/api/exercise/exercises/" + exercise.getId());
        // The entity side has to be loaded and filtered exactly as the endpoint did, otherwise a collection the query
        // never fetched shows up as a difference that says nothing about the migration.
        Exercise reloaded = exerciseRepository.findByIdWithCategoriesAndTeamAssignmentConfigElseThrow(exercise.getId());
        reloaded.setGradingCriteria(gradingCriterionRepository.findByExerciseIdWithEagerGradingCriteria(exercise.getId()));
        Map<String, Object> entityJson = objectMapper.convertValue(reloaded, new TypeReference<>() {
        });

        assertThat(response.keySet()).as("Top-level key set the entity used to serialize").containsExactlyInAnyOrderElementsOf(entityJson.keySet());
        assertThat(PROGRAMMING_SCALARS).allSatisfy(key -> {
            assertThat(entityJson.get(key)).as(key + " is serialized by the entity").isNotNull();
            assertThat(response.get(key)).as(key + " on the wire").isEqualTo(entityJson.get(key));
        });
        assertThat(response.get("testRepositoryUri")).isEqualTo(TEST_REPOSITORY_URI);
        assertThat(response.get("defaultTestCaseVisibility")).isEqualTo(Visibility.ALWAYS.name());
    }

    /**
     * The student path runs {@code filterSensitiveInformation}, which clears the test repository uri. NON_EMPTY then
     * drops the key, exactly as the entity dropped it.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetProgrammingExerciseHidesTheTestRepositoryUriFromStudents() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exercise.setTestRepositoryUri(TEST_REPOSITORY_URI);
        exerciseRepository.save(exercise);

        Map<String, Object> response = getJsonMap("/api/exercise/exercises/" + exercise.getId());

        assertThat(response).as("Test repository uri stays hidden from students").doesNotContainKey("testRepositoryUri");
        assertThat(response.get("defaultTestCaseVisibility")).isEqualTo(Visibility.ALWAYS.name());
    }

    /**
     * Reads a response as a plain map, so a key-set assertion sees exactly the keys on the wire.
     *
     * @param url the url to request
     * @return the parsed response
     */
    private Map<String, Object> getJsonMap(String url) throws Exception {
        return objectMapper.readValue(request.get(url, HttpStatus.OK, String.class), new TypeReference<>() {
        });
    }

    /**
     * The keys an entity puts on the wire, read off a detached instance the way the endpoint serialized it: its lazy
     * collections stay uninitialized and NON_EMPTY drops them, exactly as they were dropped before the migration.
     *
     * @param entity the entity to serialize
     * @return its top-level key set
     */
    private Set<String> serializedKeysOf(Object entity) {
        Map<String, Object> json = objectMapper.convertValue(entity, new TypeReference<>() {
        });
        return json.keySet();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(Map<String, Object> parent, String key) {
        assertThat(parent).containsKey(key);
        return (Map<String, Object>) parent.get(key);
    }

    private <T> void assertEqualOrNull(T actual, T expected, String entityName) {
        if (expected != null) {
            assertThat(actual).as(entityName + " was set correctly").isEqualTo(expected);
        }
        else {
            assertThat(actual).as(entityName + " not present").isNull();
        }
    }

    private void assertFileUploadExercise(FileUploadExercise exercise, String filePattern, String exampleSolution) {
        assertEqualOrNull(exercise.getFilePattern(), filePattern, "File pattern");
        assertEqualOrNull(exercise.getExampleSolution(), exampleSolution, "Sample solution");
    }

    private void assertModelingExercise(ModelingExercise exercise, DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation) {
        assertThat(exercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(diagramType);
        assertEqualOrNull(exercise.getExampleSolutionModel(), exampleSolutionModel, "Sample solution model");
        assertEqualOrNull(exercise.getExampleSolutionExplanation(), exampleSolutionExplanation, "Sample solution explanation");
    }

    private void assertProgrammingExercise(ProgrammingExercise exercise, boolean projectKey, String templateRepositoryUri, String solutionRepositoryUri, String testRepositoryUri,
            String templateBuildPlanId, String solutionBuildPlanId) {
        if (projectKey) {
            assertThat(exercise.getProjectKey()).as("Project key was set").isNotNull();
        }
        else {
            assertThat(exercise.getProjectKey()).as("Project key not present").isNull();
        }
        assertEqualOrNull(exercise.getTemplateRepositoryUri(), templateRepositoryUri, "Template repository uri");
        assertEqualOrNull(exercise.getSolutionRepositoryUri(), solutionRepositoryUri, "Solution repository uri");
        assertEqualOrNull(exercise.getTestRepositoryUri(), testRepositoryUri, "Test repository uri");
        assertEqualOrNull(exercise.getTemplateBuildPlanId(), templateBuildPlanId, "Template build plan id");
        assertEqualOrNull(exercise.getSolutionBuildPlanId(), solutionBuildPlanId, "Solution build plan id");
    }

    private void assertQuizExercise(QuizExercise exercise, int duration, int allowedNumberOfAttempts, List<QuizQuestion> quizQuestions) {
        assertThat(exercise.getDuration()).as("Duration was set correctly").isEqualTo(duration);
        assertThat(exercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(allowedNumberOfAttempts);
        assertEqualOrNull(exercise.getQuizQuestions(), quizQuestions, "Quiz questions");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExamExercise_asStudent_forbidden() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "student11");
        getExamExercise();
    }

    private void getExamExercise() throws Exception {
        TextExercise textExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        request.get("/api/exercise/exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, ExerciseResponseDTO.class);
        request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetUpcomingExercises() throws Exception {
        var now = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<UpcomingExerciseDTO> exercises = request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.OK, UpcomingExerciseDTO.class);
        for (var exercise : exercises) {
            assertThat(exercise.dueDate()).isAfterOrEqualTo(now);
        }
        var size = exercises.size();

        // Test for exercise with upcoming due date.
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        var exercise = course.getExercises().iterator().next();
        assertThat(exercise.getDueDate()).isAfterOrEqualTo(now);
        exercises = request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.OK, UpcomingExerciseDTO.class);
        assertThat(exercises).hasSize(size + 1);
        var added = exercises.stream().filter(dto -> dto.id() == exercise.getId()).findFirst().orElseThrow();
        assertThat(added.type()).isEqualTo(exercise.getType());
        assertThat(added.title()).isEqualTo(exercise.getTitle());
        assertThat(added.dueDate()).isEqualTo(exercise.getDueDate());
        assertThat(added.course()).isNotNull();
        assertThat(added.course().id()).isEqualTo(course.getId());
        assertThat(added.course().title()).isEqualTo(course.getTitle());
        assertThat(exercises).extracting(UpcomingExerciseDTO::dueDate).isSortedAccordingTo(Comparator.naturalOrder());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetUpcomingExercisesAsStudentForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, UpcomingExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetUpcomingExercisesAsInstructorForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, UpcomingExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetUpcomingExercisesAsTutorForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, UpcomingExerciseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                ExerciseDetailsDTO exerciseWithDetailsWrapper = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
                Exercise exerciseWithDetails = exerciseWithDetailsWrapper.exercise();

                if (exerciseWithDetails instanceof FileUploadExercise fileUploadExercise) {
                    assertFileUploadExercise(fileUploadExercise, "png", null);
                    assertThat(fileUploadExercise.getStudentParticipations()).as("Number of participations is correct").isEmpty();
                }
                else if (exerciseWithDetails instanceof ModelingExercise modelingExercise) {
                    assertModelingExercise(modelingExercise, DiagramType.ClassDiagram, null, null);
                    assertThat(modelingExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(1);
                }
                else if (exerciseWithDetails instanceof ProgrammingExercise programmingExerciseExercise) {
                    assertProgrammingExercise(programmingExerciseExercise, true, null, null, null, null, null);
                    assertThat(programmingExerciseExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(2);
                }
                else if (exerciseWithDetails instanceof QuizExercise quizExercise) {
                    assertQuizExercise(quizExercise, 120, 1, List.of());
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
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        ZonedDateTime now = ZonedDateTime.now();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {

                request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, ExerciseResponseDTO.class);

                exercise.setExampleSolutionPublicationDate(now.minusHours(1));
                exerciseRepository.save(exercise);

                ExerciseResponseDTO exerciseForExampleSolution = request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK,
                        ExerciseResponseDTO.class);
                assertThat(exerciseForExampleSolution.exampleSolutionPublicationDate()).isBeforeOrEqualTo(now);
                assertThat(exerciseForExampleSolution.type()).isEqualTo(exercise.getType());
                assertThat(exerciseForExampleSolution.problemStatement()).isEqualTo(exercise.getProblemStatement());
                switch (exercise) {
                    case FileUploadExercise ignored -> assertThat(exerciseForExampleSolution.exampleSolution()).isEqualTo("Example Solution");
                    case ModelingExercise ignored -> {
                        assertThat(exerciseForExampleSolution.exampleSolutionModel()).isEqualTo("Example solution model");
                        assertThat(exerciseForExampleSolution.exampleSolutionExplanation()).isEqualTo("Example Solution");
                    }
                    case TextExercise ignored -> assertThat(exerciseForExampleSolution.exampleSolution()).isEqualTo("Example Solution");
                    default -> {
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "TA")
    void testGetExamExerciseForExampleSolution() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = courseUtilService.createEnrolledCourse(TEST_PREFIX);
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, user);
        Exam exam = course.getExams().stream().findFirst().orElseThrow();
        exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        TextExercise exercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, ExerciseResponseDTO.class);

        ZonedDateTime now = ZonedDateTime.now();
        exam.setExampleSolutionPublicationDate(now.minusHours(1));
        examUtilService.addStudentExamWithUser(exam, user);
        examRepository.save(exam);

        ExerciseResponseDTO exerciseForExampleSolution = request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK, ExerciseResponseDTO.class);

        assertThat(exerciseForExampleSolution.exampleSolution()).isEqualTo("This is my example solution");
        assertThat(exerciseForExampleSolution.course()).as("An exam exercise reports its course through the exercise group").isNull();
        assertThat(exerciseForExampleSolution.exerciseGroup()).isNotNull();
        assertThat(exerciseForExampleSolution.exerciseGroup().exam()).isNotNull();
        assertThat(exerciseForExampleSolution.exerciseGroup().exam().course()).isNotNull();
        assertThat(exerciseForExampleSolution.exerciseGroup().exam().course().id()).isEqualTo(course.getId());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_assessmentDueDate_notPassed() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
            }
            ExerciseDetailsDTO exerciseWithDetails = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
            for (StudentParticipation participation : exerciseWithDetails.exercise().getStudentParticipations()) {
                Set<Result> results = participationUtilService.getResultsForParticipation(participation);
                // Programming exercises should only have one automatic result
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(results).hasSize(1);
                    assertThat(results.iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
                }
                else {
                    // All other exercises should not display a result at all
                    assertThat(results).isEmpty();
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
            int resultSize = 1;
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
                resultSize = 2;
            }

            ExerciseDetailsDTO exerciseWithDetails = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
            for (var studentParticipation : exerciseWithDetails.exercise().getStudentParticipations()) {
                Set<Result> results = participationUtilService.getResultsForParticipation(studentParticipation);
                // Programming exercises should now how two results and the latest one is the manual result.
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(results).hasSize(resultSize);
                    assertThat(results.stream().sorted(Comparator.comparing(Result::getId).reversed()).iterator().next().getAssessmentType())
                            .isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                }
                else {
                    // All other exercises have only one visible result now
                    assertThat(results).hasSize(1);
                }
            }
        }
    }

    private void addResultToSubmissionAndParticipation(Exercise exercise) {
        var participation = exercise.getStudentParticipations().iterator().next();
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L), submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_withExamExercise_asStudent() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_withExamExercise_badRequest() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, ExerciseDetailsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void filterForCourseDashboard_assessmentDueDate_notPassed() {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
            }
            exerciseService.filterExerciseForCourseDashboard(exercise, Set.copyOf(exercise.getStudentParticipations()), true);

            StudentParticipation participation = exercise.getStudentParticipations().iterator().next();
            Set<Result> results = participationUtilService.getResultsForParticipation(participation);
            if (exercise instanceof ProgrammingExercise) {
                var submission = participation.getSubmissions().iterator().next();
                // Programming exercises should only have one automatic result
                assertThat(results).hasSize(1).first().matches(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC);
                assertThat(participation.getSubmissions()).hasSize(1);
                assertThat(submission.getResults()).hasSize(1).first().matches(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC);
            }
            else if (exercise instanceof QuizExercise) {
                // Since #12842, a submitted quiz before the due date exposes a sanitized submission (submitted flag and
                // submission date only, no answers, no results) so the dashboard can show "Submitted, waiting for due date".
                assertThat(participation.getSubmissions()).hasSize(1);
                var submission = participation.getSubmissions().iterator().next();
                assertThat(submission).isInstanceOf(QuizSubmission.class);
                assertThat(submission.isSubmitted()).isTrue();
                assertThat(submission.getResults()).isEmpty();
                assertThat(results).isEmpty();
            }
            else {
                // All other exercises have no visible result, and therefore no submission to transmit the result
                assertThat(participation.getSubmissions()).isEmpty();
                assertThat(results).isEmpty();
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
                addResultToSubmissionAndParticipation(exercise);
            }
            exerciseService.filterExerciseForCourseDashboard(exercise, Set.copyOf(exercise.getStudentParticipations()), true);
            Set<Result> results = participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next());
            // All exercises have one result
            assertThat(results).hasSize(1);
            // Programming exercises should now have one manual result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(results.iterator().next().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExercise_forbidden() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "student11");
        // Use a course without TEST_PREFIX enrollment so student11 is not enrolled (it should be FORBIDDEN)
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Text");
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, ExerciseResponseDTO.class);
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                ExerciseResponseDTO exerciseForAssessmentDashboard = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK,
                        ExerciseResponseDTO.class);
                assertThat(exerciseForAssessmentDashboard.tutorParticipations()).as("Tutor participation was created").hasSize(1);
                assertThat(exerciseForAssessmentDashboard.exampleSubmissions()).as("No example submissions, so the list is omitted from the response").isNullOrEmpty();

                // Test that certain properties were set correctly
                assertThat(exerciseForAssessmentDashboard.type()).as("Discriminator is present").isEqualTo(exercise.getType());
                assertThat(exerciseForAssessmentDashboard.releaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.dueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.maxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseForAssessmentDashboard.difficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);
                assertThat(exerciseForAssessmentDashboard.course()).as("Course context is present").isNotNull();

                // Test presence of exercise type specific properties
                switch (exercise) {
                    case FileUploadExercise ignored -> assertThat(exerciseForAssessmentDashboard.filePattern()).as("File pattern was set correctly").isEqualTo("png");
                    case ModelingExercise ignored ->
                        assertThat(exerciseForAssessmentDashboard.diagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    case ProgrammingExercise ignored -> assertThat(exerciseForAssessmentDashboard.projectKey()).as("Project key was set").isNotNull();
                    case QuizExercise ignored -> {
                        assertThat(exerciseForAssessmentDashboard.duration()).as("Duration was set correctly").isEqualTo(120);
                        assertThat(exerciseForAssessmentDashboard.allowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    /**
     * The assessment dashboard renders a programming exercise's problem statement through the instructions component,
     * which it feeds with {@code templateParticipation}, and offers the solution repository through the code button,
     * which reads {@code solutionParticipation.repositoryUri}. Both are fetch-joined by the dashboard's programming
     * query, so both have to reach the client.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_programmingExerciseCarriesTemplateAndSolutionParticipation() throws Exception {
        Course course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        ProgrammingExercise exercise = (ProgrammingExercise) course.getExercises().iterator().next();
        exercise.setAssessmentType(AssessmentType.SEMI_AUTOMATIC);
        exerciseRepository.save(exercise);

        ExerciseResponseDTO response = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, ExerciseResponseDTO.class);

        assertThat(response.templateParticipation()).as("Template participation the instructions component needs").isNotNull();
        assertThat(response.templateParticipation().id()).isNotNull();
        assertThat(response.solutionParticipation()).as("Solution participation the code button needs").isNotNull();
        assertThat(response.solutionParticipation().repositoryUri()).as("Repository URI the code button links to").isNotBlank();
    }

    /**
     * The exam assessment dashboard builds one submission fetch and one submission section per correction round from
     * {@code exerciseGroup.exam.numberOfCorrectionRoundsInExam}, and renders the exam dates instead of a course, so the
     * exam context has to reach the client with those fields. A dropped field degrades silently: the page would render
     * zero assessable rounds with a 200 and no error.
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExamExerciseForAssessmentDashboardCarriesExamContext() throws Exception {
        TextExercise exercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        Exam exam = exercise.getExerciseGroup().getExam();
        exam.setNumberOfCorrectionRoundsInExam(2);
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        exam = examRepository.save(exam);

        ExerciseResponseDTO response = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, ExerciseResponseDTO.class);

        assertThat(response.course()).as("An exam exercise reports its course through the exercise group").isNull();
        assertThat(response.exerciseGroup()).isNotNull();
        assertThat(response.exerciseGroup().title()).isEqualTo(exercise.getExerciseGroup().getTitle());
        var examContext = response.exerciseGroup().exam();
        assertThat(examContext).isNotNull();
        assertThat(examContext.numberOfCorrectionRoundsInExam()).as("The dashboard iterates over the correction rounds").isEqualTo(2);
        assertThat(examContext.endDate()).as("Exam end date the dashboard shows instead of a course").isCloseTo(exam.getEndDate(), within(1, ChronoUnit.SECONDS));
        assertThat(examContext.publishResultsDate()).isCloseTo(exam.getPublishResultsDate(), within(1, ChronoUnit.SECONDS));
        assertThat(examContext.course()).isNotNull();
        assertThat(examContext.course().id()).isEqualTo(exam.getCourse().getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_submissionsWithoutAssessments() throws Exception {
        var validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        var course = modelingExerciseUtilService.addEnrolledCourseWithOneModelingExercise("ClassDiagram", TEST_PREFIX);
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        var exampleSubmission = participationUtilService.generateExampleSubmission(validModel, exercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        ExerciseResponseDTO receivedExercise = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, ExerciseResponseDTO.class);
        assertThat(receivedExercise.exampleSubmissions()).as("Example submission without assessment is removed from exercise").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExerciseForAssessmentDashboard_forbidden() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6");
        // Use a course without TEST_PREFIX enrollment so tutor6 is not enrolled (it should be FORBIDDEN)
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Text").getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, ExerciseResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_programmingExerciseWithAutomaticAssessment() throws Exception {
        var exercise = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX).getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.BAD_REQUEST, ExerciseResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_exerciseWithTutorParticipation() throws Exception {
        var exercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX).getExercises().iterator().next();
        var tutorParticipation = new TutorParticipation().tutor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1")).assessedExercise(exercise)
                .status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationRepo.save(tutorParticipation);
        var textExercise = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, ExerciseResponseDTO.class);
        assertThat(textExercise.tutorParticipations().getFirst().status()).as("Status was changed to trained").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    private List<User> findTutors(Course course) {
        return userCourseRoleTestRepository.findByCourse_IdAndRole(course.getId(), CourseRole.TEACHING_ASSISTANT).stream().map(UserCourseRole::getUser).toList();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            var tutors = findTutors(course);
            for (Exercise exercise : course.getExercises()) {
                StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                        StatsForDashboardDTO.class);
                assertThat(stats.getTotalNumberOfAssessments()).as("Number of in-time assessments is correct").isZero();

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
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6");
        // Use a course without TEST_PREFIX enrollment so tutor6 is not enrolled (it should be FORBIDDEN)
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Text").getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExercise() throws Exception {
        List<Course> courses = courseUtilService.createEnrolledCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercise/exercises/" + exercise.getId() + "/reset", HttpStatus.OK);
                assertThat(exercise.getStudentParticipations()).as("Student participations have been deleted").isEmpty();
                assertThat(exercise.getTutorParticipations()).as("Tutor participations have been deleted").isEmpty();
                assertThat(participationRepository.findWithIndividualDueDateByExerciseId(exercise.getId())).isEmpty();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testResetExercise_forbidden() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
        // Use a course without TEST_PREFIX enrollment so instructor2 is not enrolled (it should be FORBIDDEN)
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Text").getExercises().iterator().next();
        request.delete("/api/exercise/exercises/" + exercise.getId() + "/reset", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagEnable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];

        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class,
                HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagDisable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setSecondCorrectionEnabled(true);
        exerciseRepository.save(exercise);
        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class,
                HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testSetSecondCorrectionEnabledFlagForbidden() throws Exception {
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6");
        // Use a course without TEST_PREFIX enrollment so tutor6 is not enrolled (it should be FORBIDDEN)
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise("Text");
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
        testGetExamExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
        testGetExamExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        // course exercise
        testGetExerciseTitle();

        // exam exercise
        testGetExamExerciseTitle();
    }

    private void testGetExerciseTitle() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setTitle("Test Exercise");
        exercise = exerciseRepository.save(exercise);

        final var title = request.get("/api/exercise/exercises/" + exercise.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exercise.getTitle());
    }

    private void testGetExamExerciseTitle() throws Exception {
        TextExercise textExercise = examUtilService.addEnrolledCourseExamExerciseGroupWithOneTextExercise(TEST_PREFIX);
        final String expectedTitle = textExercise.getExerciseGroup().getTitle();
        final String title = request.get("/api/exercise/exercises/" + textExercise.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(expectedTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExerciseTitleForNonExistingExercise() throws Exception {
        request.get("/api/exercise/exercises/12312321321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        StudentParticipation studentParticipation2 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
        StudentParticipation studentParticipation3 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student3");

        studentParticipation2.setIndividualDueDate(exercise.getDueDate().plusHours(2));
        studentParticipation3.setIndividualDueDate(exercise.getDueDate().plusHours(4));
        participationRepository.save(studentParticipation2);
        participationRepository.save(studentParticipation3);

        ZonedDateTime latestDueDate = request.get("/api/exercise/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(studentParticipation3.getIndividualDueDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDateWhenNoIndividualDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addEnrolledCourseWithOneReleasedTextExercise("Text", TEST_PREFIX);
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");

        ZonedDateTime latestDueDate = request.get("/api/exercise/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(exercise.getDueDate(), within(1, ChronoUnit.SECONDS));
    }
}
