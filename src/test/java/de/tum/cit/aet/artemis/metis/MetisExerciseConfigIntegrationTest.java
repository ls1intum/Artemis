package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.AdditionalAnswers;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseDefaultConfigurationsRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisFailureReason;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.dto.MetisExerciseConfigDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisGroupWindowDTO;
import de.tum.cit.aet.artemis.metis.dto.MetisTutorialGroupDTO;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.metis.service.MetisExerciseConfigService;
import de.tum.cit.aet.artemis.metis.util.MetisUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * The Metis settings of an exercise through their REST endpoints: the configuration with its defaults, its validation and the checks a saved change moves, the
 * windows of the tutorial groups with their replace semantics, and the tutorial groups of the course, each against the roles that may and may not use it.
 * <p>
 * Every programming exercise owns its configuration row from the moment the fixtures store it, as in production, so the settings endpoints change that row in
 * place and never insert or delete it. Only a row that a creation which failed half-way left out is added, with the defaults; the tests simulate that creation
 * by deleting the row.
 * <p>
 * The database of the bucket is shared with every other test class, and claims, check creation and the sweeps consider every enabled configuration. Every
 * configuration a method switched on is therefore switched off again afterwards, so that no check of this class is ever claimed elsewhere.
 */
class MetisExerciseConfigIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisexconfig";

    /** The repair insert writes only the key, so the column defaults fill the row; Hibernate may render the table with an alias. */
    private static final Pattern INSERT_OF_THE_KEY_ONLY = Pattern.compile("insert into metis_exercise_config( as \\w+)?\\s*\\(exercise_id\\) values .*", Pattern.DOTALL);

    /** The window of the fixtures. Every other time is an offset from it, in whole seconds, which the database keeps exactly. */
    private static final ZonedDateTime WINDOW_START = ZonedDateTime.parse("2024-03-01T10:00:00Z");

    private static final ZonedDateTime WINDOW_END = WINDOW_START.plusHours(2);

    private static final String COMMIT_HASH = "a".repeat(40);

    private static final String HINT = "Ask about the recursion.";

    /** The error key of a request that bean validation rejects. */
    private static final String VALIDATION = "validation";

    /** An exercise id no exercise has. */
    private static final long MISSING_EXERCISE_ID = Long.MAX_VALUE / 2;

    /** The columns are datetime(3) without a zone, so what comes back is the same instant, not necessarily the same zone. */
    private static final BiPredicate<ZonedDateTime, ZonedDateTime> SAME_INSTANT = (left, right) -> left.toInstant().equals(right.toInstant());

    private static final RecursiveComparisonConfiguration SAME_INSTANTS = RecursiveComparisonConfiguration.builder().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).build();

    /** A statement that inserts, updates or deletes rows of the configuration table, as Hibernate executes it, in lower case. */
    private static final Pattern WRITE_OF_THE_CONFIGURATION = Pattern.compile("^(?:insert into|update|delete from) metis_exercise_config\\b");

    /** A statement that inserts a window of a tutorial group, as Hibernate executes it, in lower case. */
    private static final Pattern INSERT_OF_A_GROUP_WINDOW = Pattern.compile("^insert into metis_group_window\\b");

    @Autowired
    private MetisUtilService metisUtilService;

    @Autowired
    private MetisExerciseConfigRepository configRepository;

    @Autowired
    private MetisGroupWindowRepository groupWindowRepository;

    @Autowired
    private MetisCheckRepository checkRepository;

    @Autowired
    private MetisQuestionRepository questionRepository;

    @Autowired
    private ExerciseDefaultConfigurationsRepository exerciseDefaultConfigurationsRepository;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    /** Every exercise the current method created; the configurations among them that are switched on are switched off afterwards. */
    private final List<Long> exerciseIds = new ArrayList<>();

    private Course course;

    private ProgrammingExercise exercise;

    private long exerciseId;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 4, 1, 1, 1);
        course = programmingExerciseUtilService.addEnrolledCourseWithOneProgrammingExercise(TEST_PREFIX);
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        exerciseId = exercise.getId();
        exerciseIds.add(exerciseId);
    }

    @AfterEach
    void switchOffTheEnabledConfigurations() {
        for (long id : exerciseIds) {
            configRepository.findById(id).filter(MetisExerciseConfig::isEnabled).ifPresent(this::switchOff);
        }
        exerciseIds.clear();
    }

    @Nested
    class ReadConfiguration {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnTheRowEveryProgrammingExerciseOwnsWithTheDefaults() throws Exception {
            // Nobody has saved a configuration, but the fixture stored the exercise the way every creation path does, so the row is there.
            assertThat(exerciseDefaultConfigurationsRepository.hasMetisExerciseConfig(exerciseId)).isTrue();

            MetisExerciseConfigDTO actualConfig = request.get(configPath(exerciseId), HttpStatus.OK, MetisExerciseConfigDTO.class);

            assertThat(actualConfig).isEqualTo(defaultSettings(exerciseId));
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnTheDefaultsWithoutWritingAnythingWhenTheRowIsMissing() throws Exception {
            deleteConfigurationRow(exerciseId);

            MetisExerciseConfigDTO actualConfig = request.get(configPath(exerciseId), HttpStatus.OK, MetisExerciseConfigDTO.class);

            // A read never writes: the next save of the settings or of the exercise adds the row.
            assertThat(actualConfig).isEqualTo(defaultSettings(exerciseId));
            assertThat(configRepository.existsById(exerciseId)).isFalse();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldReturnTheStoredConfigurationWhenAnEditorChangedIt() throws Exception {
            MetisExerciseConfig stored = metisUtilService.saveConfig(exerciseId, false, true, WINDOW_START, WINDOW_END);
            stored.setMainQuestionCount(4);
            stored.setInstructorHint(HINT);
            configRepository.save(stored);

            MetisExerciseConfigDTO actualConfig = request.get(configPath(exerciseId), HttpStatus.OK, MetisExerciseConfigDTO.class);

            var expectedConfig = new MetisExerciseConfigDTO(exerciseId, false, WINDOW_START, WINDOW_END, 4, 2, 60, 300, 3, 3, 5, 3, true, HINT);
            assertThat(actualConfig).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedConfig);
        }
    }

    @Nested
    class SaveConfiguration {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldChangeThePermanentRowInPlaceOnEverySave() throws Exception {
            // A window references the row, and its key cascades: a save that deleted the row and inserted it again would take the window with it.
            MetisGroupWindow window = metisUtilService.saveGroupWindow(exerciseId, tutorialGroup(course, "Metis Monday").getId(), WINDOW_START, WINDOW_END);

            var firstSave = new MetisExerciseConfigDTO(exerciseId, true, WINDOW_START, WINDOW_END, 4, 1, 90, 600, 2, 4, 7, 6, true, HINT);
            queryInterceptor.startQueryCount();
            MetisExerciseConfigDTO actualFirstSave = saveConfig(firstSave);
            List<String> writesOfTheFirstSave = writesOfTheConfiguration(queryInterceptor.getStatements());

            var secondSave = new MetisExerciseConfigDTO(exerciseId, false, null, WINDOW_END.plusDays(1), 1, 0, 30, 3600, 5, 1, 5, 1, false, null);
            queryInterceptor.startQueryCount();
            MetisExerciseConfigDTO actualSecondSave = saveConfig(secondSave);
            List<String> writesOfTheSecondSave = writesOfTheConfiguration(queryInterceptor.getStatements());

            assertThat(actualFirstSave).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(firstSave);
            assertThat(actualSecondSave).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(secondSave);
            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(secondSave);
            MetisExerciseConfigDTO actualRead = request.get(configPath(exerciseId), HttpStatus.OK, MetisExerciseConfigDTO.class);
            assertThat(actualRead).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(secondSave);

            // Each save wrote the row with exactly one update; it was neither deleted nor inserted, so it is still one row and still has its window.
            assertThat(writesOfTheFirstSave).as("statements of the first save that write the configuration").singleElement().asString().startsWith("update");
            assertThat(writesOfTheSecondSave).as("statements of the second save that write the configuration").singleElement().asString().startsWith("update");
            assertThat(exerciseDefaultConfigurationsRepository.hasMetisExerciseConfig(exerciseId)).isTrue();
            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).extracting(MetisGroupWindow::getId).containsExactly(window.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRepairAMissingRowAndThenUpdateIt() throws Exception {
            deleteConfigurationRow(exerciseId);
            var settings = new MetisExerciseConfigDTO(exerciseId, true, WINDOW_START, WINDOW_END, 4, 1, 90, 600, 2, 4, 7, 6, true, HINT);

            queryInterceptor.startQueryCount();
            MetisExerciseConfigDTO actualConfig = saveConfig(settings);
            List<String> writes = writesOfTheConfiguration(queryInterceptor.getStatements());

            // The row is added with nothing but its key, so the column defaults fill it, and the same update as for every other save writes the settings.
            assertThat(writes).as("statements that write the configuration").hasSize(2);
            assertThat(writes.getFirst()).as("the repair inserts the key only").matches(INSERT_OF_THE_KEY_ONLY);
            assertThat(writes.getLast()).startsWith("update");
            assertThat(actualConfig).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(settings);
            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(settings);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldNeverGiveAnExerciseOfAnotherTypeAConfiguration() throws Exception {
            TextExercise textExercise = textExerciseUtilService.createSampleTextExercise(course);
            long textExerciseId = textExercise.getId();

            // Switched off, the settings pass every check of their own, yet only a programming exercise owns a configuration.
            request.putAndExpectError(configPath(textExerciseId), settings(textExerciseId, false, false, null, null), HttpStatus.BAD_REQUEST, "metis.unsupportedExercise");
            request.putAndExpectError(groupWindowsPath(textExerciseId), List.of(), HttpStatus.BAD_REQUEST, "metis.unsupportedExercise");

            assertThat(configRepository.existsById(textExerciseId)).isFalse();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("invalidConfigurations")
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheConfigurationWhenAFieldIsInvalid(String description, String field, @Nullable Object value, String expectedErrorKey) throws Exception {
            Map<String, Object> body = validConfigurationBody(exerciseId);
            body.put(field, value);

            request.putAndExpectError(configPath(exerciseId), body, HttpStatus.BAD_REQUEST, expectedErrorKey);

            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        /**
         * One invalid field each, replacing a field of the valid body: 3 main questions, 2 follow-ups, 60 to 300 seconds, a 5-point scale with the threshold 3,
         * and a window of two hours.
         */
        private static Stream<Arguments> invalidConfigurations() {
            List<Arguments> cases = new ArrayList<>();
            cases.add(Arguments.of("no exercise id", "exerciseId", null, VALIDATION));
            cases.add(Arguments.of("no enabled flag", "enabled", null, VALIDATION));
            cases.add(Arguments.of("no main question count", "mainQuestionCount", null, VALIDATION));
            cases.add(Arguments.of("no main question", "mainQuestionCount", 0, VALIDATION));
            cases.add(Arguments.of("six main questions", "mainQuestionCount", 6, VALIDATION));
            cases.add(Arguments.of("no follow-up limit", "maxFollowUpsPerQuestion", null, VALIDATION));
            cases.add(Arguments.of("a negative follow-up limit", "maxFollowUpsPerQuestion", -1, VALIDATION));
            cases.add(Arguments.of("four follow-ups", "maxFollowUpsPerQuestion", 4, VALIDATION));
            cases.add(Arguments.of("no shortest time limit", "minSecondsPerQuestion", null, VALIDATION));
            cases.add(Arguments.of("a shortest time limit under 30 seconds", "minSecondsPerQuestion", 29, VALIDATION));
            cases.add(Arguments.of("a shortest time limit over an hour", "minSecondsPerQuestion", 3601, VALIDATION));
            cases.add(Arguments.of("no longest time limit", "maxSecondsPerQuestion", null, VALIDATION));
            cases.add(Arguments.of("a longest time limit under 30 seconds", "maxSecondsPerQuestion", 29, VALIDATION));
            cases.add(Arguments.of("a longest time limit over an hour", "maxSecondsPerQuestion", 3601, VALIDATION));
            cases.add(Arguments.of("a shortest time limit above the longest", "minSecondsPerQuestion", 301, "metis.timeLimitOrder"));
            cases.add(Arguments.of("a longest time limit below the shortest", "maxSecondsPerQuestion", 59, "metis.timeLimitOrder"));
            cases.add(Arguments.of("no answer length", "answerLength", null, VALIDATION));
            cases.add(Arguments.of("answer length 0", "answerLength", 0, VALIDATION));
            cases.add(Arguments.of("answer length 6", "answerLength", 6, VALIDATION));
            cases.add(Arguments.of("no complexity", "complexity", null, VALIDATION));
            cases.add(Arguments.of("complexity 0", "complexity", 0, VALIDATION));
            cases.add(Arguments.of("complexity 6", "complexity", 6, VALIDATION));
            cases.add(Arguments.of("no rating scale", "likertScalePoints", null, VALIDATION));
            cases.add(Arguments.of("a 4-point scale", "likertScalePoints", 4, VALIDATION));
            cases.add(Arguments.of("a 6-point scale", "likertScalePoints", 6, "metis.likertScale"));
            cases.add(Arguments.of("an 8-point scale", "likertScalePoints", 8, VALIDATION));
            cases.add(Arguments.of("no follow-up threshold", "followUpThreshold", null, VALIDATION));
            cases.add(Arguments.of("follow-up threshold 0", "followUpThreshold", 0, VALIDATION));
            cases.add(Arguments.of("a follow-up threshold at the top of the scale", "followUpThreshold", 5, "metis.followUpThreshold"));
            cases.add(Arguments.of("a follow-up threshold above the scale", "followUpThreshold", 6, "metis.followUpThreshold"));
            cases.add(Arguments.of("a follow-up threshold above every scale", "followUpThreshold", 7, VALIDATION));
            cases.add(Arguments.of("no review flag", "reviewRequired", null, VALIDATION));
            cases.add(Arguments.of("a window that ends when it starts", "windowEnd", WINDOW_START, "metis.windowOrder"));
            cases.add(Arguments.of("a window that ends before it starts", "windowEnd", WINDOW_START.minusMinutes(1), "metis.windowOrder"));
            cases.add(Arguments.of("a hint over 2000 characters", "instructorHint", "x".repeat(2001), VALIDATION));
            return cases.stream();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectSwitchingTheCheckOnWhenTheExerciseIsATeamExercise() throws Exception {
            Exercise teamExercise = exerciseRepository.findByIdElseThrow(exerciseId);
            teamExercise.setMode(ExerciseMode.TEAM);
            exerciseRepository.save(teamExercise);

            request.putAndExpectError(configPath(exerciseId), settings(exerciseId, true, false, null, null), HttpStatus.BAD_REQUEST, "metis.unsupportedExercise");
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));

            // Only switching the check on is refused: switched off, the settings of a team exercise are stored like any others.
            var switchedOff = new MetisExerciseConfigDTO(exerciseId, false, null, null, 4, 2, 60, 300, 3, 3, 5, 3, true, HINT);
            saveConfig(switchedOff);
            assertThat(storedConfig()).isEqualTo(switchedOff);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectSwitchingTheCheckOnWhenTheExerciseBelongsToAnExam() throws Exception {
            long examExerciseId = examExercise().getId();

            request.putAndExpectError(configPath(examExerciseId), settings(examExerciseId, true, false, null, null), HttpStatus.BAD_REQUEST, "metis.unsupportedExercise");

            // A programming exercise of an exam owns its row like any other; it keeps the defaults.
            assertThat(storedConfig(examExerciseId)).isEqualTo(defaultSettings(examExerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldSwitchTheCheckOnWhenTheExerciseHasNoDueDateYet() throws Exception {
            exerciseUtilService.updateExerciseDueDate(exerciseId, null);
            var switchedOn = settings(exerciseId, true, false, null, null);

            saveConfig(switchedOn);

            assertThat(storedConfig()).isEqualTo(switchedOn);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheConfigurationWhenTheBodyNamesAnotherExercise() throws Exception {
            long otherExerciseId = otherExercise().getId();

            request.put(configPath(exerciseId), settings(otherExerciseId, true, true, null, null), HttpStatus.CONFLICT);

            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
            assertThat(storedConfig(otherExerciseId)).isEqualTo(defaultSettings(otherExerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldLockTheRatingScaleWhenAStudentHasStartedTheCheck() throws Exception {
            generatedCheck(exercise, "student1", MetisCheckStatus.READY);
            generatedCheck(otherExercise(), "student3", MetisCheckStatus.IN_PROGRESS);

            // Nobody has started the check of this exercise yet, so the scale may still change: a start in another exercise does not count.
            saveConfig(scaledSettings(exerciseId, 7, 4));
            assertThat(configRepository.findByIdElseThrow(exerciseId).getLikertScalePoints()).isEqualTo(7);

            // Once a student has started, the statement refuses a new scale, and nothing else of the refused request is written either.
            generatedCheck(exercise, "student2", MetisCheckStatus.IN_PROGRESS);
            var otherScaleAndMoreQuestions = new MetisExerciseConfigDTO(exerciseId, false, null, null, 5, 2, 60, 300, 3, 3, 5, 3, false, null);
            request.putAndExpectError(configPath(exerciseId), otherScaleAndMoreQuestions, HttpStatus.CONFLICT, "metis.scaleLocked");
            assertThat(storedConfig()).isEqualTo(scaledSettings(exerciseId, 7, 4));

            // The other settings can still change, as long as the scale stays as it is.
            var moreQuestions = new MetisExerciseConfigDTO(exerciseId, false, null, null, 4, 2, 60, 300, 3, 3, 7, 4, false, null);
            saveConfig(moreQuestions);
            assertThat(storedConfig()).isEqualTo(moreQuestions);
        }

        @Test
        void shouldRefuseANewScaleWhenAStudentStartsWhileTheSettingsAreSaved() {
            // Every statement of the repository runs for real, but right before the update a student starts the check, after everything the request read.
            Answer<Object> realStatement = AdditionalAnswers.delegatesTo(configRepository);
            MetisExerciseConfigRepository racingRepository = mock(MetisExerciseConfigRepository.class, invocation -> {
                if ("updateSettings".equals(invocation.getMethod().getName())) {
                    generatedCheck(exercise, "student1", MetisCheckStatus.IN_PROGRESS);
                }
                return realStatement.answer(invocation);
            });
            var service = new MetisExerciseConfigService(racingRepository, groupWindowRepository, checkRepository);

            // The rule is part of the statement, so the start that came after every read still locks the scale, and nothing of the request is written.
            assertThatExceptionOfType(ConflictException.class).isThrownBy(() -> service.updateConfig(exerciseId, scaledSettings(exerciseId, 7, 4)))
                    .extracting(ConflictException::getErrorKey).isEqualTo("metis.scaleLocked");
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        void shouldAnswerNotFoundWhenTheRowDisappearsBetweenTheReadAndTheUpdate() {
            // Every statement of the repository runs for real, but right before the update the row goes, as it does when the exercise is deleted at that moment.
            Answer<Object> realStatement = AdditionalAnswers.delegatesTo(configRepository);
            MetisExerciseConfigRepository racingRepository = mock(MetisExerciseConfigRepository.class, invocation -> {
                if ("updateSettings".equals(invocation.getMethod().getName())) {
                    deleteConfigurationRow(exerciseId);
                }
                return realStatement.answer(invocation);
            });
            var service = new MetisExerciseConfigService(racingRepository, groupWindowRepository, checkRepository);

            // The statement changes no row, and since the row is gone, that is no locked scale but a missing exercise.
            assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> service.updateConfig(exerciseId, settings(exerciseId, false, true, null, null)));
            assertThat(configRepository.existsById(exerciseId)).isFalse();
        }

        @ParameterizedTest(name = "review required: {0}")
        @ValueSource(booleans = { true, false })
        void shouldMoveTheChecksToTheStoredReviewWhenAParallelSaveSwitchedItInBetween(boolean reviewRequired) {
            metisUtilService.saveConfig(exerciseId, false, reviewRequired, null, null);
            MetisCheckStatus statusTheReviewDemands = reviewRequired ? MetisCheckStatus.IN_REVIEW : MetisCheckStatus.READY;
            MetisCheck check = generatedCheck(exercise, "student1", statusTheReviewDemands);
            mainQuestion(check, 1, false);
            // Every statement of the repository runs for real, but right before the update a parallel save switches the review the other way and moves the check.
            // The request read the row before that and keeps the review it read, so compared with what it read, its own save does not change the review.
            var parallelService = new MetisExerciseConfigService(configRepository, groupWindowRepository, checkRepository);
            List<MetisCheckStatus> statusAfterTheParallelSave = new ArrayList<>();
            Answer<Object> realStatement = AdditionalAnswers.delegatesTo(configRepository);
            MetisExerciseConfigRepository racingRepository = mock(MetisExerciseConfigRepository.class, invocation -> {
                if ("updateSettings".equals(invocation.getMethod().getName())) {
                    parallelService.updateConfig(exerciseId, settings(exerciseId, false, !reviewRequired, null, null));
                    statusAfterTheParallelSave.add(statusOf(check));
                }
                return realStatement.answer(invocation);
            });
            var service = new MetisExerciseConfigService(racingRepository, groupWindowRepository, checkRepository);

            service.updateConfig(exerciseId, settings(exerciseId, false, reviewRequired, null, null));

            // The parallel save moved the check, and then the request stored its review again: the check follows what is stored, not what the request changed.
            assertThat(statusAfterTheParallelSave).containsExactly(reviewRequired ? MetisCheckStatus.READY : MetisCheckStatus.IN_REVIEW);
            assertThat(configRepository.findByIdElseThrow(exerciseId).isReviewRequired()).isEqualTo(reviewRequired);
            assertThat(statusOf(check)).isEqualTo(statusTheReviewDemands);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldReleaseEveryCheckInReviewWhenTheReviewIsSwitchedOff() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, true, null, null);
            MetisCheck unapproved = generatedCheck(exercise, "student1", MetisCheckStatus.IN_REVIEW);
            mainQuestion(unapproved, 1, false);
            MetisCheck approved = generatedCheck(exercise, "student2", MetisCheckStatus.IN_REVIEW);
            mainQuestion(approved, 1, true);
            MetisCheck inReviewOfOtherExercise = generatedCheck(otherExercise(), "student1", MetisCheckStatus.IN_REVIEW);
            mainQuestion(inReviewOfOtherExercise, 1, false);

            // A save that keeps the review on moves nothing.
            saveConfig(settings(exerciseId, false, true, null, null));
            assertThat(statusOf(unapproved)).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(statusOf(approved)).isEqualTo(MetisCheckStatus.IN_REVIEW);

            saveConfig(settings(exerciseId, false, false, null, null));
            assertThat(statusOf(unapproved)).isEqualTo(MetisCheckStatus.READY);
            assertThat(statusOf(approved)).isEqualTo(MetisCheckStatus.READY);
            assertThat(statusOf(inReviewOfOtherExercise)).isEqualTo(MetisCheckStatus.IN_REVIEW);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldReturnUnstartedReadyChecksWithAnUnapprovedMainQuestionToReviewWhenTheReviewIsSwitchedOn() throws Exception {
            MetisCheck partlyApproved = generatedCheck(exercise, "student1", MetisCheckStatus.READY);
            mainQuestion(partlyApproved, 1, true);
            mainQuestion(partlyApproved, 2, false);
            MetisCheck fullyApproved = generatedCheck(exercise, "student2", MetisCheckStatus.READY);
            mainQuestion(fullyApproved, 1, true);
            mainQuestion(fullyApproved, 2, true);
            MetisCheck started = generatedCheck(exercise, "student3", MetisCheckStatus.IN_PROGRESS);
            mainQuestion(started, 1, false);
            MetisCheck completed = generatedCheck(exercise, "student4", MetisCheckStatus.COMPLETED);
            mainQuestion(completed, 1, false);
            MetisCheck readyOfOtherExercise = generatedCheck(otherExercise(), "student1", MetisCheckStatus.READY);
            mainQuestion(readyOfOtherExercise, 1, false);

            saveConfig(settings(exerciseId, false, true, null, null));

            assertThat(statusOf(partlyApproved)).isEqualTo(MetisCheckStatus.IN_REVIEW);
            assertThat(statusOf(fullyApproved)).isEqualTo(MetisCheckStatus.READY);
            assertThat(statusOf(started)).isEqualTo(MetisCheckStatus.IN_PROGRESS);
            assertThat(statusOf(completed)).isEqualTo(MetisCheckStatus.COMPLETED);
            assertThat(statusOf(readyOfOtherExercise)).isEqualTo(MetisCheckStatus.READY);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRequeueTheChecksSkippedForAClosedWindowOnlyWhenTheWindowChanges() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, false, WINDOW_START, WINDOW_END);
            MetisCheck windowClosed = unavailableCheck(exercise, "student1", MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            MetisCheck noSubmission = unavailableCheck(exercise, "student2", MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            MetisCheck llmUnavailable = unavailableCheck(exercise, "student3", MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE);
            MetisCheck ofOtherExercise = unavailableCheck(otherExercise(), "student1", MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);

            // The same window written in another zone, and another hint, are no change of the window.
            ZoneId vienna = ZoneId.of("Europe/Vienna");
            ZonedDateTime viennaStart = WINDOW_START.withZoneSameInstant(vienna);
            ZonedDateTime viennaEnd = WINDOW_END.withZoneSameInstant(vienna);
            saveConfig(new MetisExerciseConfigDTO(exerciseId, false, viennaStart, viennaEnd, 3, 2, 60, 300, 3, 3, 5, 3, false, HINT));
            assertStoredState(windowClosed, MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);

            // A later end judges the check again; the other reasons stay, and so do the checks of other exercises.
            saveConfig(settings(exerciseId, false, false, WINDOW_START, WINDOW_END.plusHours(1)));
            assertStoredState(windowClosed, MetisCheckStatus.PENDING, null);
            assertStoredState(noSubmission, MetisCheckStatus.SKIPPED, MetisFailureReason.NO_SUBMISSION);
            assertStoredState(llmUnavailable, MetisCheckStatus.FAILED, MetisFailureReason.LLM_UNAVAILABLE);
            assertStoredState(ofOtherExercise, MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);

            // So does a changed start: the window now opens at the due date.
            skipForAClosedWindow(windowClosed);
            saveConfig(settings(exerciseId, false, false, null, WINDOW_END.plusHours(1)));
            assertStoredState(windowClosed, MetisCheckStatus.PENDING, null);
        }
    }

    @Nested
    class GroupWindows {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldInsertUpdateAndDeleteTheWindowsByGroupAndRequeueTheChecksSkippedForAClosedWindow() throws Exception {
            metisUtilService.saveConfig(exerciseId, false, true, WINDOW_START, WINDOW_END);
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");
            TutorialGroup tuesday = tutorialGroup(course, "Metis Tuesday");
            TutorialGroup wednesday = tutorialGroup(course, "Metis Wednesday");
            MetisGroupWindow storedMonday = metisUtilService.saveGroupWindow(exerciseId, monday.getId(), WINDOW_START, WINDOW_END);
            MetisGroupWindow storedTuesday = metisUtilService.saveGroupWindow(exerciseId, tuesday.getId(), WINDOW_START.plusDays(1), WINDOW_END.plusDays(1));
            MetisCheck windowClosed = unavailableCheck(exercise, "student1", MetisCheckStatus.SKIPPED, MetisFailureReason.WINDOW_CLOSED);
            long otherExerciseId = otherExercise().getId();

            // Monday moves, Tuesday is left out and Wednesday is new. The id and the exercise of a window in the body do not count: the new window names the id
            // of Tuesday's window and another exercise, and still becomes a new window of this exercise.
            ZonedDateTime wednesdayStart = WINDOW_START.plusDays(2);
            ZonedDateTime wednesdayEnd = WINDOW_END.plusDays(2);
            var newWednesday = new MetisGroupWindowDTO(storedTuesday.getId(), otherExerciseId, wednesday.getId(), wednesdayStart, wednesdayEnd);
            var movedMonday = new MetisGroupWindowDTO(null, null, monday.getId(), WINDOW_START.plusHours(1), WINDOW_END.plusHours(1));
            List<MetisGroupWindowDTO> actualWindows = saveWindows(List.of(newWednesday, movedMonday));

            // The windows come in the order they were created: Monday's window was updated in place, Wednesday's is the newest.
            assertThat(actualWindows).hasSize(2);
            var expectedMonday = new MetisGroupWindowDTO(storedMonday.getId(), exerciseId, monday.getId(), WINDOW_START.plusHours(1), WINDOW_END.plusHours(1));
            assertThat(actualWindows.getFirst()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedMonday);
            MetisGroupWindowDTO actualWednesday = actualWindows.get(1);
            assertThat(actualWednesday.id()).isNotIn(storedMonday.getId(), storedTuesday.getId());
            var expectedWednesday = new MetisGroupWindowDTO(actualWednesday.id(), exerciseId, wednesday.getId(), wednesdayStart, wednesdayEnd);
            assertThat(actualWednesday).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedWednesday);
            List<MetisGroupWindowDTO> actualRead = request.getList(groupWindowsPath(exerciseId), HttpStatus.OK, MetisGroupWindowDTO.class);
            assertThat(actualRead).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(actualWindows);
            assertThat(groupWindowRepository.findAllByExerciseId(otherExerciseId)).isEmpty();

            assertThat(statusOf(windowClosed)).isEqualTo(MetisCheckStatus.PENDING);
            // The configuration the windows belong to keeps its settings.
            var expectedConfig = new MetisExerciseConfigDTO(exerciseId, false, WINDOW_START, WINDOW_END, 3, 2, 60, 300, 3, 3, 5, 3, true, null);
            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(expectedConfig);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldStoreTheWindowsWithoutWritingTheConfiguration() throws Exception {
            var editedConfig = new MetisExerciseConfigDTO(exerciseId, false, WINDOW_START, WINDOW_END, 4, 1, 90, 600, 2, 4, 7, 6, true, HINT);
            saveConfig(editedConfig);
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");

            queryInterceptor.startQueryCount();
            saveWindows(List.of(window(monday, WINDOW_START, WINDOW_END)));
            List<String> statements = queryInterceptor.getStatements();

            // The recording holds the statements of the request, since it caught the insert of the window. The windows hang off the row the exercise owns, and
            // none of those statements writes the row, so every setting of the editor stays.
            assertThat(statementsMatching(statements, INSERT_OF_A_GROUP_WINDOW)).as("statements that insert the window").hasSize(1);
            assertThat(writesOfTheConfiguration(statements)).as("statements that write the configuration").isEmpty();
            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(editedConfig);
            assertThat(exerciseDefaultConfigurationsRepository.hasMetisExerciseConfig(exerciseId)).isTrue();
            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).extracting(MetisGroupWindow::getTutorialGroupId).containsExactly(monday.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRepairAMissingRowWithTheDefaultsBeforeStoringTheWindows() throws Exception {
            deleteConfigurationRow(exerciseId);
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");

            saveWindows(List.of(window(monday, WINDOW_START, WINDOW_END)));

            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).extracting(MetisGroupWindow::getTutorialGroupId).containsExactly(monday.getId());
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldDeleteEveryWindowWhenTheRequestIsEmpty() throws Exception {
            metisUtilService.saveGroupWindow(exerciseId, tutorialGroup(course, "Metis Monday").getId(), WINDOW_START, WINDOW_END);
            metisUtilService.saveGroupWindow(exerciseId, tutorialGroup(course, "Metis Tuesday").getId(), WINDOW_START, WINDOW_END);

            List<MetisGroupWindowDTO> actualWindows = saveWindows(List.of());

            assertThat(actualWindows).isEmpty();
            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).isEmpty();
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheWindowsWhenAGroupAppearsTwice() throws Exception {
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");
            var first = window(monday, WINDOW_START, WINDOW_END);
            var second = window(monday, WINDOW_START.plusDays(7), WINDOW_END.plusDays(7));

            request.putAndExpectError(groupWindowsPath(exerciseId), List.of(first, second), HttpStatus.BAD_REQUEST, "metis.duplicateTutorialGroup");

            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).isEmpty();
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheWindowsWhenAGroupBelongsToAnotherCourse() throws Exception {
            TutorialGroup ownGroup = tutorialGroup(course, "Metis Monday");
            TutorialGroup groupOfOtherCourse = tutorialGroup(courseUtilService.addEmptyCourse(), "Metis Tuesday");
            var missingGroupWindow = new MetisGroupWindowDTO(null, null, Long.MAX_VALUE / 2, WINDOW_START, WINDOW_END);

            List<MetisGroupWindowDTO> withForeignGroup = List.of(window(ownGroup, WINDOW_START, WINDOW_END), window(groupOfOtherCourse, WINDOW_START, WINDOW_END));
            request.putAndExpectError(groupWindowsPath(exerciseId), withForeignGroup, HttpStatus.BAD_REQUEST, "metis.tutorialGroupNotInCourse");
            request.putAndExpectError(groupWindowsPath(exerciseId), List.of(missingGroupWindow), HttpStatus.BAD_REQUEST, "metis.tutorialGroupNotInCourse");

            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).isEmpty();
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheWindowsWhenAWindowDoesNotEndAfterItStarts() throws Exception {
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");

            request.putAndExpectError(groupWindowsPath(exerciseId), List.of(window(monday, WINDOW_END, WINDOW_START)), HttpStatus.BAD_REQUEST, "metis.windowOrder");
            request.putAndExpectError(groupWindowsPath(exerciseId), List.of(window(monday, WINDOW_START, WINDOW_START)), HttpStatus.BAD_REQUEST, "metis.windowOrder");

            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheWindowsWhenAWindowLacksAnEndOrIsMissing() throws Exception {
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");
            var withoutEnd = new MetisGroupWindowDTO(null, null, monday.getId(), WINDOW_START, null);

            request.put(groupWindowsPath(exerciseId), List.of(withoutEnd), HttpStatus.BAD_REQUEST);
            request.put(groupWindowsPath(exerciseId), Arrays.asList(window(monday, WINDOW_START, WINDOW_END), null), HttpStatus.BAD_REQUEST);

            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).isEmpty();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldRejectTheWindowsWhenTheExerciseBelongsToAnExam() throws Exception {
            long examExerciseId = examExercise().getId();

            request.putAndExpectError(groupWindowsPath(examExerciseId), List.of(), HttpStatus.BAD_REQUEST, "metis.unsupportedExercise");

            assertThat(storedConfig(examExerciseId)).isEqualTo(defaultSettings(examExerciseId));
        }
    }

    /**
     * The repair of a row that a creation which failed half-way left out, in the situations a single request cannot reach: a parallel request that adds the same
     * row in between, and a violation that is not one. The statements run against the database; only the moment between the read and the insert is taken over.
     */
    @Nested
    class RowRepair {

        @Test
        void shouldNeverReplaceTheStoredSettingsWithALateRepairInsert() {
            var editedSettings = new MetisExerciseConfigDTO(exerciseId, false, WINDOW_START, WINDOW_END, 4, 1, 90, 600, 2, 4, 7, 6, true, HINT);
            configRepository.save(entityOf(editedSettings));

            // The insert of a request that found no row just before an editor's request added it and saved the settings: the primary key refuses it.
            assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> configRepository.insertDefaults(exerciseId));
            // A repair that reads first finds the row and inserts nothing.
            configRepository.ensureExistsFor(exerciseId);

            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(editedSettings);
        }

        @Test
        void shouldTolerateARepairInsertThatAParallelRequestWon() {
            deleteConfigurationRow(exerciseId);
            // The statements of the repository run for real; between the read that finds no row and the insert, a parallel request adds the same row.
            MetisExerciseConfigRepository racingRepository = mock(MetisExerciseConfigRepository.class, CALLS_REAL_METHODS);
            doAnswer(invocation -> configRepository.existsById(exerciseId)).when(racingRepository).existsById(exerciseId);
            doAnswer(invocation -> {
                // The parallel request inserts first, so the insert of this request is the one the primary key refuses.
                configRepository.insertDefaults(exerciseId);
                configRepository.insertDefaults(exerciseId);
                return null;
            }).when(racingRepository).insertDefaults(exerciseId);

            racingRepository.ensureExistsFor(exerciseId);

            verify(racingRepository, times(1)).insertDefaults(exerciseId);
            verify(racingRepository, times(2)).existsById(exerciseId);
            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        void shouldRethrowAViolationThatLeavesNoRow() {
            // The exercise does not exist, so the key to it refuses the insert, and no parallel request can have added the row either.
            assertThatExceptionOfType(DataIntegrityViolationException.class).isThrownBy(() -> configRepository.ensureExistsFor(MISSING_EXERCISE_ID));

            assertThat(configRepository.existsById(MISSING_EXERCISE_ID)).isFalse();
        }
    }

    @Nested
    class TutorialGroups {

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldListTheTutorialGroupsOfTheCourseOrderedByTitle() throws Exception {
            TutorialGroup tuesday = tutorialGroup(course, "Metis Tuesday");
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");
            tutorialGroup(courseUtilService.addEmptyCourse(), "Metis Friday");

            List<MetisTutorialGroupDTO> actualGroups = request.getList(tutorialGroupsPath(course.getId()), HttpStatus.OK, MetisTutorialGroupDTO.class);

            assertThat(actualGroups).containsExactly(new MetisTutorialGroupDTO(monday.getId(), "Metis Monday"), new MetisTutorialGroupDTO(tuesday.getId(), "Metis Tuesday"));
        }
    }

    @Nested
    class Authorization {

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void shouldLetATutorReadTheConfigurationButNothingElse() throws Exception {
            request.get(configPath(exerciseId), HttpStatus.OK, MetisExerciseConfigDTO.class);
            request.put(configPath(exerciseId), settings(exerciseId, true, false, null, null), HttpStatus.FORBIDDEN);
            request.getList(groupWindowsPath(exerciseId), HttpStatus.FORBIDDEN, MetisGroupWindowDTO.class);
            request.put(groupWindowsPath(exerciseId), List.of(), HttpStatus.FORBIDDEN);
            request.getList(tutorialGroupsPath(course.getId()), HttpStatus.FORBIDDEN, MetisTutorialGroupDTO.class);

            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void shouldForbidAStudentEverySettingsEndpoint() throws Exception {
            request.get(configPath(exerciseId), HttpStatus.FORBIDDEN, MetisExerciseConfigDTO.class);
            request.put(configPath(exerciseId), settings(exerciseId, true, false, null, null), HttpStatus.FORBIDDEN);
            request.getList(groupWindowsPath(exerciseId), HttpStatus.FORBIDDEN, MetisGroupWindowDTO.class);
            request.put(groupWindowsPath(exerciseId), List.of(), HttpStatus.FORBIDDEN);
            request.getList(tutorialGroupsPath(course.getId()), HttpStatus.FORBIDDEN, MetisTutorialGroupDTO.class);

            assertThat(storedConfig()).isEqualTo(defaultSettings(exerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void shouldForbidAnEditorTheSettingsOfAnotherCourse() throws Exception {
            Course otherCourse = courseUtilService.addEmptyCourse();
            long otherExerciseId = programmingExerciseUtilService.addProgrammingExerciseToCourse(otherCourse).getId();
            exerciseIds.add(otherExerciseId);

            request.get(configPath(otherExerciseId), HttpStatus.FORBIDDEN, MetisExerciseConfigDTO.class);
            request.put(configPath(otherExerciseId), settings(otherExerciseId, true, false, null, null), HttpStatus.FORBIDDEN);
            request.getList(groupWindowsPath(otherExerciseId), HttpStatus.FORBIDDEN, MetisGroupWindowDTO.class);
            request.put(groupWindowsPath(otherExerciseId), List.of(), HttpStatus.FORBIDDEN);
            request.getList(tutorialGroupsPath(otherCourse.getId()), HttpStatus.FORBIDDEN, MetisTutorialGroupDTO.class);

            assertThat(storedConfig(otherExerciseId)).isEqualTo(defaultSettings(otherExerciseId));
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void shouldLetAnInstructorChangeTheSettings() throws Exception {
            var config = settings(exerciseId, true, false, WINDOW_START, WINDOW_END);
            TutorialGroup monday = tutorialGroup(course, "Metis Monday");

            saveConfig(config);
            saveWindows(List.of(window(monday, WINDOW_START, WINDOW_END)));
            List<MetisTutorialGroupDTO> actualGroups = request.getList(tutorialGroupsPath(course.getId()), HttpStatus.OK, MetisTutorialGroupDTO.class);

            assertThat(storedConfig()).usingRecursiveComparison(SAME_INSTANTS).isEqualTo(config);
            assertThat(groupWindowRepository.findAllByExerciseId(exerciseId)).extracting(MetisGroupWindow::getTutorialGroupId).containsExactly(monday.getId());
            assertThat(actualGroups).containsExactly(new MetisTutorialGroupDTO(monday.getId(), "Metis Monday"));
        }
    }

    private static String configPath(long id) {
        return "/api/metis/exercises/" + id + "/config";
    }

    private static String groupWindowsPath(long id) {
        return "/api/metis/exercises/" + id + "/group-windows";
    }

    private static String tutorialGroupsPath(long courseId) {
        return "/api/metis/courses/" + courseId + "/tutorial-groups";
    }

    /**
     * The settings every configuration row starts with, spelled out rather than taken from the code under test: the column defaults of the changelog, switched
     * off, without a window and without a hint.
     */
    private static MetisExerciseConfigDTO defaultSettings(long id) {
        return new MetisExerciseConfigDTO(id, false, null, null, 3, 2, 60, 300, 3, 3, 5, 3, false, null);
    }

    /**
     * A valid configuration with the default limits, so that a test names only the settings it is about.
     */
    private static MetisExerciseConfigDTO settings(long id, boolean enabled, boolean reviewRequired, @Nullable ZonedDateTime start, @Nullable ZonedDateTime end) {
        return new MetisExerciseConfigDTO(id, enabled, start, end, 3, 2, 60, 300, 3, 3, 5, 3, reviewRequired, null);
    }

    /**
     * A valid configuration, switched off and without a window, with the given rating scale.
     */
    private static MetisExerciseConfigDTO scaledSettings(long id, int likertScalePoints, int followUpThreshold) {
        return new MetisExerciseConfigDTO(id, false, null, null, 3, 2, 60, 300, 3, 3, likertScalePoints, followUpThreshold, false, null);
    }

    /**
     * A valid configuration as a JSON object, so that a test can replace a field with a value of the wrong range, or leave it out.
     */
    private static Map<String, Object> validConfigurationBody(long id) {
        Map<String, Object> body = new HashMap<>();
        body.put("exerciseId", id);
        body.put("enabled", false);
        body.put("windowStart", WINDOW_START);
        body.put("windowEnd", WINDOW_END);
        body.put("mainQuestionCount", 3);
        body.put("maxFollowUpsPerQuestion", 2);
        body.put("minSecondsPerQuestion", 60);
        body.put("maxSecondsPerQuestion", 300);
        body.put("answerLength", 3);
        body.put("complexity", 3);
        body.put("likertScalePoints", 5);
        body.put("followUpThreshold", 3);
        body.put("reviewRequired", false);
        body.put("instructorHint", HINT);
        return body;
    }

    /**
     * The settings as the entity a test saves directly, bypassing the endpoint.
     */
    private static MetisExerciseConfig entityOf(MetisExerciseConfigDTO settings) {
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(settings.exerciseId());
        config.setEnabled(settings.enabled());
        config.setWindowStart(settings.windowStart());
        config.setWindowEnd(settings.windowEnd());
        config.setMainQuestionCount(settings.mainQuestionCount());
        config.setMaxFollowUpsPerQuestion(settings.maxFollowUpsPerQuestion());
        config.setMinSecondsPerQuestion(settings.minSecondsPerQuestion());
        config.setMaxSecondsPerQuestion(settings.maxSecondsPerQuestion());
        config.setAnswerLength(settings.answerLength());
        config.setComplexity(settings.complexity());
        config.setLikertScalePoints(settings.likertScalePoints());
        config.setFollowUpThreshold(settings.followUpThreshold());
        config.setReviewRequired(settings.reviewRequired());
        config.setInstructorHint(settings.instructorHint());
        return config;
    }

    private static MetisGroupWindowDTO window(TutorialGroup group, ZonedDateTime start, ZonedDateTime end) {
        return new MetisGroupWindowDTO(null, null, group.getId(), start, end);
    }

    /**
     * The statements among the given ones that insert, update or delete rows of the configuration table, in lower case and in the order they ran.
     */
    private static List<String> writesOfTheConfiguration(List<String> statements) {
        return statementsMatching(statements, WRITE_OF_THE_CONFIGURATION);
    }

    /**
     * The statements among the given ones that match a pattern of lower-case SQL, in lower case and in the order they ran.
     */
    private static List<String> statementsMatching(List<String> statements, Pattern pattern) {
        return statements.stream().map(sql -> sql.strip().toLowerCase(Locale.ROOT)).filter(sql -> pattern.matcher(sql).find()).toList();
    }

    private MetisExerciseConfigDTO saveConfig(MetisExerciseConfigDTO config) throws Exception {
        return request.putWithResponseBody(configPath(config.exerciseId()), config, MetisExerciseConfigDTO.class, HttpStatus.OK);
    }

    private List<MetisGroupWindowDTO> saveWindows(List<MetisGroupWindowDTO> windows) throws Exception {
        return request.putWithResponseBodyList(groupWindowsPath(exerciseId), windows, MetisGroupWindowDTO.class, HttpStatus.OK);
    }

    private MetisExerciseConfigDTO storedConfig() {
        return storedConfig(exerciseId);
    }

    private MetisExerciseConfigDTO storedConfig(long id) {
        return MetisExerciseConfigDTO.of(configRepository.findByIdElseThrow(id));
    }

    /**
     * Removes the configuration row of an exercise, which is what a creation that failed between storing the exercise and inserting the row leaves behind.
     */
    private void deleteConfigurationRow(long id) {
        configRepository.deleteById(id);
        assertThat(exerciseDefaultConfigurationsRepository.hasMetisExerciseConfig(id)).isFalse();
    }

    private void switchOff(MetisExerciseConfig config) {
        metisUtilService.saveConfig(config.getExerciseId(), false, config.isReviewRequired(), config.getWindowStart(), config.getWindowEnd());
    }

    private User user(String suffix) {
        return userUtilService.getUserByLogin(TEST_PREFIX + suffix);
    }

    private TutorialGroup tutorialGroup(Course groupCourse, String title) {
        return tutorialGroupUtilService.createAndSaveTutorialGroup(groupCourse.getId(), title, "", 10, false, "Garching", "English", user("tutor1"), Set.of());
    }

    /**
     * A second programming exercise of the course, whose checks a change of the fixture exercise must leave alone.
     */
    private ProgrammingExercise otherExercise() {
        ProgrammingExercise other = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false, ProgrammingLanguage.JAVA, "Metis other", "METISOTHER", null);
        exerciseIds.add(other.getId());
        return other;
    }

    /**
     * A programming exercise of an exam, in a new course in which the users of this class are enrolled.
     */
    private ProgrammingExercise examExercise() {
        ProgrammingExercise examExercise = programmingExerciseUtilService.addEnrolledCourseExamExerciseGroupWithOneProgrammingExercise(TEST_PREFIX);
        exerciseIds.add(examExercise.getId());
        return examExercise;
    }

    private ProgrammingExerciseStudentParticipation participation(ProgrammingExercise participationExercise, String userSuffix) {
        var participation = ParticipationFactory.generateIndividualProgrammingExerciseStudentParticipation(participationExercise, user(userSuffix));
        return programmingExerciseStudentParticipationRepository.save(participation);
    }

    private MetisCheck generatedCheck(ProgrammingExercise checkExercise, String userSuffix, MetisCheckStatus status) {
        return metisUtilService.saveGeneratedCheck(participation(checkExercise, userSuffix).getId(), status, COMMIT_HASH);
    }

    private MetisCheck unavailableCheck(ProgrammingExercise checkExercise, String userSuffix, MetisCheckStatus status, MetisFailureReason reason) {
        MetisCheck check = metisUtilService.saveCheck(participation(checkExercise, userSuffix).getId(), status);
        check.setFailureReason(reason.name());
        return checkRepository.save(check);
    }

    private void skipForAClosedWindow(MetisCheck check) {
        MetisCheck stored = reload(check);
        stored.setStatus(MetisCheckStatus.SKIPPED);
        stored.setFailureReason(MetisFailureReason.WINDOW_CLOSED.name());
        checkRepository.save(stored);
    }

    private MetisQuestion mainQuestion(MetisCheck check, int order, boolean approved) {
        MetisQuestion question = metisUtilService.saveMainQuestion(check.getId(), order, "src/Sorter.java", 12, "What does part " + order + " of the sorter do?", 120);
        if (!approved) {
            return question;
        }
        question.setApprovedAt(WINDOW_START.minusDays(1));
        return questionRepository.save(question);
    }

    private MetisCheck reload(MetisCheck check) {
        return checkRepository.findByIdElseThrow(check.getId());
    }

    private MetisCheckStatus statusOf(MetisCheck check) {
        return reload(check).getStatus();
    }

    private void assertStoredState(MetisCheck check, MetisCheckStatus expectedStatus, @Nullable MetisFailureReason expectedReason) {
        MetisCheck stored = reload(check);
        assertThat(stored.getStatus()).isEqualTo(expectedStatus);
        assertThat(stored.getFailureReason()).isEqualTo(expectedReason != null ? expectedReason.name() : null);
    }
}
