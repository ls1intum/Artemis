package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BiPredicate;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.service.CourseResetService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseConfigurationService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.localvc.service.ParticipationVcsAccessTokenService;
import de.tum.cit.aet.artemis.metis.config.MetisEnabled;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
import de.tum.cit.aet.artemis.metis.domain.MetisCheckStatus;
import de.tum.cit.aet.artemis.metis.domain.MetisExerciseConfig;
import de.tum.cit.aet.artemis.metis.domain.MetisGroupWindow;
import de.tum.cit.aet.artemis.metis.domain.MetisQuestion;
import de.tum.cit.aet.artemis.metis.repository.MetisCheckRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisExerciseConfigRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisGroupWindowRepository;
import de.tum.cit.aet.artemis.metis.repository.MetisQuestionRepository;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationIndependentTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;
import liquibase.changelog.ChangeLogParameters;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;

/**
 * Holds the Metis data model to what its schema promises: every column round-trips through the repositories, the
 * foreign keys cascade from the participation, the check, the main question, the tutorial group and the exercise without
 * any Metis service being involved, no row can be written without its parent, a follow-up can only name a question of
 * its own check, the unique keys refuse a second window per tutorial group, a second check per participation and a
 * second question per position, a focus file path of 1024 characters fits, only a five- or seven-point Likert scale is
 * accepted, and the module condition follows its property. Every programming exercise owns its configuration from the
 * moment it is stored, with the column defaults, and storing it again neither adds a second row nor resets the first.
 *
 * <p>
 * The deletions run through the paths a participation or an exercise really leaves by: a plain delete of the
 * participation row, {@link ParticipationDeletionService#delete(long, boolean)}, which the exercise deletion and the
 * course reset run once per participation, {@link ExerciseDeletionService#delete(long, boolean)}, which the deletion
 * of a course runs once per exercise, and {@link CourseResetService#resetStudentData(long)}, which keeps the exercise
 * and so has to keep its configuration. A check, a main question and a tutorial group are deleted through their repositories.
 * After every deletion a sweep over all four tables asserts that no row names a parent that is gone. With every foreign
 * key in place, such a row cannot exist, so the sweep is the assertion that no key has gone missing from the changelog,
 * over whole tables rather than this test's rows; a key that stopped cascading would surface earlier, as an exception
 * from the deletion itself. Deleting a whole course is not run here: under the Independent profile it asks a continuous
 * integration service to remove the build plans of every programming exercise, and none is loaded.
 */
class MetisDataModelIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metisdatamodel";

    /** Every timestamp in the fixtures is a distinct offset from this one, so a value written to the wrong column is caught. */
    private static final ZonedDateTime BASE = ZonedDateTime.parse("2026-03-01T10:00:00Z");

    private static final int MAIN_QUESTION_ORDER = 1;

    private static final int FOLLOW_UP_ORDER = 2;

    /** An id no fixture hands out, so a row pointing at it names a parent that does not exist. */
    private static final long MISSING_ID = Long.MAX_VALUE / 2;

    /** The changelog that creates the Metis tables, and its changeSet that gives the programming exercises stored before it their configuration. */
    private static final String CHANGELOG = "config/liquibase/changelog/20261012160502_changelog.xml";

    private static final String BACKFILL_CHANGE_SET = "20261012160502-06-backfill-metis-exercise-config";

    /** A repository path of exactly 1024 characters, the longest the column holds: 58 nested directories and a long file name. */
    private static final String LONGEST_FOCUS_FILE_PATH = "src/" + "nested_directory/".repeat(58) + "A".repeat(29) + ".java";

    /** The columns are datetime(3) without a zone, so what comes back is the same instant, not necessarily the same zone. */
    private static final BiPredicate<ZonedDateTime, ZonedDateTime> SAME_INSTANT = (left, right) -> left.toInstant().equals(right.toInstant());

    @Autowired
    private MetisExerciseConfigRepository metisExerciseConfigRepository;

    @Autowired
    private MetisGroupWindowRepository metisGroupWindowRepository;

    @Autowired
    private MetisCheckRepository metisCheckRepository;

    @Autowired
    private MetisQuestionRepository metisQuestionRepository;

    @Autowired
    private TutorialGroupUtilService tutorialGroupUtilService;

    @Autowired
    private TutorialGroupTestRepository tutorialGroupRepository;

    @Autowired
    private ParticipationVcsAccessTokenService participationVcsAccessTokenService;

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private ExerciseDeletionService exerciseDeletionService;

    @Autowired
    private CourseResetService courseResetService;

    @Autowired
    private ExerciseConfigurationService exerciseConfigurationService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    /**
     * For what the repositories cannot do: the orphan sweep, since they cannot ask for a row whose parent is gone, and a native write that the database has to
     * refuse.
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * For the writes behind the entity's back that have to stay: a configuration removed the way a creation that failed half-way leaves it, and the backfill
     * changeSet of the migration. The pool of the tests disables auto-commit, so they run on a connection of their own.
     */
    @Autowired
    private DataSource dataSource;

    private Course course;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private TutorialGroup tutorialGroup;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        tutorialGroup = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Metis group", "", 10, false, "Garching", "English",
                userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), Set.of());
    }

    @Test
    void everyColumnRoundTrips() {
        MetisExerciseConfig config = configWithEveryFieldSet();
        metisExerciseConfigRepository.save(config);
        MetisGroupWindow window = metisGroupWindowRepository.save(windowWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));

        MetisExerciseConfig loadedConfig = metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow();
        assertThat(loadedConfig).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).isEqualTo(config);

        List<MetisGroupWindow> loadedWindows = metisGroupWindowRepository.findAllByExerciseId(exercise.getId());
        assertThat(loadedWindows).hasSize(1);
        assertThat(loadedWindows.getFirst()).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).isEqualTo(window);

        MetisCheck loadedCheck = metisCheckRepository.findByParticipationId(participation.getId()).orElseThrow();
        assertThat(loadedCheck).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).isEqualTo(check);

        List<MetisQuestion> loadedQuestions = metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId());
        assertThat(loadedQuestions).hasSize(2);
        assertThat(loadedQuestions.getFirst()).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).isEqualTo(mainQuestion);
        assertThat(loadedQuestions.getFirst().isFollowUp()).isFalse();
        assertThat(loadedQuestions.getLast()).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class).isEqualTo(followUp);
        assertThat(loadedQuestions.getLast().isFollowUp()).isTrue();
    }

    @Test
    void deletingTheParticipationRemovesTheCheckAndItsQuestionsAndKeepsTheConfig() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));

        // The participation's own VCS access token restricts the delete, so it goes first, as it does in the deletion service. The participation row itself then leaves
        // through a plain DELETE: no Metis code runs, the cascade in the schema alone has to reach the check and its questions.
        participationVcsAccessTokenService.deleteByParticipationId(participation.getId());
        programmingExerciseStudentParticipationRepository.deleteById(participation.getId());

        assertThat(metisCheckRepository.findByParticipationId(participation.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(mainQuestion.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(followUp.getId())).isEmpty();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void participationDeletionThroughTheServiceLeavesNoMetisRows() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        MetisGroupWindow window = metisGroupWindowRepository.save(windowWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));
        clearTheBuildPlanIdOfTheParticipation();

        // The service removes the token, the submissions and the results itself and then deletes the participation row. It knows nothing about Metis, so the check
        // and the questions can only have gone through the cascade; the configuration and the group window belong to the exercise and are not its concern.
        participationDeletionService.delete(participation.getId(), true);

        assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isEmpty();
        assertThat(metisCheckRepository.findById(check.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(mainQuestion.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(followUp.getId())).isEmpty();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();
        assertThat(metisGroupWindowRepository.findById(window.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void courseResetLeavesTheConfigurationAndRemovesTheStudentData() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        MetisGroupWindow window = metisGroupWindowRepository.save(windowWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));
        clearTheBuildPlanIdOfTheParticipation();

        // A reset deletes every participation of every exercise and keeps the exercises and the tutorial groups, so the student's check and questions have to go
        // while the instructor's configuration and group window stay. The reset notes an exercise it could not reset and throws at the end, so a foreign key that
        // refused the participation delete would surface here as an exception rather than as a quietly kept row. It runs as the instructor, as it does behind its
        // endpoint and in CourseResetServiceTest.
        courseResetService.resetStudentData(course.getId());

        assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isEmpty();
        assertThat(metisCheckRepository.findById(check.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(mainQuestion.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(followUp.getId())).isEmpty();
        assertThat(exerciseRepository.findById(exercise.getId())).isPresent();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();
        assertThat(tutorialGroupRepository.findById(tutorialGroup.getId())).isPresent();
        assertThat(metisGroupWindowRepository.findById(window.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void deletingAMainQuestionRemovesItsFollowUp() {
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));
        MetisQuestion otherMainQuestion = mainQuestionWithEveryFieldSet(check.getId());
        otherMainQuestion.setQuestionOrder(FOLLOW_UP_ORDER + 1);
        otherMainQuestion = metisQuestionRepository.save(otherMainQuestion);

        metisQuestionRepository.deleteById(mainQuestion.getId());

        assertThat(metisQuestionRepository.findById(followUp.getId())).isEmpty();
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId())).extracting(MetisQuestion::getId).containsExactly(otherMainQuestion.getId());
        assertThat(metisCheckRepository.findById(check.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void deletingTheTutorialGroupRemovesItsWindowAndKeepsTheConfig() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        MetisGroupWindow window = metisGroupWindowRepository.save(windowWithEveryFieldSet());

        tutorialGroupRepository.deleteById(tutorialGroup.getId());

        assertThat(metisGroupWindowRepository.findById(window.getId())).isEmpty();
        assertThat(metisGroupWindowRepository.findAllByExerciseId(exercise.getId())).isEmpty();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void deletingTheExerciseRemovesTheConfigAndTheWindow() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        MetisGroupWindow window = metisGroupWindowRepository.save(windowWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        clearTheBuildPlanIdOfTheParticipation();

        // The participations go through the participation deletion service, the exercise through the programming exercise deletion service, and nothing but the
        // cascade in the schema may reach the Metis rows.
        exerciseDeletionService.delete(exercise.getId(), false);

        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isEmpty();
        assertThat(metisGroupWindowRepository.findById(window.getId())).isEmpty();
        assertThat(metisCheckRepository.findById(check.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(mainQuestion.getId())).isEmpty();
        assertThat(tutorialGroupRepository.findById(tutorialGroup.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void aRowCannotBeCreatedWithoutItsParent() {
        MetisExerciseConfig configOfAMissingExercise = new MetisExerciseConfig();
        configOfAMissingExercise.setExerciseId(MISSING_ID);
        assertThatThrownBy(() -> metisExerciseConfigRepository.saveAndFlush(configOfAMissingExercise)).isInstanceOf(DataIntegrityViolationException.class);

        // A window hangs off the configuration, not off the exercise: the text exercise exists, but only a programming exercise owns a configuration.
        TextExercise textExercise = textExerciseUtilService.createSampleTextExercise(course);
        MetisGroupWindow windowOfAnExerciseWithoutConfiguration = windowWithEveryFieldSet();
        windowOfAnExerciseWithoutConfiguration.setExerciseId(textExercise.getId());
        assertThatThrownBy(() -> metisGroupWindowRepository.saveAndFlush(windowOfAnExerciseWithoutConfiguration)).isInstanceOf(DataIntegrityViolationException.class);

        // The programming exercise owns its configuration from the start, so here only the missing tutorial group is refused.
        MetisGroupWindow windowOfAMissingGroup = windowWithEveryFieldSet();
        windowOfAMissingGroup.setTutorialGroupId(MISSING_ID);
        assertThatThrownBy(() -> metisGroupWindowRepository.saveAndFlush(windowOfAMissingGroup)).isInstanceOf(DataIntegrityViolationException.class);

        MetisCheck checkOfAMissingParticipation = new MetisCheck();
        checkOfAMissingParticipation.setParticipationId(MISSING_ID);
        assertThatThrownBy(() -> metisCheckRepository.saveAndFlush(checkOfAMissingParticipation)).isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> metisQuestionRepository.saveAndFlush(mainQuestionWithEveryFieldSet(MISSING_ID))).isInstanceOf(DataIntegrityViolationException.class);

        // A follow-up has two parents; the second one, its main question, is checked as well.
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        assertThatThrownBy(() -> metisQuestionRepository.saveAndFlush(followUpWithEveryFieldSet(check.getId(), MISSING_ID))).isInstanceOf(DataIntegrityViolationException.class);

        // A refused insert leaves nothing behind: only the configuration the exercise owns and the check saved above exist.
        assertThat(metisExerciseConfigRepository.findById(MISSING_ID)).isEmpty();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();
        assertThat(metisExerciseConfigRepository.findById(textExercise.getId())).isEmpty();
        assertThat(metisGroupWindowRepository.findAllByExerciseId(exercise.getId())).isEmpty();
        assertThat(metisGroupWindowRepository.findAllByExerciseId(textExercise.getId())).isEmpty();
        assertThat(metisCheckRepository.findByParticipationId(participation.getId())).map(MetisCheck::getId).contains(check.getId());
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId())).isEmpty();
        assertNoOrphanRows();
    }

    @Test
    void aFollowUpCannotNameAMainQuestionOfAnotherCheck() {
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        ProgrammingExerciseStudentParticipation otherStudentsParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise,
                TEST_PREFIX + "student2");
        MetisCheck otherStudentsCheck = metisCheckRepository.save(checkOf(otherStudentsParticipation));

        // Both ids exist, but not as a pair: the main question belongs to the first student's check, so the second student's follow-up cannot name it.
        MetisQuestion crossCheckFollowUp = followUpWithEveryFieldSet(otherStudentsCheck.getId(), mainQuestion.getId());
        assertThatThrownBy(() -> metisQuestionRepository.saveAndFlush(crossCheckFollowUp)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(otherStudentsCheck.getId())).isEmpty();

        // The same follow-up in the main question's own check is accepted, and so is a main question, whose parent is null.
        MetisQuestion followUp = metisQuestionRepository.saveAndFlush(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId())).extracting(MetisQuestion::getId).containsExactly(mainQuestion.getId(),
                followUp.getId());
        assertNoOrphanRows();
    }

    @Test
    void deletingTheCheckRemovesItsMainQuestionsAndTheirFollowUps() {
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion mainQuestion = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion followUp = metisQuestionRepository.save(followUpWithEveryFieldSet(check.getId(), mainQuestion.getId()));

        // The follow-up is reached twice, through its check and through its main question; both keys cascade.
        metisCheckRepository.deleteById(check.getId());

        assertThat(metisQuestionRepository.findById(mainQuestion.getId())).isEmpty();
        assertThat(metisQuestionRepository.findById(followUp.getId())).isEmpty();
        assertThat(programmingExerciseStudentParticipationRepository.findById(participation.getId())).isPresent();
        assertNoOrphanRows();
    }

    @Test
    void everyProgrammingExerciseOwnsItsConfigurationFromTheMomentItIsStored() {
        // The fixture stores the exercise the way every creation path does, followed by ExerciseConfigurationService.initialize, and nothing here writes a
        // configuration. The row is there all the same, and it holds the column defaults, which are the defaults of the entity: Metis starts switched off.
        MetisExerciseConfig defaults = new MetisExerciseConfig();
        defaults.setExerciseId(exercise.getId());
        assertThat(metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(defaults);

        // An exercise of another type is stored through the same call and owns none.
        TextExercise textExercise = textExerciseUtilService.createSampleTextExercise(course);
        assertThat(metisExerciseConfigRepository.findById(textExercise.getId())).isEmpty();
    }

    @Test
    void storingTheExerciseAgainNeitherAddsNorResetsItsConfiguration() {
        // An instructor's settings, written into the row the exercise owns.
        metisExerciseConfigRepository.save(configWithEveryFieldSet());

        // A path that initializes an exercise it initialized before finds the row and leaves it alone.
        exerciseConfigurationService.initialize(exercise);
        exerciseConfigurationService.initialize(exercise);

        assertThat(metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow()).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class)
                .isEqualTo(configWithEveryFieldSet());
    }

    @Test
    void anExerciseWhoseConfigurationIsMissingGetsTheDefaultsAgain() throws SQLException {
        // What a creation that failed after storing the exercise leaves behind: the exercise without its row.
        deleteConfigurationRow(exercise.getId());
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isEmpty();

        exerciseConfigurationService.initialize(exercise);

        MetisExerciseConfig defaults = new MetisExerciseConfig();
        defaults.setExerciseId(exercise.getId());
        assertThat(metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(defaults);
    }

    @Test
    void theMigrationGivesAProgrammingExerciseStoredBeforeItTheDefaultsAndLeavesEveryOtherRowAlone() throws Exception {
        // A programming exercise as the migration finds it on an installation that already has exercises: without a configuration.
        deleteConfigurationRow(exercise.getId());
        TextExercise textExercise = textExerciseUtilService.createSampleTextExercise(course);

        executeBackfill();

        MetisExerciseConfig defaults = new MetisExerciseConfig();
        defaults.setExerciseId(exercise.getId());
        assertThat(metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow()).usingRecursiveComparison().isEqualTo(defaults);
        assertThat(metisExerciseConfigRepository.findById(textExercise.getId())).isEmpty();

        // A repeated run, after an interruption, skips every exercise that has its row and changes none of them.
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        executeBackfill();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId()).orElseThrow()).usingRecursiveComparison().withEqualsForType(SAME_INSTANT, ZonedDateTime.class)
                .isEqualTo(configWithEveryFieldSet());
    }

    @Test
    void aFocusFilePathOf1024CharactersRoundTrips() {
        assertThat(LONGEST_FOCUS_FILE_PATH).hasSize(1024);
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        MetisQuestion question = mainQuestionWithEveryFieldSet(check.getId());
        question.setFocusFilePath(LONGEST_FOCUS_FILE_PATH);

        MetisQuestion saved = metisQuestionRepository.saveAndFlush(question);

        assertThat(metisQuestionRepository.findById(saved.getId())).map(MetisQuestion::getFocusFilePath).contains(LONGEST_FOCUS_FILE_PATH);
    }

    @Test
    void onlyAFiveOrASevenPointLikertScaleIsAccepted() {
        MetisExerciseConfig config = configWithEveryFieldSet();
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            for (int points : List.of(MetisExerciseConfig.MIN_LIKERT_POINTS, MetisExerciseConfig.MAX_LIKERT_POINTS)) {
                config.setLikertScalePoints(points);
                assertThat(validator.validate(config)).as("violations of a %d-point scale", points).isEmpty();
                metisExerciseConfigRepository.saveAndFlush(config);
                assertThat(metisExerciseConfigRepository.findById(exercise.getId())).map(MetisExerciseConfig::getLikertScalePoints).contains(points);
            }

            // Six lies inside the range from five to seven, which is why the rule is membership and not a range.
            config.setLikertScalePoints(6);
            assertThat(validator.validate(config)).extracting(violation -> violation.getPropertyPath().toString()).containsExactly("likertScaleSupported");
        }

        // The database refuses six too, for the writes that do not pass through validation: the server disables it on persist, the row is inserted by a native
        // statement, and settings are changed by modifying queries.
        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE metis_exercise_config SET likert_scale_points = ? WHERE exercise_id = ?", 6, exercise.getId()))
                .isInstanceOf(DataAccessException.class)
                .satisfies(refusal -> assertThat(refusal.getMessage().toLowerCase(Locale.ROOT)).contains("ck_metis_exercise_config_likert_scale_points"));
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).map(MetisExerciseConfig::getLikertScalePoints).contains(MetisExerciseConfig.MAX_LIKERT_POINTS);
    }

    @Test
    void theUniqueKeysHold() {
        metisExerciseConfigRepository.save(configWithEveryFieldSet());
        metisGroupWindowRepository.save(windowWithEveryFieldSet());
        MetisCheck check = metisCheckRepository.save(checkWithEveryFieldSet());
        metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));

        assertThatThrownBy(() -> metisGroupWindowRepository.saveAndFlush(windowWithEveryFieldSet())).isInstanceOf(DataIntegrityViolationException.class);

        MetisCheck secondCheckForTheSameParticipation = new MetisCheck();
        secondCheckForTheSameParticipation.setParticipationId(participation.getId());
        assertThatThrownBy(() -> metisCheckRepository.saveAndFlush(secondCheckForTheSameParticipation)).isInstanceOf(DataIntegrityViolationException.class);

        MetisQuestion secondQuestionAtTheSamePosition = new MetisQuestion();
        secondQuestionAtTheSamePosition.setCheckId(check.getId());
        secondQuestionAtTheSamePosition.setQuestionOrder(MAIN_QUESTION_ORDER);
        secondQuestionAtTheSamePosition.setQuestionText("Which invariant does the loop in the focus file keep?");
        secondQuestionAtTheSamePosition.setTimeLimitSeconds(120);
        assertThatThrownBy(() -> metisQuestionRepository.saveAndFlush(secondQuestionAtTheSamePosition)).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(metisGroupWindowRepository.findAllByExerciseId(exercise.getId())).hasSize(1);
        assertThat(metisCheckRepository.findByParticipationId(participation.getId())).map(MetisCheck::getId).contains(check.getId());
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId())).hasSize(1);
    }

    @Test
    void metisEnabledFollowsTheModuleProperty() {
        AnnotatedTypeMetadata metadata = mock(AnnotatedTypeMetadata.class);

        assertThat(new MetisEnabled().matches(contextWithMetisEnabled("true"), metadata)).isTrue();
        assertThat(new MetisEnabled().matches(contextWithMetisEnabled("false"), metadata)).isFalse();
    }

    private static ConditionContext contextWithMetisEnabled(String enabled) {
        ConditionContext context = mock(ConditionContext.class);
        when(context.getEnvironment()).thenReturn(new MockEnvironment().withProperty(Constants.METIS_ENABLED_PROPERTY_NAME, enabled));
        return context;
    }

    /**
     * Removes the configuration row of an exercise without going through the entity, as a creation that failed after storing the exercise leaves it.
     *
     * @param exerciseId the id of the exercise whose row goes
     * @throws SQLException if the statement fails
     */
    private void deleteConfigurationRow(long exerciseId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement("DELETE FROM metis_exercise_config WHERE exercise_id = ?")) {
            connection.setAutoCommit(true);
            statement.setLong(1, exerciseId);
            statement.executeUpdate();
        }
    }

    /**
     * Runs the backfill changeSet of the Metis migration against the test database the way Liquibase runs it on an upgrade. On the empty database every test
     * run starts from, the migration has no exercise to give a row, so this is the only place it meets one.
     *
     * @throws Exception if the changelog cannot be read or the changeSet fails
     */
    private void executeBackfill() throws Exception {
        try (Connection connection = dataSource.getConnection(); ClassLoaderResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor()) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            var changeLog = ChangeLogParserFactory.getInstance().getParser(CHANGELOG, resourceAccessor).parse(CHANGELOG, new ChangeLogParameters(database), resourceAccessor);
            var changeSet = changeLog.getChangeSets().stream().filter(candidate -> candidate.getId().equals(BACKFILL_CHANGE_SET)).findFirst().orElseThrow();
            changeSet.execute(changeLog, database);
        }
    }

    /**
     * The fixture gives the participation a build plan id, and removing a build plan needs a continuous integration service that the Independent profile does not
     * load. Clearing the id keeps a deletion on its real path: the participation goes through the participation deletion service, the exercise through the
     * programming exercise deletion service, and nothing but the cascade in the schema may reach the Metis rows.
     */
    private void clearTheBuildPlanIdOfTheParticipation() {
        ProgrammingExerciseStudentParticipation withoutBuildPlan = programmingExerciseStudentParticipationRepository.findById(participation.getId()).orElseThrow();
        withoutBuildPlan.setBuildPlanId(null);
        programmingExerciseStudentParticipationRepository.save(withoutBuildPlan);
    }

    /**
     * Fails when any Metis row names a parent that no longer exists, one count per foreign key. While every key is in place, no such row can exist; the sweep
     * therefore guards against a key that went missing from the changelog, over the whole tables rather than this test's fixture, since an orphan is an orphan
     * whichever test left it. A key that stopped cascading is caught before this: the deletion itself throws, or the single-row lookups above still find the
     * child. The statements use nothing beyond a LEFT JOIN and read the same on H2, PostgreSQL and MySQL.
     */
    private void assertNoOrphanRows() {
        assertThat(countRowsWithoutParent("metis_question", "check_id", "metis_check", "id")).as("questions whose check is gone").isZero();
        assertThat(countFollowUpsWithoutTheirMainQuestion()).as("follow-ups whose main question is gone or belongs to another check").isZero();
        assertThat(countRowsWithoutParent("metis_check", "participation_id", "participation", "id")).as("checks whose participation is gone").isZero();
        assertThat(countRowsWithoutParent("metis_group_window", "exercise_id", "metis_exercise_config", "exercise_id")).as("windows whose configuration is gone").isZero();
        assertThat(countRowsWithoutParent("metis_group_window", "tutorial_group_id", "tutorial_group", "id")).as("windows whose tutorial group is gone").isZero();
        assertThat(countRowsWithoutParent("metis_exercise_config", "exercise_id", "exercise", "id")).as("configurations whose exercise is gone").isZero();
    }

    /**
     * How many follow-ups name a main question their own check does not hold, which covers a deleted main question and one of another check alike: the key of a
     * follow-up is the pair (parent_question_id, check_id). A main question has no parent and is not counted.
     *
     * @return the number of follow-ups whose main question is missing from their check
     */
    private long countFollowUpsWithoutTheirMainQuestion() {
        String sql = "SELECT COUNT(*) FROM metis_question c LEFT JOIN metis_question p ON p.check_id = c.check_id AND p.id = c.parent_question_id"
                + " WHERE c.parent_question_id IS NOT NULL AND p.id IS NULL";
        Long orphans = jdbcTemplate.queryForObject(sql, Long.class);
        return orphans == null ? 0 : orphans;
    }

    /**
     * How many rows of the child table name a parent the parent table does not hold. The columns swept here are all NOT NULL, so the IS NOT NULL guard is
     * implied; it keeps the statement correct for a nullable key as well.
     *
     * @param childTable   the table holding the foreign key
     * @param childColumn  the foreign key column
     * @param parentTable  the table the key points at
     * @param parentColumn the key column of the parent table
     * @return the number of rows whose parent is missing
     */
    private long countRowsWithoutParent(String childTable, String childColumn, String parentTable, String parentColumn) {
        String sql = "SELECT COUNT(*) FROM " + childTable + " c LEFT JOIN " + parentTable + " p ON p." + parentColumn + " = c." + childColumn + " WHERE c." + childColumn
                + " IS NOT NULL AND p." + parentColumn + " IS NULL";
        Long orphans = jdbcTemplate.queryForObject(sql, Long.class);
        return orphans == null ? 0 : orphans;
    }

    private MetisExerciseConfig configWithEveryFieldSet() {
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(exercise.getId());
        config.setEnabled(true);
        config.setWindowStart(BASE);
        config.setWindowEnd(BASE.plusDays(7));
        config.setMainQuestionCount(4);
        config.setMaxFollowUpsPerQuestion(1);
        config.setMinSecondsPerQuestion(90);
        config.setMaxSecondsPerQuestion(240);
        config.setAnswerLength(2);
        config.setComplexity(4);
        config.setLikertScalePoints(7);
        config.setFollowUpThreshold(4);
        config.setReviewRequired(true);
        config.setInstructorHint("Ask about the sorting strategy; leave the test files alone.");
        return config;
    }

    private MetisGroupWindow windowWithEveryFieldSet() {
        MetisGroupWindow window = new MetisGroupWindow();
        window.setExerciseId(exercise.getId());
        window.setTutorialGroupId(tutorialGroup.getId());
        window.setWindowStart(BASE.plusDays(1));
        window.setWindowEnd(BASE.plusDays(2));
        return window;
    }

    private static MetisCheck checkOf(ProgrammingExerciseStudentParticipation participation) {
        MetisCheck check = new MetisCheck();
        check.setParticipationId(participation.getId());
        return check;
    }

    private MetisCheck checkWithEveryFieldSet() {
        MetisCheck check = new MetisCheck();
        check.setParticipationId(participation.getId());
        check.setStatus(MetisCheckStatus.COMPLETED);
        check.setCommitHash("0123456789abcdef0123456789abcdef01234567");
        check.setLlmModel("gpt-5-mini-2026-01-01");
        check.setPromptVersion("v1.2.0");
        check.setFailureReason("none, recorded for the round trip");
        check.setGeneratedAt(BASE.plusMinutes(1));
        check.setStartedAt(BASE.plusMinutes(2));
        check.setCompletedAt(BASE.plusMinutes(3));
        return check;
    }

    private static MetisQuestion mainQuestionWithEveryFieldSet(long checkId) {
        MetisQuestion question = questionWithEveryFieldSet(checkId, MAIN_QUESTION_ORDER, BASE.plusMinutes(10));
        question.setFocusFilePath("src/main/java/de/tum/in/www1/Sorter.java");
        question.setFocusFileChangedLines(42);
        question.setQuestionText("Why does the merge step copy the left half first?");
        return question;
    }

    private static MetisQuestion followUpWithEveryFieldSet(long checkId, long parentQuestionId) {
        MetisQuestion question = questionWithEveryFieldSet(checkId, FOLLOW_UP_ORDER, BASE.plusMinutes(20));
        question.setParentQuestionId(parentQuestionId);
        question.setQuestionText("What would break if the right half were copied first?");
        return question;
    }

    private static MetisQuestion questionWithEveryFieldSet(long checkId, int questionOrder, ZonedDateTime firstTimestamp) {
        MetisQuestion question = new MetisQuestion();
        question.setCheckId(checkId);
        question.setQuestionOrder(questionOrder);
        question.setTimeLimitSeconds(180);
        question.setRegenerationCount(2);
        question.setApprovedAt(firstTimestamp);
        question.setShownAt(firstTimestamp.plusMinutes(1));
        question.setAnswerText("Because the loop invariant assumes the left half is already in place.");
        question.setAnsweredAt(firstTimestamp.plusMinutes(2));
        question.setLlmScore(6);
        question.setLlmRationale("Names the invariant and the reason for the order.");
        question.setLlmRatedAt(firstTimestamp.plusMinutes(3));
        return question;
    }
}
