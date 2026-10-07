package de.tum.cit.aet.artemis.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.assessment.api.AssessmentDemoApi;
import de.tum.cit.aet.artemis.atlas.api.AtlasDemoApi;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyLectureUnitLinkRepository;
import de.tum.cit.aet.artemis.atlas.repository.CompetencyRepository;
import de.tum.cit.aet.artemis.communication.api.CommunicationDemoApi;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoCourseContentSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoDataSeedingService;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.lecture.api.dtos.DemoLectures;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureService;
import de.tum.cit.aet.artemis.lecture.service.LectureUnitService;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Tests the lectures that the {@code demo} profile seeds into the demo course.
 * <p>
 * Like {@link DemoDataSeedingIntegrationTest}, every test seeds the whole demo course and must stay correct whatever ran before it, because the demo course persists in the
 * shared test database.
 */
@Execution(ExecutionMode.SAME_THREAD)
class DemoLectureSeedingIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String ARCHITECTURE = "Software Architecture";

    private static final String ALGORITHMS = "Algorithms and Complexity";

    private static final String MODELING = "Object-Oriented Modeling";

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
    private CommunicationDemoApi communicationDemoApi;

    @Autowired
    private LectureRepository lectureRepository;

    @Autowired
    private LectureService lectureService;

    @Autowired
    private LectureUnitService lectureUnitService;

    @Autowired
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    @Autowired
    private SlideRepository slideRepository;

    @Autowired
    private CompetencyRepository competencyRepository;

    @Autowired
    private CompetencyLectureUnitLinkRepository competencyLectureUnitLinkRepository;

    @Test
    void seedsWeeklyLecturesWithTheirUnitsInOrder() {
        seed();
        // Seeds the lectures from scratch, because other tests leave a recreated unit at the end of its lecture. This also shows that deleted lectures come back.
        Stream.of(ARCHITECTURE, ALGORITHMS, MODELING).map(this::demoLecture).forEach(lecture -> lectureService.delete(lecture, false));
        seed();

        Lecture architecture = demoLecture(ARCHITECTURE);
        Lecture algorithms = demoLecture(ALGORITHMS);
        Lecture modeling = demoLecture(MODELING);
        ZonedDateTime now = ZonedDateTime.now();
        assertThat(List.of(architecture, algorithms, modeling)).allSatisfy(lecture -> {
            assertThat(lecture.getStartDate()).as("%s took place in the past weeks", lecture.getTitle()).isBetween(now.minusWeeks(4), now);
            assertThat(lecture.getEndDate()).as("%s is over", lecture.getTitle()).isAfter(lecture.getStartDate()).isBefore(now);
            assertThat(lecture.getDescription()).as("%s says what it is about", lecture.getTitle()).isNotBlank();
            assertThat(lecture.getLectureUnits()).as("students see every unit of %s", lecture.getTitle()).allMatch(LectureUnit::isVisibleToStudents);
        });
        assertThat(architecture.getStartDate()).as("the lectures follow the storyline week by week").isBefore(algorithms.getStartDate());
        assertThat(algorithms.getStartDate()).as("the lectures follow the storyline week by week").isBefore(modeling.getStartDate());

        long courseId = demoCourse().getId();
        Set<Long> architectureExercises = courseExerciseIds(courseId, TextExercise.class);
        Set<Long> algorithmsExercises = courseExerciseIds(courseId, FileUploadExercise.class, QuizExercise.class, ProgrammingExercise.class);
        Set<Long> modelingExercises = courseExerciseIds(courseId, ModelingExercise.class);
        assertThat(unitTypes(architecture)).as("the architecture lecture has its text, slides and further reading, followed by its exercises")
                .containsExactlyElementsOf(followedByExercises(List.of("text", "attachment", "online"), architectureExercises));
        assertThat(unitTypes(algorithms)).as("the algorithms lecture has its text and further reading, followed by its exercises")
                .containsExactlyElementsOf(followedByExercises(List.of("text", "online"), algorithmsExercises));
        assertThat(unitTypes(modeling)).as("the modeling lecture has its text and further reading, followed by its exercises")
                .containsExactlyElementsOf(followedByExercises(List.of("text", "online"), modelingExercises));
        assertThat(exerciseIds(architecture)).as("the architecture lecture links the essay about architecture").isNotEmpty().isEqualTo(architectureExercises);
        assertThat(exerciseIds(algorithms)).as("the algorithms lecture links the exercises about algorithms").isNotEmpty().isEqualTo(algorithmsExercises);
        assertThat(exerciseIds(modeling)).as("the modeling lecture links the modeling exercises").isNotEmpty().isEqualTo(modelingExercises);

        List<LectureUnit> units = Stream.of(architecture, algorithms, modeling).flatMap(lecture -> lecture.getLectureUnits().stream()).toList();
        assertThat(units).filteredOn(TextUnit.class::isInstance).map(TextUnit.class::cast).as("every lecture explains its topic").hasSize(3)
                .allSatisfy(unit -> assertThat(unit.getContent()).contains("## "));
        assertThat(units).filteredOn(OnlineUnit.class::isInstance).map(OnlineUnit.class::cast).as("every lecture points to further reading").hasSize(3).allSatisfy(unit -> {
            assertThat(unit.getSource()).startsWith("https://");
            assertThat(unit.getDescription()).isNotBlank();
        });
    }

    @Test
    void storesTheArchitectureSlidesAsPdfAndSplitsThemIntoSlides() throws IOException {
        seed();

        long slidesUnitId = unitOfType(demoLecture(ARCHITECTURE), AttachmentVideoUnit.class).getId();
        Attachment attachment = attachmentVideoUnitRepository.findWithAttachmentById(slidesUnitId).orElseThrow().getAttachment();
        assertThat(attachment.getAttachmentType()).as("the slides are an uploaded file").isEqualTo(AttachmentType.FILE);
        assertThat(attachment.isVisibleToStudents()).as("students can download the slides").isTrue();
        Path file = attachment.fileLocation().orElseThrow().path();
        assertThat(file).as("the slides are stored").exists();
        assertThat(file.getFileName().toString()).as("the slides are stored as PDF").endsWith(".pdf");
        try (PDDocument slides = Loader.loadPDF(file.toFile())) {
            int pages = slides.getNumberOfPages();
            assertThat(pages).as("the slides are a short deck").isBetween(3, 5);
            assertThat(new PDFTextStripper().getText(slides)).as("the slides are about software architecture").contains("Software Architecture", "Microservices");
            // Like an upload through the client, the PDF is split into its slides in the background.
            await().untilAsserted(() -> assertThat(slideRepository.findAllByAttachmentVideoUnitId(slidesUnitId)).as("every page becomes a slide").hasSize(pages));
        }
    }

    @Test
    void linksTheCompetencyToTheSeededUnitsOfTheArchitectureLecture() {
        seed();

        assertThat(linkedLectureUnitIds()).as("the competency is linked to the units of the architecture lecture except its exercise units, which link through their exercise")
                .isNotEmpty().isEqualTo(nonExerciseUnitIds(demoLecture(ARCHITECTURE)));
    }

    @Test
    void seedingTwiceCreatesNoLectureContent() {
        seed();
        Map<Long, List<Long>> afterFirstRun = snapshotLectures();

        seed();

        assertThat(snapshotLectures()).as("seeding again neither creates, replaces nor reorders lectures or their units").isEqualTo(afterFirstRun);
    }

    @Test
    void recreatesMissingTextUnitInExistingLecture() {
        seed();
        Lecture lecture = demoLecture(ARCHITECTURE);
        TextUnit textUnit = unitOfType(lecture, TextUnit.class);
        lectureUnitService.removeLectureUnit(textUnit);

        seed();

        Lecture afterReseeding = demoLecture(ARCHITECTURE);
        assertThat(afterReseeding.getId()).as("the existing lecture is reused").isEqualTo(lecture.getId());
        TextUnit recreatedUnit = unitOfType(afterReseeding, TextUnit.class);
        assertThat(recreatedUnit.getId()).as("the missing text unit is created again").isNotEqualTo(textUnit.getId());
        assertThat(recreatedUnit.getName()).as("the text unit is recreated as it was seeded").isEqualTo(textUnit.getName());
        assertThat(recreatedUnit.getContent()).as("the text unit is recreated as it was seeded").isEqualTo(textUnit.getContent());
        assertThat(unitIds(afterReseeding)).as("the other units are left alone").containsAll(unitIds(lecture).stream().filter(id -> !id.equals(textUnit.getId())).toList())
                .hasSameSizeAs(unitIds(lecture));
        assertThat(linkedLectureUnitIds()).as("the recreated unit is handed on, so the competency is linked to it").contains(recreatedUnit.getId());
    }

    @Test
    void leavesUnitsAddedByUsersAlone() {
        seed();
        Lecture lecture = demoLecture(ARCHITECTURE);
        lecture.addLectureUnit(LectureFactory.generateTextUnit("Unit added by an instructor", "Not part of the seeded demo content."));
        LectureUnit addedUnit = lectureRepository.saveAndFlush(lecture).getLectureUnits().getLast();
        try {
            seed();

            Lecture afterReseeding = demoLecture(ARCHITECTURE);
            assertThat(unitIds(afterReseeding)).as("the added unit is kept").contains(addedUnit.getId());
            assertThat(linkedLectureUnitIds()).as("seeding links no unit that a user added").doesNotContain(addedUnit.getId());
            // Without exercises, seeding only looks up the other units of the existing lectures.
            DemoLectures seededUnits = lectureDemoApi.createDemo(demoCourse(), List.of(), List.of(), List.of());
            assertThat(seededUnits.architecture()).extracting(LectureUnit::getId).as("only the seeded units of the lecture are returned")
                    .containsExactlyInAnyOrderElementsOf(nonExerciseUnitIds(afterReseeding).stream().filter(id -> !id.equals(addedUnit.getId())).toList());
        }
        finally {
            lectureUnitService.removeLectureUnit(addedUnit);
        }
    }

    @Test
    void relinksRecreatedCompetencyWithoutTouchingTheLectures() {
        seed();
        long courseId = demoCourse().getId();
        Map<Long, List<Long>> lecturesBefore = snapshotLectures();
        lectureRepository.findAllByCourseId(courseId).forEach(lecture -> competencyLectureUnitLinkRepository.deleteAllByLectureId(lecture.getId()));
        competencyRepository.deleteAllByCourseId(courseId);

        seed();

        assertThat(competencyRepository.findAllByCourseId(courseId)).as("the missing competency is recreated").isNotEmpty();
        assertThat(linkedLectureUnitIds()).as("the recreated competency is linked to the seeded units of the architecture lecture")
                .isEqualTo(nonExerciseUnitIds(demoLecture(ARCHITECTURE)));
        assertThat(snapshotLectures()).as("the lectures are left alone").isEqualTo(lecturesBefore);
    }

    @Test
    void seedsWithoutLectureModule() {
        seed();
        Map<Long, List<Long>> lecturesBefore = snapshotLectures();
        AtlasDemoApi atlasDemoApi = mock(AtlasDemoApi.class);
        DemoDataSeedingService withoutLectures = new DemoDataSeedingService(accountDemoApi, courseDemoApi, demoExerciseSeedingService,
                new DemoCourseContentSeedingService(Optional.empty(), Optional.of(atlasDemoApi), assessmentDemoApi, Optional.empty(), communicationDemoApi));

        assertThatCode(() -> withoutLectures.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent())).as("seeding must work when the lecture module is disabled")
                .doesNotThrowAnyException();

        verify(atlasDemoApi).createDemo(any(Course.class), eq(List.of()));
        assertThat(snapshotLectures()).as("a disabled lecture module leaves the existing lectures alone").isEqualTo(lecturesBefore);
    }

    private void seed() {
        demoDataSeedingService.seedDemoData(new DeferredEagerBeanInitializationCompletedEvent());
    }

    private Course demoCourse() {
        return courseRepository.findAllByShortName(CourseDemoApi.DEMO_COURSE_SHORT_NAME).getFirst();
    }

    private Lecture demoLecture(String title) {
        Set<Lecture> lectures = lectureRepository.findAllByTitleAndCourseIdWithLectureUnits(title, demoCourse().getId());
        assertThat(lectures).as("the demo course has exactly one lecture '%s'", title).hasSize(1);
        return lectures.iterator().next();
    }

    private static <T extends LectureUnit> T unitOfType(Lecture lecture, Class<T> type) {
        return lecture.getLectureUnits().stream().filter(type::isInstance).map(type::cast).findFirst().orElseThrow();
    }

    private static List<Long> unitIds(Lecture lecture) {
        return lecture.getLectureUnits().stream().map(LectureUnit::getId).toList();
    }

    private static Set<Long> nonExerciseUnitIds(Lecture lecture) {
        return lecture.getLectureUnits().stream().filter(unit -> !(unit instanceof ExerciseUnit)).map(LectureUnit::getId).collect(Collectors.toSet());
    }

    private static List<String> unitTypes(Lecture lecture) {
        return lecture.getLectureUnits().stream().map(LectureUnit::getType).toList();
    }

    private static List<String> followedByExercises(List<String> unitTypes, Set<Long> exerciseIds) {
        return Stream.concat(unitTypes.stream(), Collections.nCopies(exerciseIds.size(), "exercise").stream()).toList();
    }

    private static Set<Long> exerciseIds(Lecture lecture) {
        return lecture.getLectureUnits().stream().filter(ExerciseUnit.class::isInstance).map(unit -> ((ExerciseUnit) unit).getExercise().getId()).collect(Collectors.toSet());
    }

    /**
     * The exercises of the demo course of the given types, which is how the demo exercises are grouped into the topics of the course.
     */
    private Set<Long> courseExerciseIds(long courseId, Class<?>... types) {
        return exerciseRepository.findAllExercisesByCourseId(courseId).stream().filter(exercise -> Stream.of(types).anyMatch(type -> type.isInstance(exercise)))
                .map(Exercise::getId).collect(Collectors.toSet());
    }

    private Set<Long> linkedLectureUnitIds() {
        Set<Long> competencyIds = competencyRepository.findAllByCourseId(demoCourse().getId()).stream().map(Competency::getId).collect(Collectors.toSet());
        return competencyIds.isEmpty() ? Set.of() : competencyLectureUnitLinkRepository.findLectureUnitIdsByCompetencyIds(competencyIds);
    }

    /**
     * Captures the identities of the lectures of the demo course and the order of their units, so that replaced or reordered records are detected.
     */
    private Map<Long, List<Long>> snapshotLectures() {
        return lectureRepository.findAllByCourseIdWithEagerLectureUnits(demoCourse().getId()).stream()
                .collect(Collectors.toMap(Lecture::getId, DemoLectureSeedingIntegrationTest::unitIds));
    }
}
