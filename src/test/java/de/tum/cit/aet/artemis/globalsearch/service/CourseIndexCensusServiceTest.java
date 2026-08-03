package de.tum.cit.aet.artemis.globalsearch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

import de.tum.cit.aet.artemis.communication.domain.Faq;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.FaqRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.CourseCensus;
import de.tum.cit.aet.artemis.globalsearch.service.CourseIndexCensusService.TypeCensus;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService.IndexedKey;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@ExtendWith(MockitoExtension.class)
class CourseIndexCensusServiceTest {

    private static final long COURSE_ID = 7L;

    @Mock
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private LectureRepository lectureRepository;

    @Mock
    private LectureUnitRepository lectureUnitRepository;

    @Mock
    private ExamRepository examRepository;

    @Mock
    private FaqRepository faqRepository;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private ChannelRepository channelRepository;

    private CourseIndexCensusService censusService() {
        // Iris disabled, so the content-collection columns are not counted; the metadata census under test is unaffected.
        MockEnvironment environment = new MockEnvironment().withProperty("artemis.iris.enabled", "false");
        return new CourseIndexCensusService(searchableEntityWeaviateService, courseRepository, lectureRepository, lectureUnitRepository, examRepository, faqRepository,
                exerciseRepository, channelRepository, environment);
    }

    private static Faq faq(long id) {
        Faq faq = new Faq();
        faq.setId(id);
        return faq;
    }

    private static Exam exam(long id) {
        Exam exam = new Exam();
        exam.setId(id);
        return exam;
    }

    private static Channel channel(long id, boolean archived, boolean courseWide, boolean isPublic) {
        Channel channel = new Channel();
        channel.setId(id);
        channel.setIsArchived(archived);
        channel.setIsCourseWide(courseWide);
        channel.setIsPublic(isPublic);
        return channel;
    }

    private static TypeCensus typeOf(CourseCensus result, String type) {
        return result.types().stream().filter(entry -> entry.type().equals(type)).findFirst().orElseThrow();
    }

    @Test
    void computesMissingAndOrphanedFromTheSetDifference() {
        // faqs: DB has 1,2,3; Weaviate has 2,3,4 -> missing {1}, orphaned {4}
        when(faqRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(faq(1), faq(2), faq(3)));
        when(lectureRepository.findLectureIdsByCourseId(COURSE_ID)).thenReturn(Set.of(10L, 11L));
        when(examRepository.findByCourseId(COURSE_ID)).thenReturn(List.of(exam(20)));
        when(searchableEntityWeaviateService.listIndexedKeysForCourse(COURSE_ID))
                .thenReturn(List.of(new IndexedKey(SearchableEntitySchema.TypeValues.FAQ, 2), new IndexedKey(SearchableEntitySchema.TypeValues.FAQ, 3),
                        new IndexedKey(SearchableEntitySchema.TypeValues.FAQ, 4), new IndexedKey(SearchableEntitySchema.TypeValues.LECTURE, 10),
                        new IndexedKey(SearchableEntitySchema.TypeValues.LECTURE, 11), new IndexedKey(SearchableEntitySchema.TypeValues.COURSE, COURSE_ID)));

        CourseCensus result = censusService().censusCourse(COURSE_ID);

        TypeCensus faqCensus = typeOf(result, SearchableEntitySchema.TypeValues.FAQ);
        assertThat(faqCensus.expected()).isEqualTo(3);
        assertThat(faqCensus.present()).isEqualTo(3);
        assertThat(faqCensus.missing()).isEqualTo(1);
        assertThat(faqCensus.orphaned()).isEqualTo(1);

        TypeCensus lectureCensus = typeOf(result, SearchableEntitySchema.TypeValues.LECTURE);
        assertThat(lectureCensus.missing()).isEqualTo(0);
        assertThat(lectureCensus.orphaned()).isEqualTo(0);

        // exam 20 expected but not indexed -> one missing, zero present
        TypeCensus examCensus = typeOf(result, SearchableEntitySchema.TypeValues.EXAM);
        assertThat(examCensus.expected()).isEqualTo(1);
        assertThat(examCensus.present()).isEqualTo(0);
        assertThat(examCensus.missing()).isEqualTo(1);
    }

    @Test
    void computesExerciseUnitAndChannelExpectedFromTheirIndexingRules() {
        when(exerciseRepository.findAllExerciseIdsByCourseIdIncludingExam(COURSE_ID)).thenReturn(Set.of(1L, 2L, 3L));
        when(lectureUnitRepository.findIndexableUnitIdsByCourseId(COURSE_ID)).thenReturn(Set.of(50L, 51L));
        // channel 60 indexable (public), 61 indexable (course-wide), 62 excluded (archived), 63 excluded (private, not course-wide)
        when(channelRepository.findChannelsByCourseId(COURSE_ID))
                .thenReturn(List.of(channel(60, false, false, true), channel(61, false, true, false), channel(62, true, true, true), channel(63, false, false, false)));
        when(searchableEntityWeaviateService.listIndexedKeysForCourse(COURSE_ID))
                .thenReturn(List.of(new IndexedKey(SearchableEntitySchema.TypeValues.EXERCISE, 1), new IndexedKey(SearchableEntitySchema.TypeValues.EXERCISE, 2),
                        new IndexedKey(SearchableEntitySchema.TypeValues.LECTURE_UNIT, 50), new IndexedKey(SearchableEntitySchema.TypeValues.CHANNEL, 60)));

        CourseCensus result = censusService().censusCourse(COURSE_ID);

        // exercises: expected {1,2,3}, present {1,2} -> missing 1
        TypeCensus exerciseCensus = typeOf(result, SearchableEntitySchema.TypeValues.EXERCISE);
        assertThat(exerciseCensus.expected()).isEqualTo(3);
        assertThat(exerciseCensus.missing()).isEqualTo(1);
        assertThat(exerciseCensus.orphaned()).isEqualTo(0);

        // lecture units: expected {50,51}, present {50} -> missing 1
        TypeCensus unitCensus = typeOf(result, SearchableEntitySchema.TypeValues.LECTURE_UNIT);
        assertThat(unitCensus.expected()).isEqualTo(2);
        assertThat(unitCensus.missing()).isEqualTo(1);

        // channels: only 60 and 61 are indexable, present {60} -> expected 2, missing 1
        TypeCensus channelCensus = typeOf(result, SearchableEntitySchema.TypeValues.CHANNEL);
        assertThat(channelCensus.expected()).isEqualTo(2);
        assertThat(channelCensus.missing()).isEqualTo(1);
    }

    @Test
    void contentCensusCountsUnitsWithFileThatHaveIngestedContent() {
        // 3 units have a PDF (1,2,3); only 1 and 2 have slides ingested -> present 2, expected 3, missing 1
        CourseIndexCensusService.ContentCensus pdf = CourseIndexCensusService.ContentCensus.of("pdf", Set.of(1L, 2L, 3L), Set.of(1L, 2L, 9L));
        assertThat(pdf.expected()).isEqualTo(3);
        assertThat(pdf.present()).isEqualTo(2);
        assertThat(pdf.missing()).isEqualTo(1);

        // no units have a video -> everything zero, so the frontend renders it as empty
        CourseIndexCensusService.ContentCensus video = CourseIndexCensusService.ContentCensus.of("video", Set.of(), Set.of());
        assertThat(video.expected()).isEqualTo(0);
        assertThat(video.present()).isEqualTo(0);
        assertThat(video.missing()).isEqualTo(0);
    }

    @Test
    void reportsPresentOnlyForTypesWithoutADatabaseSource() {
        // posts have no expected-loader yet, so they stay present-only
        lenient().when(searchableEntityWeaviateService.listIndexedKeysForCourse(COURSE_ID))
                .thenReturn(List.of(new IndexedKey(SearchableEntitySchema.TypeValues.POST, 99), new IndexedKey(SearchableEntitySchema.TypeValues.POST, 100)));

        CourseCensus result = censusService().censusCourse(COURSE_ID);

        TypeCensus postCensus = typeOf(result, SearchableEntitySchema.TypeValues.POST);
        assertThat(postCensus.expected()).isNull();
        assertThat(postCensus.present()).isEqualTo(2);
        assertThat(postCensus.missing()).isNull();
        assertThat(postCensus.orphaned()).isNull();
    }
}
