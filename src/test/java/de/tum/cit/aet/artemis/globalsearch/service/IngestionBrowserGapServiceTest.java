package de.tum.cit.aet.artemis.globalsearch.service;

import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURES_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_TRANSCRIPTIONS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNITS_COLLECTION;
import static de.tum.cit.aet.artemis.globalsearch.service.IngestionCoverageWeaviateReadService.LECTURE_UNIT_SEGMENTS_COLLECTION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionCoverageDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.IngestionTypeCountDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingContentDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.MissingEntityDTO;
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
 * Integration test for {@link IngestionBrowserGapService} against a real Weaviate Testcontainer and database.
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
    private IngestionBrowserGapService gapService;

    @Autowired
    private CoverageRecomputeService coverageRecomputeService;

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

    private static final String CONTENT_COURSE_ID = "course_id";

    private static final String CONTENT_LECTURE_UNIT_ID = "lecture_unit_id";

    private static final List<String> IRIS_CONTENT_COLLECTIONS = List.of(LECTURES_COLLECTION, LECTURE_TRANSCRIPTIONS_COLLECTION, LECTURE_UNIT_SEGMENTS_COLLECTION,
            LECTURE_UNITS_COLLECTION);

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
        recreateIrisContentCollections();

        ZonedDateTime past = ZonedDateTime.now().minusDays(1);
        ZonedDateTime future = ZonedDateTime.now().plusDays(1);
        ZonedDateTime farFuture = ZonedDateTime.now().plusDays(2);

        course = courseUtilService.createCourse();
        presentExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        missingExercise = exerciseRepository.save(TextExerciseFactory.generateTextExercise(past, future, farFuture, course));
        Lecture lecture = lectureUtilService.createLecture(course);
        pdfUnit = seedUnitWithAttachmentLink(lecture, "Slides unit", "attachments/attachment-unit/slides.pdf");
        videoUnit = seedUnitWithVideoSource(lecture, "Video unit", "https://video.example/lecture");

        // Indexed: the course, the lecture, one of the two exercises, both units. The second exercise is deliberately
        // absent, so it is the one the browser must name.
        long courseId = course.getId();
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.COURSE, courseId);
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE, lecture.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.EXERCISE, presentExercise.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, pdfUnit.getId());
        insertMetadata(courseId, SearchableEntitySchema.TypeValues.LECTURE_UNIT, videoUnit.getId());

        // Content: slides for the PDF unit, so slides are complete; no transcript for the video unit, so it is a gap.
        insertContent(LECTURES_COLLECTION, courseId, pdfUnit.getId());
    }

    @AfterEach
    void tearDown() throws Exception {
        if (weaviateClient != null) {
            dropIrisContentCollections();
        }
    }

    @Test
    void namesTheEntitiesTheIndexDoesNotHold() {
        await().atMost(TIMEOUT).untilAsserted(() -> {
            List<MissingEntityDTO> missing = gapService.missingEntitiesForCourse(course.getId());

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
            List<MissingEntityDTO> missing = gapService.missingEntitiesForCourse(course.getId());
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
            List<MissingContentDTO> gaps = gapService.contentGapsForCourse(course.getId());

            // The video unit has no transcript; the PDF unit has slides, so it must not appear.
            assertThat(gaps).extracting(MissingContentDTO::lectureUnitId, MissingContentDTO::kind, MissingContentDTO::title)
                    .containsExactly(tuple(videoUnit.getId(), "transcript", videoUnit.getName()));
        });
    }

    @Test
    void reportsNoGapsOnceTheMissingContentIsIndexed() throws Exception {
        insertContent(LECTURE_TRANSCRIPTIONS_COLLECTION, course.getId(), videoUnit.getId());

        await().atMost(TIMEOUT).untilAsserted(() -> assertThat(gapService.contentGapsForCourse(course.getId())).isEmpty());
    }

    /** The metadata types the browser enumerates; the content and summary types are not entity types. */
    private static boolean isMetadataType(String type) {
        return IngestionCoverageWeaviateReadService.METADATA_TYPES.contains(type);
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

    private AttachmentVideoUnit seedUnitWithVideoSource(Lecture lecture, String name, String videoSource) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName(name);
        unit.setDescription("Test");
        unit.setLecture(lecture);
        unit.setVideoSource(videoSource);
        return attachmentVideoUnitRepository.save(unit);
    }

    private AttachmentVideoUnit seedUnitWithAttachmentLink(Lecture lecture, String name, String link) {
        AttachmentVideoUnit unit = new AttachmentVideoUnit();
        unit.setName(name);
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
