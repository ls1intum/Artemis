package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.dropIrisContentCollections;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertContent;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.insertMetadata;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.recreateIrisContentCollections;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.seedUnitWithAttachment;
import static de.tum.cit.aet.artemis.globalsearch.util.IngestionCoverageTestUtil.seedUnitWithVideoSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import io.weaviate.client6.v1.api.WeaviateClient;

/**
 * Integration test for the browser's gap lists against a real Weaviate Testcontainer and database.
 * <p>
 * Driven through {@link IngestionBrowserService}, which is how the endpoint reaches them, so the id-sets are loaded the
 * same once-per-open way here as in production.
 * <p>
 * Seeds a course whose database entities are only partially indexed, then asserts the browser names exactly what is
 * absent, resolves each gap to its title, and reports nothing for a type that is fully indexed. The decisive assertion is
 * the cross-check against the coverage matrix: for every type, the number of entities the browser names must equal the
 * missing count the matrix reports for the same course, because a browser that disagreed with the matrix would leave a
 * reader unable to tell which of the two was right.
 */
@EnabledIf("isWeaviateEnabled")
class IngestionBrowserGapServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private IngestionBrowserService browserService;

    @Autowired
    private CoverageRecomputeService coverageRecomputeService;

    @Autowired
    private IngestionCoverageRepository coverageRepository;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private WeaviateClient weaviateClient;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private AttachmentVideoUnitRepository attachmentVideoUnitRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private Course course;

    private Exercise presentExercise;

    private Exercise missingExercise;

    private AttachmentVideoUnit pdfUnit;

    private AttachmentVideoUnit videoUnit;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() throws Exception {
        recreateIrisContentCollections(weaviateClient);

        ZonedDateTime past = ZonedDateTime.now().minusDays(1);
        ZonedDateTime future = ZonedDateTime.now().plusDays(1);
        ZonedDateTime farFuture = ZonedDateTime.now().plusDays(2);

        course = courseUtilService.createCourse();
        presentExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        missingExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        Lecture lecture = lectureUtilService.createLecture(course);
        pdfUnit = seedUnitWithAttachment(attachmentVideoUnitRepository, attachmentRepository, lecture, "Slides unit", "attachments/attachment-unit/slides.pdf",
                AttachmentType.FILE);
        videoUnit = seedUnitWithVideoSource(attachmentVideoUnitRepository, lecture, "Video unit", "https://video.example/lecture");

        // Indexed: the course, the lecture, one of the two exercises, both units. The second exercise is deliberately
        // absent, so it is the one the browser must name.
        long courseId = course.getId();
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.COURSE, courseId, null);
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.LECTURE, lecture.getId(), null);
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.EXERCISE, presentExercise.getId(), null);
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, pdfUnit.getId(), null);
        insertMetadata(weaviateService, courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, videoUnit.getId(), null);

        // Content: slides for the PDF unit, so slides are complete; no transcript for the video unit, so it is a gap.
        insertContent(weaviateService, LECTURES_COLLECTION, courseId, pdfUnit.getId(), null);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            dropIrisContentCollections(weaviateClient);
        }
    }

    @Test
    void namesTheEntitiesTheIndexDoesNotHold() {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<MissingEntityDTO> missing = browserService.loadCourseBrowserData(course).missingEntities();

            assertThat(missing).extracting(MissingEntityDTO::type, MissingEntityDTO::entityId, MissingEntityDTO::title)
                    .contains(tuple(SearchableEntitySchema.TypeValues.EXERCISE, missingExercise.getId(), missingExercise.getTitle()));
            // The indexed exercise, units and course must not be reported as missing. Compared as (type, id) pairs
            // because an entity id is only unique within its type, so an id alone can collide across types.
            assertThat(missing).extracting(MissingEntityDTO::type, MissingEntityDTO::entityId).doesNotContain(
                    tuple(SearchableEntitySchema.TypeValues.EXERCISE, presentExercise.getId()), tuple(SearchableEntitySchema.TypeValues.LECTURE_UNIT, pdfUnit.getId()),
                    tuple(SearchableEntitySchema.TypeValues.LECTURE_UNIT, videoUnit.getId()), tuple(SearchableEntitySchema.TypeValues.COURSE, course.getId()));
        });
    }

    @Test
    void namedGapsMatchTheMatrixCountsForTheSameCourse() {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<MissingEntityDTO> missing = browserService.loadCourseBrowserData(course).missingEntities();
            IngestionCoverageDTO coverage = coverageRecomputeService.computeCoverageLive(List.of(course)).getFirst();

            for (IngestionTypeCountDTO typeCount : coverage.typeCounts()) {
                long named = missing.stream().filter(entity -> entity.type().equals(typeCount.type())).count();
                if (isMetadataType(typeCount.type())) {
                    assertThat(named).as("named missing entities for type %s", typeCount.type()).isEqualTo(typeCount.missing());
                }
            }
        });
    }

    @Test
    void namesTheUnitsWhoseContentWasNeverIngested() {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<MissingContentDTO> gaps = browserService.loadCourseBrowserData(course).contentGaps();

            // The video unit has no transcript; the PDF unit has slides, so it must not appear.
            assertThat(gaps).extracting(MissingContentDTO::lectureUnitId, MissingContentDTO::kind, MissingContentDTO::title)
                    .containsExactly(tuple(videoUnit.getId(), "transcript", videoUnit.getName()));
        });
    }

    @Test
    void reportsNoGapsOnceTheMissingContentIsIndexed() throws Exception {
        insertContent(weaviateService, LECTURE_TRANSCRIPTIONS_COLLECTION, course.getId(), videoUnit.getId(), null);

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(browserService.loadCourseBrowserData(course).contentGaps()).isEmpty());
    }

    @Test
    void keepsExpectedUnitMetadataButMarksRetainedSlidesOrphanedAfterItsPdfSourceWasRemoved() throws Exception {
        pdfUnit.setAttachment(null);
        attachmentVideoUnitRepository.save(pdfUnit);
        insertContent(weaviateService, LECTURE_TRANSCRIPTIONS_COLLECTION, course.getId(), videoUnit.getId(), null);
        insertContent(weaviateService, LECTURE_UNITS_COLLECTION, course.getId(), pdfUnit.getId(), null);
        insertContent(weaviateService, LECTURE_UNIT_SEGMENTS_COLLECTION, course.getId(), pdfUnit.getId(), null);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            var data = browserService.loadCourseBrowserData(course);

            assertThat(data.entities()).filteredOn(entity -> entity.type().equals(SearchableEntitySchema.TypeValues.LECTURE_UNIT) && entity.entityId() == pdfUnit.getId())
                    .singleElement().satisfies(entity -> assertThat(entity.expected()).isTrue());
            assertThat(data.contentGaps()).isEmpty();
            assertThat(data.contentPresence()).filteredOn(presence -> presence.key().equals(IngestionBrowserWeaviateReadService.KEY_SLIDES)).singleElement()
                    .satisfies(presence -> assertThat(presence.orphanedUnitIds()).containsExactly(pdfUnit.getId()));
            assertThat(data.contentPresence()).filteredOn(presence -> presence.key().equals(IngestionBrowserWeaviateReadService.KEY_TRANSCRIPT)).singleElement()
                    .satisfies(presence -> assertThat(presence.orphanedUnitIds()).isEmpty());
            assertThat(data.contentPresence()).filteredOn(presence -> presence.key().equals(IngestionBrowserWeaviateReadService.KEY_UNIT_SUMMARY)).singleElement()
                    .satisfies(presence -> assertThat(presence.orphanedUnitIds()).isEmpty());
            assertThat(data.contentPresence()).filteredOn(presence -> presence.key().equals(IngestionBrowserWeaviateReadService.KEY_SEGMENTS)).singleElement()
                    .satisfies(presence -> assertThat(presence.orphanedUnitIds()).isEmpty());
            assertThat(data.typeCounts()).filteredOn(count -> count.type().equals(CoverageRecomputeService.TYPE_SLIDES)).singleElement()
                    .satisfies(count -> assertThat(count)
                            .extracting(IngestionTypeCountDTO::expected, IngestionTypeCountDTO::indexed, IngestionTypeCountDTO::missing, IngestionTypeCountDTO::orphaned)
                            .containsExactly(0L, 1L, 0L, 1L));
        });
    }

    @Test
    void openingACourseReplacesItsStoredRowWithTheCountsItShows() {
        IngestionCoverageEntry stale = new IngestionCoverageEntry();
        stale.setCourseId(course.getId());
        stale.setTypeCounts(List.of(new IngestionTypeCountDTO("faq", 9, 0, 9, 0)));
        stale.setStatus(IngestionCoverageStatus.INCOMPLETE);
        stale.setComputedAt(ZonedDateTime.now().minusDays(1));
        coverageRepository.save(stale);

        List<IngestionTypeCountDTO> shown = browserService.loadCourseBrowserData(course).typeCounts();

        assertThat(coverageRepository.findByCourseId(course.getId()).orElseThrow().getTypeCounts()).isEqualTo(shown);
    }

    /** The metadata types the browser enumerates; the content and summary types are not entity types. */
    private static boolean isMetadataType(String type) {
        return IngestionCoverageWeaviateReadService.METADATA_TYPES.contains(type);
    }

}
