package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.lock.DistributedLock;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageEntry;
import de.tum.cit.aet.artemis.globalsearch.domain.IngestionCoverageStatus;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.repository.IngestionCoverageRepository;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettingsEntity;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseSettingsRepository;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentType;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentVideoUnitRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import io.weaviate.client6.v1.api.WeaviateClient;
import io.weaviate.client6.v1.api.collections.Property;
import io.weaviate.client6.v1.api.collections.VectorConfig;

/**
 * Integration test for {@link CoverageRecomputeService} against a real Weaviate Testcontainer and database.
 * <p>
 * Seeds a course whose database entities are only partially present in Weaviate, runs the recompute, and asserts the
 * stored projection holds the exact per-type diff (a metadata type with a missing entity, an exact content gap, and the
 * present-only summary behavior), the derived status and gap score, deletion of rows for courses no longer in the
 * database, and that the cluster lock makes a recompute a no-op while another holder is running.
 */
@EnabledIf("isWeaviateEnabled")
class CoverageRecomputeServiceTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    @Autowired
    private CoverageRecomputeService coverageRecomputeService;

    @Autowired
    private IngestionCoverageRepository coverageRepository;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private WeaviateClient weaviateClient;

    @Autowired
    private IrisCourseSettingsRepository irisCourseSettingsRepository;

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

    @Autowired
    private DistributedDataProvider distributedDataProvider;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private static final String CONTENT_COURSE_ID = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID = "lecture_unit_id";

    private static final List<String> IRIS_CONTENT_COLLECTIONS = List.of(LECTURES_COLLECTION, IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION,
            LECTURE_UNIT_SEGMENTS_COLLECTION, LECTURE_UNITS_COLLECTION);

    private Course course;

    private Exercise presentExercise;

    private Exercise missingExercise;

    private AttachmentVideoUnit pdfUnit;

    private AttachmentVideoUnit videoUnit;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    /** Writes the course an explicitly disabled Iris settings row; without one a course counts as enabled. */
    private void disableIrisForCourse(long courseId) {
        IrisCourseSettingsEntity settings = new IrisCourseSettingsEntity();
        settings.setCourseId(courseId);
        settings.setSettings(new IrisCourseSettings(false, null, IrisPipelineVariant.DEFAULT, IrisSupportLevel.MODERATE, null, null, null));
        irisCourseSettingsRepository.save(settings);
    }

    @BeforeEach
    void setUp() throws Exception {
        coverageRepository.deleteAll();
        recreateIrisContentCollections();

        ZonedDateTime past = ZonedDateTime.now().minusDays(1);
        ZonedDateTime future = ZonedDateTime.now().plusDays(1);
        ZonedDateTime farFuture = ZonedDateTime.now().plusDays(2);

        course = courseUtilService.createCourse();
        presentExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        missingExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        Lecture lecture = lectureUtilService.createLecture(course);
        pdfUnit = seedUnitWithAttachmentLink(lecture, "attachments/attachment-unit/slides.pdf");
        videoUnit = seedUnitWithVideoSource(lecture, "https://video.example/lecture");

        // Metadata present in Weaviate: the course, one of the two exercises, both indexable units. The second exercise
        // is deliberately absent, so it must surface as a missing exercise.
        long courseId = course.getId();
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.COURSE, courseId);
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE, lecture.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.EXERCISE, presentExercise.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, pdfUnit.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, videoUnit.getId());

        // Content present: slides for the pdf unit (so slides are complete) and both summaries; NO transcript for the
        // video unit (so transcript is an exact gap).
        insertContent(LECTURES_COLLECTION, courseId, pdfUnit.getId());
        insertContent(LECTURE_UNIT_SEGMENTS_COLLECTION, courseId, pdfUnit.getId());
        insertContent(LECTURE_UNITS_COLLECTION, courseId, pdfUnit.getId());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            dropIrisContentCollections();
        }
    }

    @Test
    void computesExactPerTypeCoverageWithContentGapAndPresentOnlySummaries() {
        // Weaviate reads are eventually consistent; recompute is an idempotent upsert, so retry until the seeded objects
        // are visible and the projection matches.
        await().atMost(TIMEOUT).untilAsserted(() -> {
            coverageRecomputeService.recomputeAllCourses();
            IngestionCoverageEntry entry = coverageRepository.findByCourseId(course.getId()).orElseThrow();

            assertThat(typeCount(entry, SearchableEntitySchema.TypeValues.EXERCISE)).isEqualTo(new IngestionTypeCountDTO(SearchableEntitySchema.TypeValues.EXERCISE, 2, 1, 1, 0));
            assertThat(typeCount(entry, CoverageRecomputeService.TYPE_SLIDES)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_SLIDES, 1, 1, 0, 0));
            assertThat(typeCount(entry, CoverageRecomputeService.TYPE_TRANSCRIPT)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_TRANSCRIPT, 1, 0, 1, 0));
            // Present-only: reported as indexed, never missing, even though no expected set was diffed.
            assertThat(typeCount(entry, CoverageRecomputeService.TYPE_SEGMENT_SUMMARY))
                    .isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_SEGMENT_SUMMARY, 1, 1, 0, 0));

            assertThat(entry.getStatus()).isEqualTo(IngestionCoverageStatus.INCOMPLETE);
            // One missing exercise + one missing transcript.
            assertThat(entry.getCoverageGapScore()).isEqualTo(2);
            assertThat(entry.getLastIngestedAt()).isNotNull();
            assertThat(entry.getCourseTitle()).isEqualTo(course.getTitle());
        });
    }

    @Test
    void anOrphanOnlyCourseIsIncompleteAndScoredRatherThanReportedComplete() throws Exception {
        // A second course with nothing missing: its only expected object, the course itself, is indexed. Then one
        // stale exercise object is planted for an id the database does not have, which is exactly the orphan shape.
        Course orphanOnly = courseUtilService.createCourse();
        long orphanCourseId = orphanOnly.getId();
        insertMetadata(orphanCourseId, SearchableEntitySchema.TypeValues.COURSE, orphanCourseId);
        insertMetadata(orphanCourseId, SearchableEntitySchema.TypeValues.EXERCISE, 999_999_999L);

        await().atMost(TIMEOUT).untilAsserted(() -> {
            coverageRecomputeService.recomputeAllCourses();
            IngestionCoverageEntry entry = coverageRepository.findByCourseId(orphanCourseId).orElseThrow();

            assertThat(typeCount(entry, SearchableEntitySchema.TypeValues.EXERCISE)).isEqualTo(new IngestionTypeCountDTO(SearchableEntitySchema.TypeValues.EXERCISE, 0, 1, 0, 1));

            // Nothing is missing, so before orphans were counted this row read COMPLETE with score 0 while its matrix
            // cell was red. Status, severity and the cell have to agree.
            assertThat(entry.getStatus()).isEqualTo(IngestionCoverageStatus.INCOMPLETE);
            assertThat(entry.getCoverageGapScore()).isEqualTo(1);
        });
    }

    @Test
    void aCourseWithIrisDisabledExpectsNoLectureContent() {
        // Slides and transcript live in the Iris collections, which are only written for a course that has Iris
        // enabled. Before the expected sets applied that rule, this course reported its PDF and its video missing on
        // every recompute: a gap that no amount of re-ingesting could ever close.
        Course irisDisabled = courseUtilService.createCourse();
        Lecture lecture = lectureUtilService.createLecture(irisDisabled);
        seedUnitWithAttachmentLink(lecture, "attachments/attachment-unit/slides.pdf");
        seedUnitWithVideoSource(lecture, "https://video.example/iris-disabled");
        disableIrisForCourse(irisDisabled.getId());

        await().atMost(TIMEOUT).untilAsserted(() -> {
            coverageRecomputeService.recomputeAllCourses();
            IngestionCoverageEntry entry = coverageRepository.findByCourseId(irisDisabled.getId()).orElseThrow();

            assertThat(typeCount(entry, CoverageRecomputeService.TYPE_SLIDES)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_SLIDES, 0, 0, 0, 0));
            assertThat(typeCount(entry, CoverageRecomputeService.TYPE_TRANSCRIPT)).isEqualTo(new IngestionTypeCountDTO(CoverageRecomputeService.TYPE_TRANSCRIPT, 0, 0, 0, 0));
        });
    }

    @Test
    void removesRowsForCoursesNoLongerInTheDatabase() {
        IngestionCoverageEntry stale = new IngestionCoverageEntry();
        stale.setCourseId(99_999_999L);
        stale.setTypeCounts(List.of());
        stale.setCoverageGapScore(0);
        stale.setStatus(IngestionCoverageStatus.EMPTY);
        stale.setActive(false);
        stale.setComputedAt(ZonedDateTime.now());
        coverageRepository.save(stale);

        coverageRecomputeService.recomputeAllCourses();

        assertThat(coverageRepository.findByCourseId(99_999_999L)).isEmpty();
    }

    @Test
    void clusterLockMakesConcurrentRecomputeANoOp() throws Exception {
        // Distributed locks are reentrant per thread, so the lock must be held by a DIFFERENT thread to stand in for
        // another cluster node; holding it on the test thread would let runUnderLock re-enter and defeat the check.
        DistributedLock lock = distributedDataProvider.getLock("ingestion-coverage-recompute");
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            lock.lock();
            locked.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            finally {
                lock.unlock();
            }
        });
        holder.start();
        assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

        // Another thread (node) holds the lock, so a triggered recompute must skip rather than run.
        assertThat(coverageRecomputeService.runUnderLock(false)).isFalse();

        release.countDown();
        holder.join(5000);

        // With the lock free, it runs.
        assertThat(coverageRecomputeService.runUnderLock(false)).isTrue();
    }

    private IngestionTypeCountDTO typeCount(IngestionCoverageEntry entry, String type) {
        return entry.getTypeCounts().stream().filter(count -> count.type().equals(type)).findFirst().orElseThrow();
    }

    private void insertMetadata(long courseId, String type, long entityId) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(SearchableEntitySchema.Properties.COURSE_ID, courseId);
        properties.put(SearchableEntitySchema.Properties.TYPE, type);
        properties.put(SearchableEntitySchema.Properties.ENTITY_ID, entityId);
        weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME).data.insert(properties);
    }

    private void insertContent(String collectionName, long courseId, long unitId) throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put(CONTENT_COURSE_ID, courseId);
        properties.put(CONTENT_LECTURE_UNIT_ID, unitId);
        weaviateService.getExternalCollection(collectionName).data.insert(properties);
    }

    private void recreateIrisContentCollections() throws Exception {
        dropIrisContentCollections();
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            weaviateClient.collections.create(name, collection -> {
                collection.vectorConfig(VectorConfig.selfProvided());
                collection.properties(Property.integer(CONTENT_COURSE_ID));
                collection.properties(Property.integer(CONTENT_LECTURE_UNIT_ID));
                return collection;
            });
        }
    }

    private void dropIrisContentCollections() throws Exception {
        for (String name : IRIS_CONTENT_COLLECTIONS) {
            if (weaviateClient.collections.exists(name)) {
                weaviateClient.collections.delete(name);
            }
        }
    }

    private AttachmentVideoUnit seedUnitWithVideoSource(Lecture lecture, String videoSource) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit.setVideoSource(videoSource);
        return attachmentVideoUnitRepository.save(unit);
    }

    private AttachmentVideoUnit seedUnitWithAttachmentLink(Lecture lecture, String link) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit = attachmentVideoUnitRepository.save(unit);

        Attachment attachment = new Attachment();
        attachment.setAttachmentType(AttachmentType.FILE);
        attachment.setName("Attachment");
        attachment.setVersion(1);
        attachment.setReleaseDate(ZonedDateTime.now().minusDays(1));
        attachment.setUploadDate(ZonedDateTime.now().minusDays(1));
        attachment.setLink(link);
        attachment.setAttachmentVideoUnit(unit);
        attachmentRepository.save(attachment);

        unit.setAttachment(attachment);
        return attachmentVideoUnitRepository.save(unit);
    }
}
