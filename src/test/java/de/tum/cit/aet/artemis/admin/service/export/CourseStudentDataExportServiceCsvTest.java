package de.tum.cit.aet.artemis.admin.service.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tools.jackson.databind.json.JsonMapper;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.domain.LLMServiceType;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageRequest;
import de.tum.cit.aet.artemis.admin.domain.LLMTokenUsageTrace;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.GradeStep;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.BonusResultDTO;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.atlas.dto.export.CompetencyProgressExportDTO;
import de.tum.cit.aet.artemis.atlas.dto.export.LearnerProfileExportDTO;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.AnswerPostRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.test_repository.LLMTokenUsageTraceTestRepository;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO.ExerciseGroupDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO.ExerciseResultDTO;
import de.tum.cit.aet.artemis.exam.dto.ExamScoresDTO.StudentResultDTO;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseType;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.CourseGradeScoreDTO;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.iris.api.IrisSettingsApi;
import de.tum.cit.aet.artemis.iris.dto.export.IrisChatSessionExportDTO;
import de.tum.cit.aet.artemis.iris.dto.export.IrisMessageExportDTO;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.programming.service.ProgrammingFeedbackSynthesizerService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistrationType;

/** Tests exported files through the public export API and an independent CSV parser. */
@ExtendWith(MockitoExtension.class)
@Isolated("Verifies CSV exports after changing the JVM default locale")
class CourseStudentDataExportServiceCsvTest {

    private static final long COURSE_ID = 12L;

    private static final long EXAM_ID = 34L;

    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private ParticipationTestRepository participationRepository;

    @Mock
    private PostTestRepository postRepository;

    @Mock
    private AnswerPostRepository answerPostRepository;

    @Mock
    private LLMTokenUsageTraceTestRepository llmTokenUsageTraceRepository;

    @Mock
    private CourseTestRepository courseRepository;

    @Mock
    private CompetencyProgressApi competencyProgressApi;

    @Mock
    private LearnerProfileApi learnerProfileApi;

    @Mock
    private IrisSettingsApi irisSettingsApi;

    @Mock
    private TutorialGroupApi tutorialGroupApi;

    @Mock
    private GradingScaleRepository gradingScaleRepository;

    @Mock
    private StudentParticipationTestRepository studentParticipationRepository;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private ProgrammingFeedbackSynthesizerService feedbackSynthesizer;

    @TempDir
    private Path outputDirectory;

    private CourseStudentDataExportService service;

    private Course course;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(COURSE_ID);
        course.setAccuracyOfScores(1);
        service = createService(true);
    }

    @ParameterizedTest
    @ValueSource(strings = { "Group, one", "Group \"quoted\"", "Group\nsecond line", "Group\rsecond line" })
    void roundTripsSpecialCharactersInExamExerciseGroupHeaders(String title) throws IOException {
        var exercise = new ExerciseResultDTO(7L, "Exercise", 100.0, 50.0, 50.0, true);
        var student = student("Ada", true, 50.0, 50.0, null, null, Map.of(1L, exercise));
        var scores = exam("Exam", 100, false, List.of(new ExerciseGroupDTO(1L, title, 100.0, 1L, List.of())), List.of(student));
        var errors = new ArrayList<String>();

        List<Path> files = service.exportExamScoresFromData(scores, outputDirectory, errors, course);

        assertThat(errors).isEmpty();
        assertThat(files).hasSize(1);
        CsvData csv = readCsv(files.getFirst());
        assertThat(csv.headers()).contains(title + " Exercise", title + " Points", title + " Score");
        assertThat(csv.row("Ada")).containsExactly("Ada", "student", "student@example.org", "123", "true", "Exercise", "50.0", "50.0", "50.0", "50.0", "100", "");
    }

    @ParameterizedTest
    @MethodSource("courseTitles")
    void roundTripsSpecialCharactersInCourseExerciseHeaders(String title, String exportedTitle) throws IOException {
        var exercise = new TextExercise();
        exercise.setId(7L);
        exercise.setTitle(title);
        exercise.setMaxPoints(100.0);
        course.setExercises(Set.of(exercise));
        var user = new User();
        user.setId(1L);
        user.setLogin("student");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        when(courseRepository.findByIdWithEagerExercisesElseThrow(COURSE_ID)).thenReturn(course);
        when(studentParticipationRepository.findGradeScoresForAllExercisesForCourse(COURSE_ID))
                .thenReturn(Set.of(new CourseGradeScoreDTO(2L, 1L, 7L, 50.0, true, null, ExerciseType.TEXT)));
        when(userRepository.findAllById(Set.of(1L))).thenReturn(List.of(user));
        var errors = new ArrayList<String>();

        List<Path> files = service.exportAllStudentData(COURSE_ID, outputDirectory, errors);

        assertThat(errors).isEmpty();
        assertThat(files).contains(outputDirectory.resolve("student-data/course-scores.csv"));
        CsvData csv = readCsv(outputDirectory.resolve("student-data/course-scores.csv"));
        assertThat(csv.headers()).contains(exportedTitle + " Points", exportedTitle + " Score");
        assertThat(csv.row("Ada Lovelace")).containsExactly("Ada Lovelace", "student", "", "", "50.0", "50.0", "50.0", "50.0", "100.0");
    }

    private static Stream<Arguments> courseTitles() {
        return Stream.of(Arguments.of("Exercise, one", "Exercise, one"), Arguments.of("Exercise \"quoted\"", "Exercise \"quoted\""),
                Arguments.of("Exercise\nsecond line", "Exercise second line"), Arguments.of("Exercise\rsecond line", "Exercise second line"));
    }

    @Test
    void exportsOptionalExamColumnsAndLeavesMissingStudentValuesEmpty() throws IOException {
        var bonus = new BonusResultDTO(null, "Practice", 10.0, "0.3", null, "P+", null, null, null);
        var ada = new StudentResultDTO(1L, "Ada, \"L\"", "ada@example.org", "ada", "42", true, 75.0, 75.0, "P", "F", true, 70.0, bonus,
                Map.of(1L, new ExerciseResultDTO(7L, "Solution, \"A\"", 50.0, 60.0, 30.0, true), 2L, new ExerciseResultDTO(8L, "Alternative", 50.0, 90.0, 45.0, true)),
                PlagiarismVerdict.WARNING);
        var missing = new StudentResultDTO(2L, "Missing", null, null, null, null, null, null, null, null, null, null, null, null, null);
        var emptyBonus = new BonusResultDTO(null, null, null, null, null, null, null, null, null);
        var noGrade = new StudentResultDTO(3L, "No grade", null, "third", null, false, 0.0, 0.0, "", null, false, null, emptyBonus,
                Map.of(1L, new ExerciseResultDTO(7L, null, null, null, null, false)), null);
        var groups = List.of(new ExerciseGroupDTO(1L, "First", 50.0, 3L, List.of()), new ExerciseGroupDTO(2L, "Second", 50.0, 3L, List.of()));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam("Exam", 100, true, groups, List.of(ada, missing, noGrade)), outputDirectory, errors, course);

        assertThat(errors).isEmpty();
        CsvData csv = readCsv(files.getFirst());
        assertThat(csv.headers()).containsExactly("Name", "Username", "Email", "RegistrationNumber", "Submitted", "First Exercise", "First Points", "First Score",
                "Second Exercise", "Second Points", "Second Score", "Overall Points", "Overall Score", "Max Points", "Grade", "Passed", "First Correction Points",
                "First Correction Grade", "Bonus Source Points", "Bonus Grade", "Final Grade", "Plagiarism Verdict");
        assertThat(csv.row("Ada, \"L\"")).containsExactly("Ada, \"L\"", "ada", "ada@example.org", "42", "true", "Solution, \"A\"", "30.0", "60.0", "Alternative", "45.0", "90.0",
                "75.0", "75.0", "100", "P", "true", "70.0", "F", "10.0", "0.3", "P+", "WARNING");
        assertThat(csv.row("Missing")).containsExactly("Missing", "", "", "", "false", "", "0.0", "0.0", "", "0.0", "0.0", "0.0", "0.0", "100", "", "", "", "", "", "", "", "");
        assertThat(csv.row("No grade")).containsExactly("No grade", "third", "", "", "false", "", "0.0", "0.0", "", "0.0", "0.0", "0.0", "0.0", "100", "", "false", "", "", "", "",
                "", "");
    }

    @Test
    void calculatesExamStatisticsAndSubmittedAndPassedSubsets() throws IOException {
        var first = student("First", true, 20.0, 20.0, "F", false, Map.of(1L, new ExerciseResultDTO(7L, "Exercise", 100.0, 20.0, 20.0, true)));
        var second = student("Second", false, 60.0, 60.0, "P", true, Map.of(1L, new ExerciseResultDTO(7L, "Exercise", 100.0, 60.0, 60.0, true)));
        var groups = List.of(new ExerciseGroupDTO(1L, "Group", 100.0, 2L, List.of()));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam("Exam", 100, false, groups, List.of(first, second)), outputDirectory, errors, course);

        assertThat(errors).isEmpty();
        CsvData csv = readCsv(files.getFirst());
        assertThat(csv.row("Registered")).containsExactly("Registered", "2");
        assertThat(csv.row("Submitted")).containsExactly("Submitted", "1 (50.0%)");
        assertThat(csv.row("Passed")).containsExactly("Passed", "1 (50.0%)");
        assertThat(csv.value("Max", "Group Points")).isEqualTo("100.0");
        assertThat(csv.value("Average (All)", "Group Points")).isEqualTo("40.0");
        assertThat(csv.value("Average (All)", "Overall Score")).isEqualTo("40.0");
        assertThat(csv.value("Average (Submitted)", "Overall Points")).isEqualTo("20.0");
        assertThat(csv.value("Average (Passed)", "Overall Points")).isEqualTo("60.0");
        assertThat(csv.value("Median", "Overall Points")).isEqualTo("40.0");
        assertThat(csv.value("Std Dev", "Overall Points")).isEqualTo("20.0");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = { 0, 100 })
    void exportsMissingResultsWithoutDivisionByZero(Integer maxPoints) throws IOException {
        var student = student("Missing", false, null, null, null, false, Map.of());
        var groups = List.of(new ExerciseGroupDTO(1L, "No maximum", null, 1L, List.of()), new ExerciseGroupDTO(2L, "Zero", 0.0, 1L, List.of()));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam(null, maxPoints, null, groups, List.of(student)), outputDirectory, errors, course);

        assertThat(errors).isEmpty();
        assertThat(files.getFirst().getFileName().toString()).isEqualTo("exam-scores-34-unnamed.csv");
        CsvData csv = readCsv(files.getFirst());
        assertThat(csv.value("Missing", "Overall Points")).isEqualTo("0.0");
        assertThat(csv.value("Missing", "Overall Score")).isEqualTo("0.0");
        assertThat(csv.value("Average (All)", "No maximum Score")).isEqualTo("0.0");
        assertThat(csv.value("Average (All)", "Zero Score")).isEqualTo("0.0");
        assertThat(csv.rows()).noneMatch(row -> row.getFirst().equals("Average (Submitted)") || row.getFirst().equals("Average (Passed)"));
        assertThat(Files.readString(files.getFirst())).doesNotContain("NaN", "Infinity");
    }

    @ParameterizedTest
    @CsvSource({ "0, 10.0", "1, 10.1", "2, 10.13" })
    void roundsExportedPointsAccordingToCourseAccuracy(int accuracy, String expected) throws IOException {
        course.setAccuracyOfScores(accuracy);
        var errors = new ArrayList<String>();
        var files = service.exportExamScoresFromData(exam("Exam", 100, false, List.of(), List.of(student("Ada", true, 10.126, 10.126, null, null, null))), outputDirectory, errors,
                course);

        assertThat(errors).isEmpty();
        assertThat(readCsv(files.getFirst()).value("Ada", "Overall Points")).isEqualTo(expected);
    }

    @Test
    void usesDtoGradesAndClampsScoreDistributionIntervals() throws IOException {
        when(gradingScaleRepository.findByExamId(EXAM_ID)).thenReturn(Optional.of(gradingScale()));
        var students = List.of(student("Negative", true, -5.0, -5.0, "F", false, null), student("Zero", true, 0.0, 0.0, "", false, null),
                student("Ten", true, 10.0, 10.0, "P", true, null), student("Ninety", true, 99.9, 99.9, "P", true, null), student("Hundred", true, 100.0, 100.0, "P", true, null),
                student("Bonus", true, 120.0, 120.0, "P", true, null), student("Missing", null, null, null, null, null, null));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam("../../Final /Exam", 100, false, List.of(), students), outputDirectory, errors, course);

        assertThat(errors).isEmpty();
        assertThat(files).extracting(path -> path.getFileName().toString()).containsExactly("exam-scores-34-______Final__Exam.csv",
                "exam-34-______Final__Exam-grade-distribution.csv");
        CsvData csv = readCsv(files.get(1));
        assertThat(csv.rows()).contains(List.of("GRADE", "P", "50.0", "100.0", "true", "4", "57.1"), List.of("GRADE", "F", "0.0", "50.0", "false", "1", "14.3"),
                List.of("INTERVAL", "0-10%", "0", "10", "", "3", "42.9"), List.of("INTERVAL", "10-20%", "10", "20", "", "1", "14.3"),
                List.of("INTERVAL", "90-100%", "90", "100", "", "3", "42.9"));
    }

    @Test
    void retainsExamScoresWhenGradeDistributionFails() throws IOException {
        when(gradingScaleRepository.findByExamId(EXAM_ID)).thenThrow(new IllegalStateException("Unavailable grading scale"));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam("Exam", 100, false, List.of(), List.of(student("Ada", true, 50.0, 50.0, null, null, null))), outputDirectory, errors,
                course);

        assertThat(files).hasSize(1);
        assertThat(readCsv(files.getFirst()).value("Ada", "Overall Points")).isEqualTo("50.0");
        assertThat(errors).containsExactly("Failed to export exam grade distribution for exam 34");
    }

    @Test
    void reportsFileCreationFailureWithoutReturningAnExamFile() throws IOException {
        Path blocked = Files.createFile(outputDirectory.resolve("file"));
        var errors = new ArrayList<String>();

        var files = service.exportExamScoresFromData(exam("Exam", 100, false, List.of(), List.of(student("Ada", true, 50.0, 50.0, null, null, null))), blocked, errors, course);

        assertThat(files).isEmpty();
        assertThat(errors).containsExactly("Failed to export exam scores for exam 34");
    }

    @Test
    void skipsAbsentAndEmptyExamData() {
        var errors = new ArrayList<String>();

        assertThat(service.exportExamScoresFromData(null, outputDirectory, errors, course)).isEmpty();
        assertThat(service.exportExamScoresFromData(exam("Empty", 100, false, List.of(), List.of()), outputDirectory, errors, course)).isEmpty();
        assertThat(errors).isEmpty();
    }

    @Test
    void exportsPostsAndAnswersWithEscapedContentAndMissingMetadata() throws IOException {
        var author = new User();
        author.setLogin("author");
        var channel = new Channel();
        channel.setId(7L);
        var post = new Post();
        post.setId(1L);
        post.setAuthor(author);
        post.setCreationDate(ZonedDateTime.parse("2026-01-02T03:04:05Z"));
        post.setContent("Text, \"quoted\"\nnext line\rreturn");
        post.setConversation(channel);
        var anonymous = new Post();
        anonymous.setId(2L);
        anonymous.setCreationDate(null);
        var answer = new AnswerPost();
        answer.setId(3L);
        answer.setAuthor(author);
        answer.setCreationDate(ZonedDateTime.parse("2026-01-02T03:04:05Z"));
        answer.setPost(post);
        answer.setContent("Answer, \"quoted\"");
        var missing = new AnswerPost();
        missing.setId(4L);
        missing.setCreationDate(null);
        when(postRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(post, anonymous));
        when(answerPostRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(answer, missing));

        exportCourseData();

        assertThat(readCsv(outputDirectory.resolve("student-data/posts.csv")).rows()).containsExactly(
                List.of("POST", "1", "author", "2026-01-02 03:04:05", "Text, \"quoted\"\nnext line\rreturn", "7", ""), List.of("POST", "2", "", "", "", "", ""),
                List.of("ANSWER", "3", "author", "2026-01-02 03:04:05", "Answer, \"quoted\"", "", "1"), List.of("ANSWER", "4", "", "", "", "", ""));
    }

    @Test
    void exportsOptionalLearningDataAndTutorialRegistrationsWithNullableValues() throws IOException {
        when(competencyProgressApi.findAllForExportByCourseId(COURSE_ID))
                .thenReturn(List.of(new CompetencyProgressExportDTO(7L, "Competency, \"quoted\"", "student", 0.5, 0.8, Instant.parse("2026-01-02T03:04:05Z")),
                        new CompetencyProgressExportDTO(null, null, null, null, null, null)));
        when(learnerProfileApi.findAllForExportByCourseId(COURSE_ID))
                .thenReturn(List.of(new LearnerProfileExportDTO("student", 2, 3, 4), new LearnerProfileExportDTO(null, null, null, null)));
        var student = new User();
        student.setLogin("student");
        student.setFirstName("Ada, \"A\"");
        student.setLastName("Lovelace");
        var group = new TutorialGroup();
        group.setId(8L);
        group.setTitle("Group, \"G\"");
        when(tutorialGroupApi.findAllRegistrationsByCourseId(COURSE_ID))
                .thenReturn(Set.of(new TutorialGroupRegistration(student, group, TutorialGroupRegistrationType.SELF_REGISTRATION), new TutorialGroupRegistration()));

        List<Path> files = exportCourseData();

        assertThat(files).extracting(path -> path.getFileName().toString()).containsExactly("competency-progress.csv", "learner-profiles.csv", "tutorial-group-registrations.csv");
        assertThat(readCsv(files.get(0)).rows()).containsExactly(List.of("7", "Competency, \"quoted\"", "student", "0.5", "0.8", "2026-01-02 03:04:05"),
                List.of("", "", "", "", "", ""));
        assertThat(readCsv(files.get(1)).rows()).containsExactly(List.of("student", "2", "3", "4"), List.of("", "", "", ""));
        assertThat(readCsv(files.get(2)).rows()).containsExactlyInAnyOrder(List.of("8", "Group, \"G\"", "student", "Ada, \"A\" Lovelace", "SELF_REGISTRATION"),
                List.of("", "", "", "", ""));
    }

    @Test
    void exportsIrisMessagesAsJsonWithoutLosingSpecialCharacters() throws IOException {
        var timestamp = ZonedDateTime.parse("2026-01-02T03:04:05Z");
        var message = new IrisMessageExportDTO(3L, timestamp, "USER", "Message, \"quoted\"\nnext line", true);
        var session = new IrisChatSessionExportDTO(1L, 2L, timestamp, List.of(message));
        when(irisSettingsApi.findCourseChatSessionsForExport(COURSE_ID)).thenReturn(List.of(session));

        List<Path> files = exportCourseData();

        assertThat(files).containsExactly(outputDirectory.resolve("student-data/iris-sessions.json"));
        assertThat(MAPPER.readValue(files.getFirst().toFile(), IrisChatSessionExportDTO[].class)).containsExactly(session);
    }

    @Test
    void supportsAbsentOptionalModulesAndAnEmptyCourse() throws IOException {
        service = createService(false);

        assertThat(exportCourseData()).isEmpty();
        assertThat(outputDirectory.resolve("student-data")).isDirectory();
    }

    @Test
    void reportsDirectoryCreationFailureWithoutExportingFiles() throws IOException {
        Path blocked = Files.createFile(outputDirectory.resolve("file"));
        var errors = new ArrayList<String>();

        assertThat(service.exportAllStudentData(COURSE_ID, blocked, errors)).isEmpty();
        assertThat(errors).containsExactly("Failed to create student-data directory for course 12");
    }

    @ParameterizedTest
    @CsvSource({ "participations, participation results, participation-results.csv", "posts, posts, posts.csv", "answers, posts, posts.csv",
            "competencies, competency progress, competency-progress.csv", "profiles, learner profiles, learner-profiles.csv",
            "tutorials, tutorial group registrations, tutorial-group-registrations.csv", "iris, Iris chat sessions, iris-sessions.json",
            "llm, LLM token usage traces, llm-token-usage.csv", "scores, course scores, course-scores.csv", "grades, course grade distribution, course-grade-distribution.csv" })
    void continuesExportingOtherSectionsAfterASourceFails(String source, String section, String failedFilename) throws IOException {
        when(courseRepository.findByIdWithEagerExercisesElseThrow(COURSE_ID)).thenReturn(course);
        var post = new Post();
        post.setId(1L);
        post.setContent("Retained post");
        when(postRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(post));
        when(learnerProfileApi.findAllForExportByCourseId(COURSE_ID)).thenReturn(List.of(new LearnerProfileExportDTO("student", 1, 2, 3)));
        var failure = new IllegalStateException("Export source unavailable");
        switch (source) {
            case "participations" -> when(participationRepository.findAllWithLatestSubmissionAndResultByCourseId(COURSE_ID)).thenThrow(failure);
            case "posts" -> when(postRepository.findAllByCourseId(COURSE_ID)).thenThrow(failure);
            case "answers" -> when(answerPostRepository.findAllByCourseId(COURSE_ID)).thenThrow(failure);
            case "competencies" -> when(competencyProgressApi.findAllForExportByCourseId(COURSE_ID)).thenThrow(failure);
            case "profiles" -> when(learnerProfileApi.findAllForExportByCourseId(COURSE_ID)).thenThrow(failure);
            case "tutorials" -> when(tutorialGroupApi.findAllRegistrationsByCourseId(COURSE_ID)).thenThrow(failure);
            case "iris" -> when(irisSettingsApi.findCourseChatSessionsForExport(COURSE_ID)).thenThrow(failure);
            case "llm" -> when(llmTokenUsageTraceRepository.findAllWithRequestsByCourseId(COURSE_ID)).thenThrow(failure);
            case "scores" -> when(studentParticipationRepository.findGradeScoresForAllExercisesForCourse(COURSE_ID)).thenThrow(failure);
            case "grades" -> when(gradingScaleRepository.findByCourseId(COURSE_ID)).thenThrow(failure);
            default -> throw new IllegalArgumentException("Unknown test source: " + source);
        }
        var errors = new ArrayList<String>();

        var files = service.exportAllStudentData(COURSE_ID, outputDirectory, errors);

        assertThat(errors).containsExactly("Failed to export " + section + " for course 12");
        assertThat(files).extracting(path -> path.getFileName().toString()).doesNotContain(failedFilename);
        if (source.equals("profiles")) {
            assertThat(readCsv(outputDirectory.resolve("student-data/posts.csv")).rows()).singleElement().satisfies(row -> assertThat(row.get(4)).isEqualTo("Retained post"));
        }
        else {
            assertThat(readCsv(outputDirectory.resolve("student-data/learner-profiles.csv")).rows()).containsExactly(List.of("student", "1", "2", "3"));
        }
    }

    @Test
    void exportsTokenUsageAndCacheCostsWithNullableTraceMetadata() throws IOException {
        var request = new LLMTokenUsageRequest();
        request.setModel("test-model");
        request.setServicePipelineId("IRIS_CHAT");
        request.setNumInputTokens(200);
        request.setNumOutputTokens(20);
        request.setCostPerMillionInputTokens(1.1f);
        request.setCostPerMillionOutputTokens(2.2f);
        request.setNumCachedInputTokens(100);
        request.setCostPerMillionCachedInputTokens(0.1f);
        request.setNumCacheWriteInputTokens(10);
        request.setCostPerMillionCacheWriteInputTokens(0.2f);
        var trace = new LLMTokenUsageTrace();
        trace.setId(10L);
        trace.setServiceType(LLMServiceType.IRIS);
        trace.setUserId(2L);
        trace.setExerciseId(3L);
        trace.setTime(ZonedDateTime.parse("2026-01-02T03:04:05Z"));
        trace.setLlmRequests(Set.of(request));
        var missing = new LLMTokenUsageTrace();
        missing.setId(11L);
        missing.setTime(null);
        missing.setLlmRequests(Set.of(new LLMTokenUsageRequest()));
        when(llmTokenUsageTraceRepository.findAllWithRequestsByCourseId(COURSE_ID)).thenReturn(List.of(trace, missing));

        var files = exportCourseData();

        assertThat(files).containsExactly(outputDirectory.resolve("student-data/llm-token-usage.csv"));
        assertThat(readCsv(files.getFirst()).rows()).containsExactly(
                List.of("10", "IRIS", "2", "3", "2026-01-02 03:04:05", "test-model", "IRIS_CHAT", "200", "20", "1.1", "2.2", "100", "0.1", "10", "0.2"),
                List.of("11", "", "", "", "", "", "", "0", "0", "0.0", "0.0", "0", "0.0", "0", "0.0"));
    }

    @Test
    void exportsOnlyLatestSubmissionAndResultWithDeterministicFeedback() throws IOException {
        var practice = new StudentParticipation();
        practice.setPracticeMode(true);
        practice.setSubmissions(null);
        var rated = new StudentParticipation();
        var exercise = new TextExercise();
        exercise.setId(7L);
        exercise.setTitle("Essay");
        rated.setExercise(exercise);
        var user = new User();
        user.setLogin("student");
        user.setFirstName("Ada");
        user.setLastName("Lovelace");
        rated.setParticipant(user);
        var earlierSubmission = new TextSubmission();
        earlierSubmission.setSubmissionDate(ZonedDateTime.parse("2025-01-01T00:00:00Z"));
        var submission = new TextSubmission();
        submission.setSubmissionDate(ZonedDateTime.parse("2026-01-02T03:04:05Z"));
        var earlierResult = new Result();
        earlierResult.setCompletionDate(ZonedDateTime.parse("2026-01-02T04:00:00Z"));
        earlierResult.setScore(10.0);
        var latestResult = new Result();
        latestResult.setCompletionDate(ZonedDateTime.parse("2026-01-02T05:00:00Z"));
        latestResult.setScore(80.0);
        latestResult.setRated(true);
        latestResult.setSuccessful(true);
        var fallback = new Feedback();
        fallback.setId(1L);
        fallback.setText("Fallback");
        fallback.setCredits(2.0);
        var detailed = new Feedback();
        detailed.setId(2L);
        detailed.setText("Ignored summary");
        detailed.setDetailText("Detailed, \"remark\"");
        var empty = new Feedback();
        empty.setId(3L);
        latestResult.setFeedbacks(List.of(empty, detailed, fallback));
        submission.setResults(Set.of(earlierResult, latestResult));
        rated.setSubmissions(Set.of(submission, earlierSubmission));
        when(participationRepository.findAllWithLatestSubmissionAndResultByCourseId(COURSE_ID)).thenReturn(List.of(practice, rated));

        var files = exportCourseData();

        assertThat(readCsv(files.getFirst()).rows()).containsExactly(List.of("", "", "", "", "PRACTICE", "", "", "", "", "0", ""),
                List.of("7", "Essay", "student", "Ada Lovelace", "RATED", "2026-01-02 03:04:05", "80.0", "true", "true", "3", "Fallback (2.0 pts) | Detailed, \"remark\" | "));
    }

    @Test
    void exportsParticipationsWithoutSubmissionsOrCompletedResults() throws IOException {
        var noSubmission = new StudentParticipation();
        noSubmission.setSubmissions(Set.of());
        var noResult = new StudentParticipation();
        var emptySubmission = new TextSubmission();
        emptySubmission.setResults(Set.of());
        noResult.setSubmissions(Set.of(emptySubmission));
        var incomplete = new StudentParticipation();
        var submission = new TextSubmission();
        var result = new Result();
        result.setCompletionDate(null);
        result.setScore(null);
        result.setSuccessful(null);
        submission.setResults(Set.of(result));
        incomplete.setSubmissions(Set.of(submission));
        when(participationRepository.findAllWithLatestSubmissionAndResultByCourseId(COURSE_ID)).thenReturn(List.of(noSubmission, noResult, incomplete));

        var files = exportCourseData();

        assertThat(readCsv(files.getFirst()).rows()).containsExactly(List.of("", "", "", "", "RATED", "", "", "", "", "0", ""),
                List.of("", "", "", "", "RATED", "", "", "", "", "0", ""), List.of("", "", "", "", "RATED", "", "", "false", "", "0", ""));
    }

    @Test
    void usesDecimalPointsForCsvPercentagesWithAGermanJvmLocale() throws IOException {
        Locale original = Locale.getDefault();
        Locale originalDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        Locale originalFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.GERMANY);
            when(gradingScaleRepository.findByExamId(EXAM_ID)).thenReturn(Optional.of(gradingScale()));
            var students = List.of(student("Passed", true, 100.0, 100.0, "P", true, null), student("Failed", false, 0.0, 0.0, "F", false, null));
            var errors = new ArrayList<String>();

            var files = service.exportExamScoresFromData(exam("Exam", 100, false, List.of(), students), outputDirectory, errors, course);

            assertThat(errors).isEmpty();
            assertThat(readCsv(files.getFirst()).row("Submitted")).containsExactly("Submitted", "1 (50.0%)");
            assertThat(readCsv(files.get(1)).rows()).contains(List.of("GRADE", "P", "50.0", "100.0", "true", "1", "50.0"),
                    List.of("INTERVAL", "90-100%", "90", "100", "", "1", "50.0"));
        }
        finally {
            Locale.setDefault(original);
            Locale.setDefault(Locale.Category.DISPLAY, originalDisplay);
            Locale.setDefault(Locale.Category.FORMAT, originalFormat);
        }
    }

    private static GradingScale gradingScale() {
        var scale = new GradingScale();
        var passed = new GradeStep();
        passed.setGradeName("P");
        passed.setLowerBoundPercentage(50.0);
        passed.setUpperBoundPercentage(100.0);
        passed.setIsPassingGrade(true);
        var failed = new GradeStep();
        failed.setGradeName("F");
        failed.setLowerBoundPercentage(0.0);
        failed.setUpperBoundPercentage(50.0);
        failed.setIsPassingGrade(false);
        scale.setGradeSteps(Set.of(passed, failed));
        return scale;
    }

    private List<Path> exportCourseData() {
        when(courseRepository.findByIdWithEagerExercisesElseThrow(COURSE_ID)).thenReturn(course);
        var errors = new ArrayList<String>();
        var files = service.exportAllStudentData(COURSE_ID, outputDirectory, errors);
        assertThat(errors).isEmpty();
        return files;
    }

    private CourseStudentDataExportService createService(boolean optionalModules) {
        return new CourseStudentDataExportService(participationRepository, postRepository, answerPostRepository, llmTokenUsageTraceRepository, courseRepository,
                optionalModules ? Optional.of(competencyProgressApi) : Optional.empty(), optionalModules ? Optional.of(learnerProfileApi) : Optional.empty(),
                optionalModules ? Optional.of(irisSettingsApi) : Optional.empty(), optionalModules ? Optional.of(tutorialGroupApi) : Optional.empty(), gradingScaleRepository,
                studentParticipationRepository, userRepository, MAPPER, feedbackSynthesizer);
    }

    private static StudentResultDTO student(String name, Boolean submitted, Double points, Double score, String grade, Boolean passed, Map<Long, ExerciseResultDTO> results) {
        return new StudentResultDTO(1L, name, "student@example.org", "student", "123", submitted, points, score, grade, null, passed, null, null, results, null);
    }

    private static ExamScoresDTO exam(String title, Integer maxPoints, Boolean secondCorrection, List<ExerciseGroupDTO> groups, List<StudentResultDTO> students) {
        return new ExamScoresDTO(EXAM_ID, title, maxPoints, null, secondCorrection, groups, students);
    }

    private static CsvData readCsv(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path, StandardCharsets.UTF_8); var parser = CSVParser.parse(reader, CSVFormat.RFC4180.builder().setHeader().get())) {
            return new CsvData(parser.getHeaderNames(), parser.getRecords().stream().map(CSVRecord::toList).toList());
        }
    }

    private record CsvData(List<String> headers, List<List<String>> rows) {

        List<String> row(String name) {
            return rows.stream().filter(row -> row.getFirst().equals(name)).findFirst().orElseThrow();
        }

        String value(String name, String column) {
            return row(name).get(headers.indexOf(column));
        }
    }
}
