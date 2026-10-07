package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.InstanceOfAssertFactories.DOUBLE;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyTaxonomy;
import de.tum.cit.aet.artemis.atlas.domain.competency.CourseCompetency;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.CourseCompetencyResponseDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathDTO;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyExerciseLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyProgressRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRelationRepository;
import de.tum.cit.aet.artemis.atlas.repository.CourseCompetencyRepository;
import de.tum.cit.aet.artemis.atlas.repository.LearningPathRepository;
import de.tum.cit.aet.artemis.communication.api.CommunicationDemoApi;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.dto.CourseAvailableTabsDTO;
import de.tum.cit.aet.artemis.course.service.CourseAvailableTabsService;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupDemoApi;

/**
 * Tests the competencies, learning paths and competency progress that the {@code demo} profile seeds into the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds the whole demo course and must stay correct whatever ran before it, because the demo course persists in the
 * shared test database.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoAtlasSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String JAVA = "Program in Java";

    private static final String ARCHITECTURE = "Evaluate architectural styles";

    private static final String ALGORITHMS = "Analyze algorithm complexity";

    private static final String MODELING = "Model a domain with class diagrams";

    private static final String COMMUNICATION = "Communicate design decisions";

    private static final String ARCHITECTURE_LECTURE = "Software Architecture";

    private static final String ALGORITHMS_LECTURE = "Algorithms and Complexity";

    private static final String MODELING_LECTURE = "Object-Oriented Modeling";

    @Autowired
    private DemoDataSeedingService demoDataSeedingService;

    @Autowired
    private AccountDemoApi accountDemoApi;

    @Autowired
    private CourseDemoApi courseDemoApi;

    @Autowired
    private DemoExerciseSeedingService demoExerciseSeedingService;

    @Autowired
    private LectureDemoApi lectureDemoApi;

    @Autowired
    private AssessmentDemoApi assessmentDemoApi;

    @Autowired
    private TutorialGroupDemoApi tutorialGroupDemoApi;

    @Autowired
    private CommunicationDemoApi communicationDemoApi;

    @Autowired
    private CourseCompetencyRepository courseCompetencyRepository;

    @Autowired
    private CompetencyRelationRepository competencyRelationRepository;

    @Autowired
    private CompetencyExerciseLinkRepository competencyExerciseLinkRepository;

    @Autowired
    private CompetencyProgressRepository competencyProgressRepository;

    @Autowired
    private LearningPathRepository learningPathRepository;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private CourseAvailableTabsService courseAvailableTabsService;

    @Test
    @WithMockUser(username = AccountDemoApi.DEMO_STUDENT_LOGIN, roles = "USER")
    void demoStudentSeesTheCompetenciesWithTheirProgress() throws Exception {
        seed();
        Course course = demoCourse();

        CourseAvailableTabsDTO tabs = courseAvailableTabsService.getAvailableTabs(course, demoStudent());
        assertThat(tabs.competencies()).as("the course shows its competencies").isTrue();
        assertThat(tabs.learningPaths()).as("the course shows the learning path of the student").isTrue();

        Map<String, CourseCompetencyResponseDTO> competencies = request
                .getList("/api/atlas/courses/" + course.getId() + "/course-competencies", HttpStatus.OK, CourseCompetencyResponseDTO.class).stream()
                .collect(Collectors.toMap(CourseCompetencyResponseDTO::title, Function.identity()));
        assertThat(competencies.values()).as("Java is what students bring to the course, and each topic of the course has a competency, plus one that spans them")
                .extracting(CourseCompetencyResponseDTO::title, CourseCompetencyResponseDTO::type, CourseCompetencyResponseDTO::taxonomy)
                .containsExactlyInAnyOrder(tuple(JAVA, "prerequisite", CompetencyTaxonomy.APPLY), tuple(ARCHITECTURE, "competency", CompetencyTaxonomy.EVALUATE),
                        tuple(ALGORITHMS, "competency", CompetencyTaxonomy.ANALYZE), tuple(MODELING, "competency", CompetencyTaxonomy.CREATE),
                        tuple(COMMUNICATION, "competency", CompetencyTaxonomy.UNDERSTAND));
        assertThat(competencies.values()).allSatisfy(competency -> {
            assertThat(competency.description()).as("%s says what students are able to do", competency.title()).isNotBlank();
            assertThat(competency.masteryThreshold()).as("%s can be mastered without a perfect score", competency.title()).isPositive().isLessThan(100);
        });

        assertThat(competencies.get(ARCHITECTURE).userProgress()).singleElement().extracting(CompetencyProgressDTO::progress, DOUBLE)
                .as("the demo student worked through the architecture lecture").isPositive();
        assertThat(competencies.get(COMMUNICATION).userProgress()).singleElement().extracting(CompetencyProgressDTO::progress, DOUBLE)
                .as("the demo student wrote the graded essay about code reviews").isPositive();
        assertThat(competencies.get(MODELING).userProgress()).singleElement().extracting(CompetencyProgressDTO::progress, DOUBLE)
                .as("the demo student has not started on the modeling lecture of last week yet").isZero();
    }

    @Test
    void demoStudentCompletedTheFirstLectureAndTheTextOfTheSecond() {
        seed();

        Map<Long, ZonedDateTime> completions = completionsOfDemoStudent();
        Lecture algorithms = demoLecture(ALGORITHMS_LECTURE);
        assertThat(completions.keySet()).as("the demo student completed the architecture lecture and read the text about algorithms, but nothing of the modeling lecture")
                .containsExactlyInAnyOrderElementsOf(
                        Stream.concat(nonExerciseUnits(demoLecture(ARCHITECTURE_LECTURE)).stream(), algorithms.getLectureUnits().stream().filter(TextUnit.class::isInstance))
                                .map(LectureUnit::getId).toList());
    }

    @Test
    void relatesTheCompetenciesToWhatTheyBuildOn() {
        seed();

        assertThat(competencyRelationRepository.findAllWithHeadAndTailByCourseId(demoCourse().getId()))
                .extracting(relation -> relation.getTailCompetency().getTitle(), CompetencyRelation::getType, relation -> relation.getHeadCompetency().getTitle())
                .containsExactlyInAnyOrder(tuple(ALGORITHMS, RelationType.ASSUMES, JAVA), tuple(MODELING, RelationType.ASSUMES, JAVA),
                        tuple(COMMUNICATION, RelationType.EXTENDS, ARCHITECTURE), tuple(COMMUNICATION, RelationType.ASSUMES, MODELING));
    }

    @Test
    void linksEveryCompetencyToTheLearningObjectsOfItsTopic() {
        seed();

        long courseId = demoCourse().getId();
        assertThat(links(ARCHITECTURE)).as("the architecture competency is linked to the essay about architecture and the units of its lecture")
                .isEqualTo(new Links(essays(courseId, 1, true), units(ARCHITECTURE_LECTURE)));
        assertThat(links(ALGORITHMS)).as("the algorithms competency is linked to the exercises about algorithms and the units of its lecture")
                .isEqualTo(new Links(exercises(courseId, 1, FileUploadExercise.class, QuizExercise.class, ProgrammingExercise.class), units(ALGORITHMS_LECTURE)));
        assertThat(links(MODELING)).as("the modeling competency is linked to the modeling exercises and the units of its lecture")
                .isEqualTo(new Links(exercises(courseId, 1, ModelingExercise.class), units(MODELING_LECTURE)));
        Map<Long, Double> communicationExercises = new HashMap<>(exercises(courseId, 0.5, ModelingExercise.class));
        communicationExercises.putAll(essays(courseId, 0.5, true));
        communicationExercises.putAll(essays(courseId, 1, false));
        assertThat(links(COMMUNICATION)).as("the essay about code reviews is about communicating design decisions, which the other essay and the class diagrams practise as well")
                .isEqualTo(new Links(communicationExercises, Map.of()));
        assertThat(links(JAVA)).as("students bring Java to the course").isEqualTo(new Links(Map.of(), Map.of()));
    }

    @Test
    void neverLinksUnitsThatUsersAdded() {
        seed();
        Lecture lecture = demoLecture(ARCHITECTURE_LECTURE);
        lecture.addLectureUnit(LectureFactory.generateTextUnit("Unit added by an instructor", "Not part of the seeded demo content."));
        LectureUnit addedUnit = lectureRepository.saveAndFlush(lecture).getLectureUnits().getLast();
        try {
            seed();

            assertThat(Stream.of(JAVA, ARCHITECTURE, ALGORITHMS, MODELING, COMMUNICATION).map(this::links))
                    .as("seeding links no unit that a user added, but keeps linking the seeded ones")
                    .allSatisfy(links -> assertThat(links.lectureUnits()).doesNotContainKey(addedUnit.getId())).anySatisfy(links -> assertThat(links.lectureUnits()).isNotEmpty());
            assertThat(completionsOfDemoStudent()).as("nor does the demo student complete it").doesNotContainKey(addedUnit.getId());
        }
        finally {
            lectureUnitService.removeLectureUnit(addedUnit);
        }
    }

    @Test
    @WithMockUser(username = AccountDemoApi.DEMO_STUDENT_LOGIN, roles = "USER")
    void enablesLearningPathsForEveryStudent() throws Exception {
        seed();

        Course course = demoCourse();
        assertThat(course.getLearningPathsEnabled()).as("the learning paths of the course are enabled").isTrue();
        assertThat(demoStudents()).as("every student of the course has a learning path")
                .allSatisfy(student -> assertThat(learningPathRepository.findByCourseIdAndUserId(course.getId(), student.getId())).isPresent());

        LearningPathDTO learningPath = request.get("/api/atlas/courses/" + course.getId() + "/learning-path/me", HttpStatus.OK, LearningPathDTO.class);
        assertThat(learningPath.id()).as("the demo student finds their learning path")
                .isEqualTo(learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), demoStudent().getId()).getId());
    }

    @Test
    void seedingTwiceCreatesNothingNew() {
        seed();
        AtlasSnapshot afterFirstRun = snapshot();

        seed();

        assertThat(snapshot()).as("seeding again neither creates nor replaces competencies, relations, links, learning paths or completions").isEqualTo(afterFirstRun);
    }

    @Test
    @WithMockUser(username = AccountDemoApi.DEMO_INSTRUCTOR_LOGIN, roles = "INSTRUCTOR")
    void recreatesAndRelinksDeletedCompetency() throws Exception {
        seed();
        long courseId = demoCourse().getId();
        AtlasSnapshot seeded = snapshot();
        long deletedId = seeded.competencyIds().get(ALGORITHMS);
        Links linksBefore = links(ALGORITHMS);
        request.delete("/api/atlas/courses/" + courseId + "/competencies/" + deletedId, HttpStatus.OK);

        seed();

        AtlasSnapshot reseeded = snapshot();
        long recreatedId = reseeded.competencyIds().get(ALGORITHMS);
        assertThat(recreatedId).as("the deleted competency is created again").isNotEqualTo(deletedId);
        assertThat(links(ALGORITHMS)).as("with the links it had").isEqualTo(linksBefore);
        assertThat(competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId))
                .filteredOn(relation -> relation.getTailCompetency().getId().equals(recreatedId) || relation.getHeadCompetency().getId().equals(recreatedId))
                .extracting(relation -> relation.getTailCompetency().getTitle(), CompetencyRelation::getType, relation -> relation.getHeadCompetency().getTitle())
                .as("and the relations it had").containsExactly(tuple(ALGORITHMS, RelationType.ASSUMES, JAVA));
        assertThat(competencyProgressRepository.findByCompetencyIdAndUserId(recreatedId, demoStudent().getId())).as("and the progress of the demo student in it").isPresent();
        assertThat(reseeded.competencyIds()).as("the other competencies are left alone").containsAllEntriesOf(
                seeded.competencyIds().entrySet().stream().filter(entry -> !ALGORITHMS.equals(entry.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }

    @Test
    void restoresLostLinksAndLinksRecreatedUnits() {
        seed();
        long courseId = demoCourse().getId();
        CourseCompetency architecture = competency(ARCHITECTURE);
        long essayId = essays(courseId, 1, true).keySet().iterator().next();
        competencyExerciseLinkRepository.delete(competencyExerciseLinkRepository.findByExerciseIdAndCompetencyId(essayId, architecture.getId()).orElseThrow());
        TextUnit textUnit = demoLecture(ARCHITECTURE_LECTURE).getLectureUnits().stream().filter(TextUnit.class::isInstance).map(TextUnit.class::cast).findFirst().orElseThrow();
        lectureUnitService.removeLectureUnit(textUnit);

        seed();

        Links links = links(ARCHITECTURE);
        assertThat(links.exercises()).as("the lost link to the essay is restored").containsKey(essayId);
        assertThat(links).as("the recreated text unit is linked as well").isEqualTo(new Links(essays(courseId, 1, true), units(ARCHITECTURE_LECTURE)));
        assertThat(links.lectureUnits()).doesNotContainKey(textUnit.getId());
        LectureUnit recreatedUnit = demoLecture(ARCHITECTURE_LECTURE).getLectureUnits().stream().filter(TextUnit.class::isInstance).findFirst().orElseThrow();
        assertThat(completionsOfDemoStudent()).as("the demo student completed the recreated text unit as well").containsKey(recreatedUnit.getId());
    }

    @Test
    void seedsWithoutAtlasModule() {
        seed();
        AtlasSnapshot beforeRun = snapshot();

        DemoDataSeedingService withoutAtlas = new DemoDataSeedingService(accountDemoApi, courseDemoApi, demoExerciseSeedingService,
                new DemoCourseContentSeedingService(Optional.of(lectureDemoApi), Optional.empty(), assessmentDemoApi, Optional.of(tutorialGroupDemoApi), communicationDemoApi));
        assertThatCode(() -> withoutAtlas.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent())).as("seeding must work when the atlas module is disabled")
                .doesNotThrowAnyException();

        assertThat(snapshot()).as("a disabled atlas module leaves the existing competencies alone").isEqualTo(beforeRun);
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    private User demoStudent() {
        return userTestRepository.findOneByLogin(AccountDemoApi.DEMO_STUDENT_LOGIN).orElseThrow();
    }

    /**
     * The demo student followed by their classmates, who already exist because every test seeds first.
     */
    private List<User> demoStudents() {
        return accountDemoApi.createDemoUsers().students();
    }

    private Lecture demoLecture(String title) {
        Set<Lecture> lectures = lectureRepository.findAllByTitleAndCourseIdWithLectureUnits(title, demoCourse().getId());
        assertThat(lectures).as("the demo course has exactly one lecture '%s'", title).hasSize(1);
        return lectures.iterator().next();
    }

    private static List<LectureUnit> nonExerciseUnits(Lecture lecture) {
        return lecture.getLectureUnits().stream().filter(unit -> !(unit instanceof ExerciseUnit)).toList();
    }

    private CourseCompetency competency(String title) {
        return courseCompetencyRepository.findAllForCourse(demoCourse().getId()).stream().filter(competency -> title.equals(competency.getTitle())).findFirst().orElseThrow();
    }

    /**
     * The learning objects that the competency with the given title is linked to, by their id, with the weight of their link.
     */
    private Links links(String title) {
        CourseCompetency competency = courseCompetencyRepository.findByIdWithLectureUnitsAndExercisesElseThrow(competency(title).getId());
        return new Links(competency.getExerciseLinks().stream().collect(Collectors.toMap(link -> link.getExercise().getId(), CompetencyExerciseLink::getWeight)),
                competency.getLectureUnitLinks().stream().collect(Collectors.toMap(link -> link.getLectureUnit().getId(), CompetencyLectureUnitLink::getWeight)));
    }

    /**
     * The exercises of the demo course of the given types, which is how the demo exercises are grouped into the topics of the course, with the weight of their link.
     */
    /**
     * The essays of the demo course with the given weight: the open one is about software architecture, the graded one about code reviews.
     */
    private Map<Long, Double> essays(long courseId, double weight, boolean open) {
        return exerciseRepository.findAllExercisesByCourseId(courseId).stream()
                .filter(exercise -> exercise instanceof TextExercise && exercise.getDueDate().isAfter(ZonedDateTime.now()) == open)
                .collect(Collectors.toMap(Exercise::getId, exercise -> weight));
    }

    private Map<Long, Double> exercises(long courseId, double weight, Class<?>... types) {
        return exerciseRepository.findAllExercisesByCourseId(courseId).stream().filter(exercise -> Stream.of(types).anyMatch(type -> type.isInstance(exercise)))
                .collect(Collectors.toMap(Exercise::getId, exercise -> weight));
    }

    /**
     * The units of the demo lecture with the given title that are linked to its competency, with the weight of their link: exercise units link through their exercise, and
     * the further reading weighs least.
     */
    private Map<Long, Double> units(String lectureTitle) {
        return nonExerciseUnits(demoLecture(lectureTitle)).stream().collect(Collectors.toMap(LectureUnit::getId, unit -> unit instanceof OnlineUnit ? 0.25 : 0.5));
    }

    /**
     * When the demo student completed the units of the demo lectures, by unit.
     */
    private Map<Long, ZonedDateTime> completionsOfDemoStudent() {
        List<LectureUnit> units = lectureRepository.findAllByCourseIdWithEagerLectureUnits(demoCourse().getId()).stream().flatMap(lecture -> lecture.getLectureUnits().stream())
                .toList();
        return lectureUnitCompletionRepository.findByLectureUnitsAndUserId(units, demoStudent().getId()).stream()
                .collect(Collectors.toMap(completion -> completion.getLectureUnit().getId(), LectureUnitCompletion::getCompletedAt));
    }

    /**
     * Captures the identities of what this area seeds, so that replaced records are detected: the links and completions have no ids of their own, so their weights and
     * completion dates stand in for them.
     */
    private AtlasSnapshot snapshot() {
        long courseId = demoCourse().getId();
        Map<String, Long> competencyIds = courseCompetencyRepository.findAllForCourse(courseId).stream()
                .collect(Collectors.toMap(CourseCompetency::getTitle, CourseCompetency::getId));
        return new AtlasSnapshot(competencyIds,
                competencyRelationRepository.findAllWithHeadAndTailByCourseId(courseId).stream().map(CompetencyRelation::getId).collect(Collectors.toSet()),
                competencyIds.keySet().stream().collect(Collectors.toMap(Function.identity(), this::links)),
                learningPathRepository.findAllByCourseIdForEnrolledStudents(courseId).stream().map(LearningPath::getId).collect(Collectors.toSet()), completionsOfDemoStudent());
    }

    private record Links(Map<Long, Double> exercises, Map<Long, Double> lectureUnits) {
    }

    private record AtlasSnapshot(Map<String, Long> competencyIds, Set<Long> relationIds, Map<String, Links> links, Set<Long> learningPathIds,
            Map<Long, ZonedDateTime> completions) {
    }
}
