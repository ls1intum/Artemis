package de.tum.cit.aet.artemis.globalsearch;

import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertExerciseExistsInWeaviate;
import static de.tum.cit.aet.artemis.globalsearch.util.WeaviateTestUtil.assertLectureExistsInWeaviate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.globalsearch.config.schema.entityschemas.SearchableEntitySchema;
import de.tum.cit.aet.artemis.globalsearch.dto.GlobalSearchResultDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.ExerciseSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.dto.searchableentity.LectureSearchableEntityDTO;
import de.tum.cit.aet.artemis.globalsearch.service.SearchableEntityWeaviateService;
import de.tum.cit.aet.artemis.globalsearch.service.WeaviateService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import io.weaviate.client6.v1.api.collections.query.Filter;

/**
 * Integration tests for {@link de.tum.cit.aet.artemis.globalsearch.web.ExerciseWeaviateResource}
 * verifying role-based access control for exercise search endpoints.
 * <p>
 * Tests verify:
 * - Students cannot see exam exercises before the exam has started
 * - Students can see exam exercises once the exam has started (ongoing or ended)
 * - Students cannot see unreleased exercises
 * - Tutors cannot see exam exercises before the exam has ended
 * - Tutors can see exam exercises after the exam has ended
 * - Editors and instructors can see all exercises at any time
 * - The {@code isAtLeastTutor} metadata flag is set for staff (TA/editor/instructor) on ended exam exercises
 */
@EnabledIf("isWeaviateEnabled")
class ExerciseWeaviateResourceIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "exweaviateres";

    @Autowired
    private SearchableEntityWeaviateService searchableEntityWeaviateService;

    @Autowired
    private ChannelService channelService;

    @Autowired
    private WeaviateService weaviateService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private LectureTestRepository lectureTestRepository;

    @Autowired
    private ExamRepository examRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    private Course course;

    private ProgrammingExercise releasedExercise;

    private ProgrammingExercise unreleasedExercise;

    private TextExercise notStartedExamExercise;

    private TextExercise ongoingExamExercise;

    private TextExercise endedExamExercise;

    private Lecture lecture;

    static boolean isWeaviateEnabled() {
        return weaviateContainer != null && weaviateContainer.isRunning();
    }

    @BeforeEach
    void setUp() {
        // Clean up stale entries from previous test runs to prevent duplicates accumulating
        // in the shared Weaviate collection (which persists across @BeforeEach invocations).
        var collection = weaviateService.getCollection(SearchableEntitySchema.COLLECTION_NAME);
        collection.data.deleteMany(Filter.property(SearchableEntitySchema.Properties.TITLE).like("ExWvtResTest*"));

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        // Create course with a released programming exercise
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        releasedExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        releasedExercise.setTitle("ExWvtResTest Released Exercise");
        releasedExercise.setReleaseDate(ZonedDateTime.now().minusDays(1));
        exerciseRepository.save(releasedExercise);

        // Create an unreleased exercise in the same course (release date in the future)
        unreleasedExercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course, false);
        unreleasedExercise.setTitle("ExWvtResTest Unreleased Exercise");
        unreleasedExercise.setReleaseDate(ZonedDateTime.now().plusDays(7));
        exerciseRepository.save(unreleasedExercise);

        // Create an exam exercise where the exam has NOT started yet (start date in the future)
        Exam notStartedExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(3), false);
        notStartedExam = examRepository.save(notStartedExam);
        var notStartedExerciseGroup = ExamFactory.generateExerciseGroup(true, notStartedExam);
        notStartedExerciseGroup = exerciseGroupRepository.save(notStartedExerciseGroup);
        notStartedExamExercise = TextExerciseFactory.generateTextExerciseForExam(notStartedExerciseGroup);
        notStartedExamExercise.setTitle("ExWvtResTest NotStarted Exam Exercise");
        notStartedExamExercise = exerciseRepository.save(notStartedExamExercise);

        // Create an exam exercise where the exam is ongoing (started but not ended)
        Exam ongoingExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusHours(2), ZonedDateTime.now().minusHours(1), ZonedDateTime.now().plusHours(1), false);
        ongoingExam = examRepository.save(ongoingExam);
        var ongoingExerciseGroup = ExamFactory.generateExerciseGroup(true, ongoingExam);
        ongoingExerciseGroup = exerciseGroupRepository.save(ongoingExerciseGroup);
        ongoingExamExercise = TextExerciseFactory.generateTextExerciseForExam(ongoingExerciseGroup);
        ongoingExamExercise.setTitle("ExWvtResTest Ongoing Exam Exercise");
        ongoingExamExercise = exerciseRepository.save(ongoingExamExercise);

        // Create an exam exercise where the exam has already ended
        Exam endedExam = ExamFactory.generateExam(course, ZonedDateTime.now().minusDays(3), ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1), false);
        endedExam = examRepository.save(endedExam);
        var endedExerciseGroup = ExamFactory.generateExerciseGroup(true, endedExam);
        endedExerciseGroup = exerciseGroupRepository.save(endedExerciseGroup);
        endedExamExercise = TextExerciseFactory.generateTextExerciseForExam(endedExerciseGroup);
        endedExamExercise.setTitle("ExWvtResTest Ended Exam Exercise");
        endedExamExercise = exerciseRepository.save(endedExamExercise);

        // Create a lecture in the same course
        lecture = lectureUtilService.createLecture(course);
        lecture.setTitle("ExWvtResTest Test Lecture");
        lecture = lectureTestRepository.save(lecture);

        // Index all exercises and the lecture in Weaviate
        searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(releasedExercise));
        searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(unreleasedExercise));
        searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(notStartedExamExercise));
        searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(ongoingExamExercise));
        searchableEntityWeaviateService.upsertExerciseAsync(ExerciseSearchableEntityDTO.fromExercise(endedExamExercise));
        searchableEntityWeaviateService.upsertLectureAsync(LectureSearchableEntityDTO.fromLecture(lecture));

        // Wait for all entities to be indexed AND BM25-searchable.
        // Existence checks (fetchObjects with filter) verify the data is stored,
        // but the BM25 inverted index may lag behind — we must also verify that
        // a keyword search returns the expected items before running test assertions.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertExerciseExistsInWeaviate(weaviateService, releasedExercise);
            assertExerciseExistsInWeaviate(weaviateService, unreleasedExercise);
            assertExerciseExistsInWeaviate(weaviateService, notStartedExamExercise);
            assertExerciseExistsInWeaviate(weaviateService, ongoingExamExercise);
            assertExerciseExistsInWeaviate(weaviateService, endedExamExercise);
            assertLectureExistsInWeaviate(weaviateService, lecture);

            // Verify BM25 inverted index is ready by checking that a keyword search finds all 6 items
            var bm25Results = collection.query.bm25("ExWvtResTest", b -> b.limit(10).queryProperties(SearchableEntitySchema.Properties.TITLE));
            assertThat(bm25Results.objects()).hasSizeGreaterThanOrEqualTo(6);
        });
    }

    private List<String> getResultTitles(List<GlobalSearchResultDTO> results) {
        return results.stream().map(GlobalSearchResultDTO::title).toList();
    }

    @Nested
    class GlobalSearchEndpointTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeNotStartedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("ExWvtResTest NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCanSeeStartedAndEndedExamExercisesButNotNotStartedOnes() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Ongoing Exam Exercise", "ExWvtResTest Ended Exam Exercise");
            assertThat(titles).doesNotContain("ExWvtResTest NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeUnreleasedExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise");
            assertThat(titles).doesNotContain("ExWvtResTest Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCannotSeeNotEndedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("ExWvtResTest NotStarted Exam Exercise", "ExWvtResTest Ongoing Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCanSeeEndedExamExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorCanSeeUnreleasedExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testEditorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest Unreleased Exercise", "ExWvtResTest NotStarted Exam Exercise",
                    "ExWvtResTest Ongoing Exam Exercise", "ExWvtResTest Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testInstructorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest Unreleased Exercise", "ExWvtResTest NotStarted Exam Exercise",
                    "ExWvtResTest Ongoing Exam Exercise", "ExWvtResTest Ended Exam Exercise");
        }
    }

    @Nested
    class GlobalSearchWithoutCourseIdTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testGlobalSearchStudentFiltersCorrectly() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest", HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest Ongoing Exam Exercise");
            assertThat(titles).doesNotContain("ExWvtResTest Unreleased Exercise", "ExWvtResTest NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testGlobalSearchInstructorSeesAll() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest", HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest NotStarted Exam Exercise", "ExWvtResTest Ongoing Exam Exercise",
                    "ExWvtResTest Ended Exam Exercise");

            var unreleasedResults = request.getList("/api/search?q=ExWvtResTest%20Unreleased&types=exercise", HttpStatus.OK, GlobalSearchResultDTO.class);
            assertThat(getResultTitles(unreleasedResults)).contains("ExWvtResTest Unreleased Exercise");
        }
    }

    @Nested
    class ExerciseSearchEndpointTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCannotSeeNotStartedExamOrUnreleasedExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&types=exercise&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).doesNotContain("ExWvtResTest NotStarted Exam Exercise", "ExWvtResTest Unreleased Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentCanSeeStartedAndEndedExamExercisesButNotNotStartedOnes() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&types=exercise&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Ongoing Exam Exercise", "ExWvtResTest Ended Exam Exercise");
            assertThat(titles).doesNotContain("ExWvtResTest NotStarted Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
        void testEditorCanSeeAllExercises() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&types=exercise&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest Unreleased Exercise", "ExWvtResTest NotStarted Exam Exercise",
                    "ExWvtResTest Ongoing Exam Exercise", "ExWvtResTest Ended Exam Exercise");
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testEmptyQueryReturnsOk() throws Exception {
            // The global search endpoint accepts empty queries to browse recent items
            request.getList("/api/search?q=&types=exercise&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
        }
    }

    @Nested
    class AdminTypeFilterTests {

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testAdminGlobalSearchWithTypeFilterReturnsOnlyRequestedType() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&types=exercise", HttpStatus.OK, GlobalSearchResultDTO.class);
            var types = results.stream().map(GlobalSearchResultDTO::type).toList();

            assertThat(types).isNotEmpty();
            assertThat(types).allMatch("exercise"::equals);
            assertThat(getResultTitles(results)).doesNotContain("ExWvtResTest Test Lecture");
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testAdminGlobalSearchWithLectureTypeFilterReturnsOnlyLectures() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest&types=lecture", HttpStatus.OK, GlobalSearchResultDTO.class);
            var types = results.stream().map(GlobalSearchResultDTO::type).toList();

            assertThat(types).isNotEmpty();
            assertThat(types).allMatch("lecture"::equals);
            assertThat(getResultTitles(results)).contains("ExWvtResTest Test Lecture");
            assertThat(getResultTitles(results)).doesNotContain("ExWvtResTest Released Exercise");
        }

        @Test
        @WithMockUser(username = "admin", roles = "ADMIN")
        void testAdminGlobalSearchWithoutTypeFilterReturnsAllTypes() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest", HttpStatus.OK, GlobalSearchResultDTO.class);
            var titles = getResultTitles(results);

            assertThat(titles).contains("ExWvtResTest Released Exercise", "ExWvtResTest Test Lecture");
        }
    }

    @Nested
    class ExamExerciseMetadataFlagTests {

        /**
         * Staff users (TA, editor, instructor) should see {@code isAtLeastTutor: true} in the metadata
         * of ended exam exercises so the client can route them to the course-management view.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testInstructorReceivesIsAtLeastTutorFlagOnEndedExamExercise() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest%20Ended&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var endedExamResult = results.stream().filter(r -> "ExWvtResTest Ended Exam Exercise".equals(r.title())).findFirst();

            assertThat(endedExamResult).isPresent();
            assertThat(endedExamResult.get().metadata()).containsEntry("isAtLeastTutor", true);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
        void testTutorReceivesIsAtLeastTutorFlagOnEndedExamExercise() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest%20Ended&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var endedExamResult = results.stream().filter(r -> "ExWvtResTest Ended Exam Exercise".equals(r.title())).findFirst();

            assertThat(endedExamResult).isPresent();
            assertThat(endedExamResult.get().metadata()).containsEntry("isAtLeastTutor", true);
        }

        /**
         * Students should NOT receive the {@code isAtLeastTutor} flag; the client falls back to
         * the student exercise route for their ended-exam exercise results.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testStudentDoesNotReceiveIsAtLeastTutorFlagOnEndedExamExercise() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest%20Ended&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var endedExamResult = results.stream().filter(r -> "ExWvtResTest Ended Exam Exercise".equals(r.title())).findFirst();

            assertThat(endedExamResult).isPresent();
            assertThat(endedExamResult.get().metadata()).doesNotContainKey("isAtLeastTutor");
        }

        /**
         * The {@code isAtLeastTutor} flag must NOT appear on regular (non-exam) exercises
         * regardless of role, since those never need the course-management routing branch.
         */
        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testIsAtLeastTutorFlagAbsentOnRegularExercise() throws Exception {
            var results = request.getList("/api/search?q=ExWvtResTest%20Released&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
            var regularResult = results.stream().filter(r -> "ExWvtResTest Released Exercise".equals(r.title())).findFirst();

            assertThat(regularResult).isPresent();
            assertThat(regularResult.get().metadata()).doesNotContainKey("isAtLeastTutor");
        }
    }

    @Nested
    class ArchivedChannelSearchTests {

        @Test
        @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
        void testArchivedChannelIsExcludedFromSearch() throws Exception {
            User instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

            Channel channel = new Channel();
            channel.setName("weaviate-archive-search");
            channel.setIsPublic(true);
            channel.setIsCourseWide(true);
            channel.setIsAnnouncementChannel(false);

            Channel createdChannel = channelService.createChannel(course, channel, Optional.of(instructor));

            // Wait for channel to be indexed — capture security context for awaitility thread
            var securityContext = SecurityContextHolder.getContext();
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                SecurityContextHolder.setContext(securityContext);
                var results = request.getList("/api/search?q=weaviate-archive-search&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
                var titles = getResultTitles(results);
                assertThat(titles).contains("weaviate-archive-search");
            });

            // Archive the channel
            channelService.archiveChannel(createdChannel.getId());

            // Verify the archived channel no longer appears in search
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                SecurityContextHolder.setContext(securityContext);
                var results = request.getList("/api/search?q=weaviate-archive-search&courseId=" + course.getId(), HttpStatus.OK, GlobalSearchResultDTO.class);
                var titles = getResultTitles(results);
                assertThat(titles).doesNotContain("weaviate-archive-search");
            });
        }
    }

}
