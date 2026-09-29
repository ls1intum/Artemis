package de.tum.cit.aet.artemis.metis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.PluralAttribute;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.metis.domain.MetisCheck;
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
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroup;
import de.tum.cit.aet.artemis.tutorialgroup.test_repository.TutorialGroupTestRepository;
import de.tum.cit.aet.artemis.tutorialgroup.util.TutorialGroupUtilService;

/**
 * Holds the Metis data model to the promise that its rows are never loaded together with their parents. The entities
 * carry no JPA association in either direction and find each other by id, so an exercise, a participation, a course or
 * a tutorial group is read exactly as it was before Metis existed, and a Metis row is read without its parent. The
 * one-to-one between an exercise and its configuration is the case that matters most: a mapped inverse one-to-one
 * cannot be lazy without bytecode enhancement, so Hibernate would resolve it with a second select on every load of
 * every exercise.
 *
 * <p>
 * The proof has three parts. Loading each parent through its repository records the SQL Hibernate executes, and none
 * of it may name a Metis table. Loading each Metis row runs exactly one statement, which reads only its own table. And
 * the JPA metamodel of the parents has no attribute of a Metis type, which is the structural statement behind the first
 * two. The fixture puts one row in every Metis table, so that an eager mapping, if one existed, would have something to
 * load and show up as a statement. {@code MetisDomainArchitectureTest} holds the same line in the source.
 */
class MetisLazyLoadingIntegrationTest extends AbstractProgrammingIntegrationIndependentTest {

    private static final String TEST_PREFIX = "metislazy";

    private static final String METIS_PACKAGE = "de.tum.cit.aet.artemis.metis";

    private static final ZonedDateTime WINDOW_START = ZonedDateTime.parse("2026-03-01T10:00:00Z");

    /**
     * The entities a Metis row names by id, the supertypes a mapping could be declared on, and the course, where a course-wide collection would be the next
     * place to put a back-reference.
     */
    private static final List<Class<?>> PARENT_ENTITIES = List.of(Exercise.class, ProgrammingExercise.class, Participation.class, StudentParticipation.class,
            ProgrammingExerciseStudentParticipation.class, Course.class, TutorialGroup.class);

    /** The Metis entities, each of which names its parent by id and must therefore carry no association. */
    private static final List<Class<?>> METIS_ENTITIES = List.of(MetisExerciseConfig.class, MetisGroupWindow.class, MetisCheck.class, MetisQuestion.class);

    /** The identifier after FROM or JOIN: a table the statement reads. */
    private static final Pattern TABLE_AFTER_FROM_OR_JOIN = Pattern.compile("\\b(?:from|join)\\s+([a-z_][a-z0-9_]*)");

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
    private EntityManagerFactory entityManagerFactory;

    private Course course;

    private ProgrammingExercise exercise;

    private ProgrammingExerciseStudentParticipation participation;

    private TutorialGroup tutorialGroup;

    private MetisCheck check;

    @BeforeEach
    void setUp() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 0, 0);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, TEST_PREFIX + "student1");
        tutorialGroup = tutorialGroupUtilService.createAndSaveTutorialGroup(course.getId(), "Metis lazy group", "", 10, false, "Garching", "English",
                userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), Set.of());

        // One row in every Metis table. An eager mapping that nobody noticed would have something to load here, and that load would show up as a statement.
        MetisExerciseConfig config = new MetisExerciseConfig();
        config.setExerciseId(exercise.getId());
        config.setEnabled(true);
        metisExerciseConfigRepository.save(config);

        MetisGroupWindow window = new MetisGroupWindow();
        window.setExerciseId(exercise.getId());
        window.setTutorialGroupId(tutorialGroup.getId());
        window.setWindowStart(WINDOW_START);
        window.setWindowEnd(WINDOW_START.plusDays(1));
        metisGroupWindowRepository.save(window);

        MetisCheck pendingCheck = new MetisCheck();
        pendingCheck.setParticipationId(participation.getId());
        check = metisCheckRepository.save(pendingCheck);

        MetisQuestion question = new MetisQuestion();
        question.setCheckId(check.getId());
        question.setQuestionOrder(1);
        question.setQuestionText("Why does the merge step copy the left half first?");
        question.setTimeLimitSeconds(120);
        metisQuestionRepository.save(question);
    }

    // Loading a parent does not touch Metis. The getters after each load read the entity the way a service would, so that an accessor which resolved an
    // association on first use would be seen as well; only the SQL recorded from the load to the last getter is judged.

    @Test
    void loadingTheExerciseByIdDoesNotTouchMetis() {
        queryInterceptor.startQueryCount();
        ProgrammingExercise loaded = programmingExerciseRepository.findByIdElseThrow(exercise.getId());
        assertThat(loaded.getTitle()).isEqualTo(exercise.getTitle());
        assertThat(loaded.getMaxPoints()).isEqualTo(exercise.getMaxPoints());
        assertThat(loaded.getProgrammingLanguage()).isEqualTo(exercise.getProgrammingLanguage());
        assertThat(loaded.getCourseViaExerciseGroupOrCourseMember().getId()).isEqualTo(course.getId());

        assertParentLoadedWithoutReadingMetis(queryInterceptor.getStatements());
    }

    @Test
    void loadingTheExerciseWithItsTemplateAndSolutionDoesNotTouchMetis() {
        queryInterceptor.startQueryCount();
        ProgrammingExercise loaded = programmingExerciseRepository.findWithTemplateAndSolutionParticipationById(exercise.getId()).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo(exercise.getTitle());
        assertThat(loaded.getTemplateParticipation().getId()).isNotNull();
        assertThat(loaded.getSolutionParticipation().getId()).isNotNull();

        assertParentLoadedWithoutReadingMetis(queryInterceptor.getStatements());
    }

    @Test
    void loadingTheParticipationDoesNotTouchMetis() {
        queryInterceptor.startQueryCount();
        ProgrammingExerciseStudentParticipation loaded = programmingExerciseStudentParticipationRepository.findByIdElseThrow(participation.getId());
        assertThat(loaded.getInitializationState()).isEqualTo(participation.getInitializationState());
        assertThat(loaded.getBuildPlanId()).isEqualTo(participation.getBuildPlanId());
        assertThat(loaded.getRepositoryUri()).isEqualTo(participation.getRepositoryUri());
        assertThat(loaded.getExercise().getId()).isEqualTo(exercise.getId());

        assertParentLoadedWithoutReadingMetis(queryInterceptor.getStatements());
    }

    @Test
    void loadingTheCourseDoesNotTouchMetis() {
        queryInterceptor.startQueryCount();
        Course loaded = courseRepository.findByIdElseThrow(course.getId());
        assertThat(loaded.getTitle()).isEqualTo(course.getTitle());
        assertThat(loaded.getShortName()).isEqualTo(course.getShortName());
        assertThat(loaded.getSemester()).isEqualTo(course.getSemester());

        assertParentLoadedWithoutReadingMetis(queryInterceptor.getStatements());
    }

    @Test
    void loadingTheTutorialGroupDoesNotTouchMetis() {
        queryInterceptor.startQueryCount();
        TutorialGroup loaded = tutorialGroupRepository.findByIdElseThrow(tutorialGroup.getId());
        assertThat(loaded.getTitle()).isEqualTo(tutorialGroup.getTitle());
        assertThat(loaded.getCapacity()).isEqualTo(tutorialGroup.getCapacity());
        assertThat(loaded.getLanguage()).isEqualTo(tutorialGroup.getLanguage());
        assertThat(loaded.getCourse().getId()).isEqualTo(course.getId());

        assertParentLoadedWithoutReadingMetis(queryInterceptor.getStatements());
    }

    // Loading a Metis row does not touch its parent: one statement, and the only table in it is the row's own.

    @Test
    void loadingTheConfigReadsOnlyItsOwnTable() {
        queryInterceptor.startQueryCount();
        assertThat(metisExerciseConfigRepository.findById(exercise.getId())).isPresent();

        assertReadsOnly("metis_exercise_config", queryInterceptor.getStatements());
    }

    @Test
    void loadingTheCheckReadsOnlyItsOwnTable() {
        queryInterceptor.startQueryCount();
        assertThat(metisCheckRepository.findByParticipationId(participation.getId())).isPresent();

        assertReadsOnly("metis_check", queryInterceptor.getStatements());
    }

    @Test
    void loadingTheGroupWindowsReadsOnlyTheirOwnTable() {
        queryInterceptor.startQueryCount();
        assertThat(metisGroupWindowRepository.findAllByExerciseId(exercise.getId())).hasSize(1);

        assertReadsOnly("metis_group_window", queryInterceptor.getStatements());
    }

    @Test
    void loadingTheQuestionsReadsOnlyTheirOwnTable() {
        queryInterceptor.startQueryCount();
        assertThat(metisQuestionRepository.findAllByCheckIdOrderByQuestionOrderAsc(check.getId())).hasSize(1);

        assertReadsOnly("metis_question", queryInterceptor.getStatements());
    }

    // The structural statement behind the two above: nothing on a parent, inherited or declared, is of a Metis type. A lazy collection would run no SQL in
    // the tests above and still be an association every fetch has to leave out, so the mapping itself is checked.

    @Test
    void noParentEntityHasAnAttributeOfAMetisType() {
        List<String> pointingAtMetis = PARENT_ENTITIES.stream().flatMap(this::attributesOf).filter(MetisLazyLoadingIntegrationTest::pointsAtMetis)
                .map(attribute -> attribute.getDeclaringType().getJavaType().getSimpleName() + "." + attribute.getName()).toList();

        assertThat(pointingAtMetis).as("""
                An attribute of a Metis type on a parent is an inverse mapping towards Metis. A mappedBy one-to-one cannot be lazy without bytecode \
                enhancement, so every load of the parent would read the Metis table, and a collection is one more association every fetch has to leave \
                out. Metis finds its rows by the parent's id instead.""").isEmpty();
    }

    // The mirror of the test above, for the other direction: a Metis entity names its parent by id. A lazy owning-side association would pass the SQL
    // assertions above, since Hibernate reads the key column and resolves the parent on first access, so the mapping itself is checked here as well.

    @Test
    void noMetisEntityHasAnAssociation() {
        List<String> associations = METIS_ENTITIES.stream().flatMap(this::attributesOf).filter(Attribute::isAssociation)
                .map(attribute -> attribute.getDeclaringType().getJavaType().getSimpleName() + "." + attribute.getName()).toList();

        assertThat(associations).as("""
                A Metis entity names its parent by id. An association on it, even a lazy one, is a second select Hibernate may run on first access, a \
                join every fetch has to leave out, and on an exercise the eager load of a polymorphic parent.""").isEmpty();
    }

    /**
     * The load has to reach the database, otherwise the test proves nothing; and none of what it ran may name a Metis table.
     *
     * @param statements the SQL recorded since the load started
     */
    private static void assertParentLoadedWithoutReadingMetis(List<String> statements) {
        assertThat(statements).as("the load has to reach the database, otherwise this proves nothing").isNotEmpty();
        List<String> readingMetis = statements.stream().filter(sql -> sql.toLowerCase(Locale.ROOT).contains("metis_")).toList();
        assertThat(readingMetis).as("statements that read a Metis table while loading a parent").isEmpty();
    }

    /**
     * One statement, and the only table after FROM or JOIN in it is the Metis table itself. The check looks at what the statement reads rather than for
     * bare words, because the config's key column is exercise_id and the check's is participation_id: the parent's name legitimately appears in a column.
     *
     * @param table      the Metis table the load is allowed to read
     * @param statements the SQL recorded since the load started
     */
    private static void assertReadsOnly(String table, List<String> statements) {
        assertThat(statements).as("one select on %s and nothing else", table).hasSize(1);
        assertThat(tablesReadBy(statements.getFirst())).as("tables read by: %s", statements.getFirst()).containsExactly(table);
    }

    /**
     * Every identifier that follows FROM or JOIN, in order. A join lists its table as well, so a read of a single table yields exactly one entry.
     *
     * @param sql the statement as Hibernate executed it
     * @return the tables it reads
     */
    private static List<String> tablesReadBy(String sql) {
        Matcher matcher = TABLE_AFTER_FROM_OR_JOIN.matcher(sql.toLowerCase(Locale.ROOT));
        List<String> tables = new ArrayList<>();
        while (matcher.find()) {
            tables.add(matcher.group(1));
        }
        return tables;
    }

    private Stream<? extends Attribute<?, ?>> attributesOf(Class<?> entity) {
        return entityManagerFactory.getMetamodel().managedType(entity).getAttributes().stream();
    }

    /**
     * Whether the attribute holds a Metis entity, directly or as the element of a collection.
     *
     * @param attribute an attribute of a parent entity
     * @return true when its type, or its element type, lives in the Metis module
     */
    private static boolean pointsAtMetis(Attribute<?, ?> attribute) {
        Class<?> type = attribute instanceof PluralAttribute<?, ?, ?> plural ? plural.getElementType().getJavaType() : attribute.getJavaType();
        return type.getPackageName().startsWith(METIS_PACKAGE);
    }
}
