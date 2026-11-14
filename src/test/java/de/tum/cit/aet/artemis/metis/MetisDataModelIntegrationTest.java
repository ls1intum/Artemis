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
import java.util.function.Consumer;
import java.util.stream.Stream;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
 * second question per position, a focus file path of 1024 characters fits, every bounded column refuses a value past its
 * bounds by Bean Validation and by its check constraint alike (except that a position or a regeneration count of 128 is refused
 * by the column type on MySQL and H2, and by the constraint on PostgreSQL), and the module condition follows its property.
 * Every programming exercise owns its configuration from the moment it is stored, with the column defaults, and storing it
 * again neither adds a second row nor resets the first.
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
    private static final String CHANGELOG = "config/liquibase/changelog/20261112102400_changelog.xml";

    private static final String BACKFILL_CHANGE_SET = "20261112102400-17-backfill-metis-exercise-config";

    /** A repository path of exactly 1024 characters, the longest the column holds: 58 nested directories and a long file name. */
    private static final String LONGEST_FOCUS_FILE_PATH = "src/" + "nested_directory/".repeat(58) + "A".repeat(29) + ".java";

    /** The columns are datetime(3) without a zone, so what comes back is the same instant, not necessarily the same zone. */
    private static final BiPredicate<ZonedDateTime, ZonedDateTime> SAME_INSTANT = (left, right) -> left.toInstant().equals(right.toInstant());

    /** Checks the Bean Validation rules of the entities, which nothing in production runs on them; the database states the same rules. */
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

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
     * For the writes behind the entity's back that have to stay: a configuration removed the way an exercise stored before the migration lacks it, and the
     * backfill changeSet of the migration. The pool of the tests disables auto-commit, so they run on a connection of their own.
     */
    @Autowired
    private DataSource dataSource;

    private Course course;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private TutorialGroup tutorialGroup;

    @AfterAll
    static void closeTheValidatorFactory() {
        VALIDATOR_FACTORY.close();
    }

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

    /**
     * A value of a bounded column of the configuration, at a bound or just past it: Bean Validation and the database judge it alike.
     * The database has to, since nothing that writes the row in production validates it: the server disables validation on persist,
     * the row is inserted by a JPQL insert, and settings are changed by modifying queries.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("settingsAtAndPastTheirBounds")
    void aSettingIsJudgedAlikeByValidationAndByTheDatabase(Bound<MetisExerciseConfig> bound) {
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(exercise.getId());
        bound.entityChange().accept(config);
        assertThat(VALIDATOR.validate(config)).extracting(violation -> violation.getPropertyPath().toString()).as("Bean Validation").containsExactlyElementsOf(bound.violations());

        assertTheDatabaseJudges(bound, "UPDATE metis_exercise_config SET " + bound.sqlChange() + " WHERE exercise_id = " + exercise.getId());
    }

    /**
     * A value of a bounded column of a question, at a bound or just past it: Bean Validation and the database judge it alike.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("questionValuesAtAndPastTheirBounds")
    void aQuestionValueIsJudgedAlikeByValidationAndByTheDatabase(Bound<MetisQuestion> bound) {
        MetisCheck check = metisCheckRepository.save(checkOf(participation));
        MetisQuestion stored = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));
        MetisQuestion question = mainQuestionWithEveryFieldSet(check.getId());
        bound.entityChange().accept(question);
        assertThat(VALIDATOR.validate(question)).extracting(violation -> violation.getPropertyPath().toString()).as("Bean Validation")
                .containsExactlyElementsOf(bound.violations());

        assertTheDatabaseJudges(bound, "UPDATE metis_question SET " + bound.sqlChange() + " WHERE id = " + stored.getId());
    }

    @Test
    void aPositionOrRegenerationCountPastTheTinyintRangeIsRefusedOnEveryEngine() {
        MetisCheck check = metisCheckRepository.save(checkOf(participation));
        MetisQuestion stored = metisQuestionRepository.save(mainQuestionWithEveryFieldSet(check.getId()));

        // MySQL and H2 refuse 128 as out of range of the tinyint column before any check runs. PostgreSQL stores a smallint and would take it; there the check
        // constraint refuses it, so a test on any engine finds an overflow.
        for (String change : List.of("question_order = 128", "regeneration_count = 128")) {
            assertThatThrownBy(() -> jdbcTemplate.update("UPDATE metis_question SET " + change + " WHERE id = " + stored.getId())).as(change)
                    .isInstanceOf(DataAccessException.class);
        }
        assertThat(VALIDATOR.validateValue(MetisQuestion.class, "questionOrder", 128)).hasSize(1);
        assertThat(VALIDATOR.validateValue(MetisQuestion.class, "regenerationCount", 128)).hasSize(1);
        assertThat(metisQuestionRepository.findById(stored.getId())).hasValueSatisfying(question -> {
            assertThat(question.getQuestionOrder()).isEqualTo(MAIN_QUESTION_ORDER);
            assertThat(question.getRegenerationCount()).isEqualTo(stored.getRegenerationCount());
        });
    }

    private void assertTheDatabaseJudges(Bound<?> bound, String update) {
        if (bound.constraint() == null) {
            assertThat(jdbcTemplate.update(update)).as("rows the database accepted").isEqualTo(1);
        }
        else {
            assertThatThrownBy(() -> jdbcTemplate.update(update)).isInstanceOf(DataAccessException.class)
                    .satisfies(refusal -> assertThat(refusal.getMessage().toLowerCase(Locale.ROOT)).contains(bound.constraint()));
        }
    }

    /**
     * One value of a bounded column, applied once to an entity that is valid otherwise and once to the stored row, which only the database checks.
     *
     * @param description  what the value is, which names the test
     * @param entityChange applies the value to the entity
     * @param sqlChange    the same value as the SET clause of an update of the stored row
     * @param violations   the properties Bean Validation names, empty for a valid value
     * @param constraint   the check constraint the database names, or null for a valid value
     * @param <E>          the entity
     */
    record Bound<E>(String description, Consumer<E> entityChange, String sqlChange, List<String> violations, @Nullable String constraint) {

        static <E> Bound<E> valid(String description, Consumer<E> entityChange, String sqlChange) {
            return new Bound<>(description, entityChange, sqlChange, List.of(), null);
        }

        static <E> Bound<E> refused(String description, Consumer<E> entityChange, String sqlChange, String violation, String constraint) {
            return new Bound<>(description, entityChange, sqlChange, List.of(violation), constraint);
        }

        @Override
        public String toString() {
            return description;
        }
    }

    static Stream<Bound<MetisExerciseConfig>> settingsAtAndPastTheirBounds() {
        String mainQuestions = "ck_metis_exercise_config_main_question_count";
        String followUps = "ck_metis_exercise_config_max_follow_ups_per_question";
        String seconds = "ck_metis_exercise_config_seconds_per_question";
        String answerLength = "ck_metis_exercise_config_answer_length";
        String complexity = "ck_metis_exercise_config_complexity";
        String likert = "ck_metis_exercise_config_likert_scale_points";
        String threshold = "ck_metis_exercise_config_follow_up_threshold";
        return Stream.of(Bound.valid("one main question", config -> config.setMainQuestionCount(1), "main_question_count = 1"),
                Bound.valid("five main questions", config -> config.setMainQuestionCount(5), "main_question_count = 5"),
                Bound.refused("no main question", config -> config.setMainQuestionCount(0), "main_question_count = 0", "mainQuestionCount", mainQuestions),
                Bound.refused("six main questions", config -> config.setMainQuestionCount(6), "main_question_count = 6", "mainQuestionCount", mainQuestions),
                Bound.valid("no follow-ups", config -> config.setMaxFollowUpsPerQuestion(0), "max_follow_ups_per_question = 0"),
                Bound.valid("three follow-ups", config -> config.setMaxFollowUpsPerQuestion(3), "max_follow_ups_per_question = 3"),
                Bound.refused("a negative number of follow-ups", config -> config.setMaxFollowUpsPerQuestion(-1), "max_follow_ups_per_question = -1", "maxFollowUpsPerQuestion",
                        followUps),
                Bound.refused("four follow-ups", config -> config.setMaxFollowUpsPerQuestion(4), "max_follow_ups_per_question = 4", "maxFollowUpsPerQuestion", followUps),
                Bound.valid("time limits from 30 seconds to an hour", config -> {
                    config.setMinSecondsPerQuestion(30);
                    config.setMaxSecondsPerQuestion(3600);
                }, "min_seconds_per_question = 30, max_seconds_per_question = 3600"), Bound.valid("one time limit only", config -> {
                    config.setMinSecondsPerQuestion(120);
                    config.setMaxSecondsPerQuestion(120);
                }, "min_seconds_per_question = 120, max_seconds_per_question = 120"),
                Bound.refused("a shortest time limit of 29 seconds", config -> config.setMinSecondsPerQuestion(29), "min_seconds_per_question = 29", "minSecondsPerQuestion",
                        seconds),
                Bound.refused("a longest time limit of an hour and a second", config -> config.setMaxSecondsPerQuestion(3601), "max_seconds_per_question = 3601",
                        "maxSecondsPerQuestion", seconds),
                Bound.refused("a shortest time limit above the longest", config -> {
                    config.setMinSecondsPerQuestion(200);
                    config.setMaxSecondsPerQuestion(100);
                }, "min_seconds_per_question = 200, max_seconds_per_question = 100", "timeLimitRangeOrdered", seconds),
                Bound.valid("the shortest answers", config -> config.setAnswerLength(1), "answer_length = 1"),
                Bound.valid("the longest answers", config -> config.setAnswerLength(5), "answer_length = 5"),
                Bound.refused("an answer length of 0", config -> config.setAnswerLength(0), "answer_length = 0", "answerLength", answerLength),
                Bound.refused("an answer length of 6", config -> config.setAnswerLength(6), "answer_length = 6", "answerLength", answerLength),
                Bound.valid("the simplest questions", config -> config.setComplexity(1), "complexity = 1"),
                Bound.valid("the most complex questions", config -> config.setComplexity(5), "complexity = 5"),
                Bound.refused("a complexity of 0", config -> config.setComplexity(0), "complexity = 0", "complexity", complexity),
                Bound.refused("a complexity of 6", config -> config.setComplexity(6), "complexity = 6", "complexity", complexity),
                Bound.valid("five Likert points", config -> config.setLikertScalePoints(5), "likert_scale_points = 5"),
                Bound.valid("seven Likert points", config -> config.setLikertScalePoints(7), "likert_scale_points = 7"),
                Bound.refused("four Likert points", config -> config.setLikertScalePoints(4), "likert_scale_points = 4", "likertScaleSupported", likert),
                // Six lies between five and seven, which is why the rule is membership and not a range.
                Bound.refused("six Likert points", config -> config.setLikertScalePoints(6), "likert_scale_points = 6", "likertScaleSupported", likert),
                Bound.refused("eight Likert points", config -> config.setLikertScalePoints(8), "likert_scale_points = 8", "likertScaleSupported", likert),
                Bound.valid("a follow-up threshold of 1", config -> config.setFollowUpThreshold(1), "follow_up_threshold = 1"),
                Bound.valid("a follow-up threshold just below the top of five points", config -> config.setFollowUpThreshold(4), "follow_up_threshold = 4"),
                Bound.valid("a follow-up threshold just below the top of seven points", config -> {
                    config.setLikertScalePoints(7);
                    config.setFollowUpThreshold(6);
                }, "likert_scale_points = 7, follow_up_threshold = 6"),
                Bound.refused("a follow-up threshold of 0", config -> config.setFollowUpThreshold(0), "follow_up_threshold = 0", "followUpThreshold", threshold),
                Bound.refused("a follow-up threshold at the top of five points", config -> config.setFollowUpThreshold(5), "follow_up_threshold = 5",
                        "followUpThresholdBelowTheTopScore", threshold),
                Bound.refused("a follow-up threshold at the top of seven points", config -> {
                    config.setLikertScalePoints(7);
                    config.setFollowUpThreshold(7);
                }, "likert_scale_points = 7, follow_up_threshold = 7", "followUpThresholdBelowTheTopScore", threshold));
    }

    static Stream<Bound<MetisQuestion>> questionValuesAtAndPastTheirBounds() {
        String order = "ck_metis_question_question_order";
        String regenerations = "ck_metis_question_regeneration_count";
        String timeLimit = "ck_metis_question_time_limit_seconds";
        String score = "ck_metis_question_llm_score";
        String changedLines = "ck_metis_question_focus_file_changed_lines";
        return Stream.of(Bound.valid("the first position", question -> question.setQuestionOrder(1), "question_order = 1"),
                Bound.valid("the last position a tinyint holds", question -> question.setQuestionOrder(127), "question_order = 127"),
                Bound.refused("position 0", question -> question.setQuestionOrder(0), "question_order = 0", "questionOrder", order),
                Bound.valid("no regeneration", question -> question.setRegenerationCount(0), "regeneration_count = 0"),
                Bound.valid("127 regenerations", question -> question.setRegenerationCount(127), "regeneration_count = 127"),
                Bound.refused("a negative number of regenerations", question -> question.setRegenerationCount(-1), "regeneration_count = -1", "regenerationCount", regenerations),
                Bound.valid("the shortest time limit", question -> question.setTimeLimitSeconds(30), "time_limit_seconds = 30"),
                Bound.valid("the longest time limit", question -> question.setTimeLimitSeconds(3600), "time_limit_seconds = 3600"),
                Bound.refused("a time limit of 29 seconds", question -> question.setTimeLimitSeconds(29), "time_limit_seconds = 29", "timeLimitSeconds", timeLimit),
                Bound.refused("a time limit of an hour and a second", question -> question.setTimeLimitSeconds(3601), "time_limit_seconds = 3601", "timeLimitSeconds", timeLimit),
                Bound.valid("no score before the rating", question -> question.setLlmScore(null), "llm_score = NULL"),
                Bound.valid("the lowest score", question -> question.setLlmScore(1), "llm_score = 1"),
                Bound.valid("the top score of seven points", question -> question.setLlmScore(7), "llm_score = 7"),
                Bound.refused("a score of 0", question -> question.setLlmScore(0), "llm_score = 0", "llmScore", score),
                Bound.refused("a score of 8", question -> question.setLlmScore(8), "llm_score = 8", "llmScore", score),
                Bound.valid("a focus file without changed lines", question -> question.setFocusFileChangedLines(0), "focus_file_changed_lines = 0"),
                Bound.valid("no focus file, as for a follow-up", question -> question.setFocusFileChangedLines(null), "focus_file_changed_lines = NULL"),
                Bound.refused("a negative number of changed lines", question -> question.setFocusFileChangedLines(-1), "focus_file_changed_lines = -1", "focusFileChangedLines",
                        changedLines));
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
     * Removes the configuration row of an exercise without going through the entity, so that the exercise looks like one stored before the migration.
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
