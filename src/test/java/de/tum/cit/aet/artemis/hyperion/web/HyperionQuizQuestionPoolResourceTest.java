package de.tum.cit.aet.artemis.hyperion.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.util.LinkedMultiValueMap;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.core.domain.CourseRole;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionDifficulty;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItem;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionPoolItemState;
import de.tum.cit.aet.artemis.hyperion.domain.QuizQuestionReviewState;
import de.tum.cit.aet.artemis.hyperion.dto.HyperionCourseSettingsDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionFilterVerdictDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolDeletionDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolPageDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolProgressDTO;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolReviewDTO;
import de.tum.cit.aet.artemis.hyperion.repository.QuizQuestionPoolItemRepository;
import de.tum.cit.aet.artemis.hyperion.service.HyperionQuizQuestionPoolService;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

class HyperionQuizQuestionPoolResourceTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "hyperionquizpool";

    /**
     * The first competency has a text unit and a slide-deck unit, the second a text unit: three lecture units, each with the default
     * twelve single-choice questions split 20/50/30 over the difficulties, give nine cells holding two, six and four items.
     */
    private static final int EXPECTED_ITEMS = 36;

    private static final int EXPECTED_CELLS = 9;

    private static final int EXPECTED_ITEMS_OF_FIRST_COMPETENCY = 24;

    /** A phrase on the first slide of the test deck, which must reach the generation prompt through the PDF text extraction. */
    private static final String SLIDE_DECK_PHRASE = "Control Structures";

    private static final Pattern NUMBER_OF_QUESTIONS = Pattern.compile("Number of Questions: (\\d+)");

    private static final Pattern QUESTION_TYPE = Pattern.compile("Question Type: ([a-z-]+)");

    private static final Pattern CORRECT_OPTIONS = Pattern.compile("question (\\d+): exactly (\\d+)");

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private HyperionQuizQuestionPoolService poolService;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    private QuizQuestionPoolItemRepository itemRepository;

    private long courseId;

    private Competency firstCompetency;

    private LectureUnit firstTextUnit;

    private final AtomicInteger questionCounter = new AtomicInteger();

    private final AtomicBoolean generationFails = new AtomicBoolean(false);

    private final AtomicInteger generationDelayMillis = new AtomicInteger();

    private final AtomicBoolean slideDeckReachedThePrompt = new AtomicBoolean(false);

    @BeforeEach
    void setupTestData() {
        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);

        Course course = CourseFactory.generateMinimalCourse();
        course.setTitle("Hyperion Pool Test Course");
        course = courseRepository.save(course);
        courseId = course.getId();
        firstCompetency = competencyUtilService.createCompetency(course, "1");
        Competency secondCompetency = competencyUtilService.createCompetency(course, "2");
        Lecture lecture = lectureUtilService.createLecture(course);
        firstTextUnit = lectureUtilService.createTextUnit(lecture);
        AttachmentVideoUnit slideDeck = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 1, true);
        slideDeck.setName("Control structures slides");
        slideDeck = attachmentVideoUnitRepository.save(slideDeck);
        LectureUnit secondTextUnit = lectureUtilService.createTextUnit(lecture);
        competencyUtilService.linkLectureUnitToCompetency(firstCompetency, firstTextUnit);
        competencyUtilService.linkLectureUnitToCompetency(firstCompetency, slideDeck);
        competencyUtilService.linkLectureUnitToCompetency(secondCompetency, secondTextUnit);

        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), course, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), course, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), course, CourseRole.EDITOR);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, CourseRole.INSTRUCTOR);

        questionCounter.set(0);
        generationFails.set(false);
        generationDelayMillis.set(0);
        slideDeckReachedThePrompt.set(false);
        mockModel();
    }

    @AfterEach
    void resetMocks() {
        reset(azureOpenAiChatModel);
    }

    /**
     * Answers generation prompts with the requested number of questions, the last of every batch titled so the filter rejects it, and
     * filter prompts with a verdict that rejects exactly those questions. Generation throws while {@link #generationFails} is set.
     */
    private void mockModel() {
        doAnswer(invocation -> {
            Prompt prompt = invocation.getArgument(0);
            String system = text(prompt, SystemMessage.class);
            String user = text(prompt, UserMessage.class);
            if (system.startsWith("You are a strict reviewer")) {
                return response(verdict(user.contains("Title: Reject") ? 1.0 : user.contains("Title: Flag") ? 0.2 : 0.0));
            }
            if (generationFails.get()) {
                throw new RuntimeException("model unavailable");
            }
            if (generationDelayMillis.get() > 0) {
                Thread.sleep(generationDelayMillis.get());
            }
            if (user.contains(SLIDE_DECK_PHRASE)) {
                slideDeckReachedThePrompt.set(true);
            }
            Matcher count = NUMBER_OF_QUESTIONS.matcher(user);
            Matcher type = QUESTION_TYPE.matcher(user);
            assertThat(count.find()).isTrue();
            assertThat(type.find()).isTrue();
            int number = Integer.parseInt(count.group(1));
            Map<Integer, Integer> correctCounts = new HashMap<>();
            Matcher correct = CORRECT_OPTIONS.matcher(user);
            while (correct.find()) {
                correctCounts.put(Integer.parseInt(correct.group(1)) - 1, Integer.parseInt(correct.group(2)));
            }
            StringBuilder questions = new StringBuilder();
            for (int index = 0; index < number; index++) {
                if (index > 0) {
                    questions.append(",");
                }
                int id = questionCounter.incrementAndGet();
                String prefix = index == number - 1 ? "Reject " : index == 0 ? "Flag " : "";
                questions.append(question(type.group(1), prefix + "Q" + id, correctCounts.getOrDefault(index, 1)));
            }
            return response("{ \"questions\": [" + questions + "] }");
        }).when(azureOpenAiChatModel).call(any(Prompt.class));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldBuildThePoolForInstructor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true)).andExpect(jsonPath("$.enqueued").value(EXPECTED_ITEMS));
        awaitBuild();

        QuizQuestionPoolProgressDTO progress = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/progress", HttpStatus.OK, QuizQuestionPoolProgressDTO.class);
        assertThat(progress.total()).isEqualTo(EXPECTED_ITEMS);
        assertThat(progress.judged()).isEqualTo(EXPECTED_ITEMS);
        assertThat(progress.accepted()).isEqualTo(EXPECTED_ITEMS - EXPECTED_CELLS);
        assertThat(progress.rejected()).isEqualTo(EXPECTED_CELLS);
        assertThat(progress.toGenerate()).isZero();
        assertThat(progress.toJudge()).isZero();
        assertThat(progress.failed()).isZero();
        assertThat(questionCounter.get()).as("the rejected question of every cell is generated three times before it is kept").isEqualTo(EXPECTED_ITEMS + 2 * EXPECTED_CELLS);

        QuizQuestionPoolPageDTO firstPage = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.OK, QuizQuestionPoolPageDTO.class);
        assertThat(firstPage.totalCount()).isEqualTo(EXPECTED_ITEMS);
        assertThat(firstPage.competencies()).hasSize(2);
        List<QuizQuestionPoolItemDTO> items = firstPage.items();
        assertThat(items).hasSize(EXPECTED_ITEMS);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.state()).isEqualTo(QuizQuestionPoolItemState.FILTERED);
            assertThat(item.question()).isNotNull();
            assertThat(item.question().options()).hasSize(4);
            assertThat(item.question().type()).isEqualTo(item.questionType());
            assertThat(item.decision()).isNotNull();
            assertThat(item.decision().verdicts()).hasSize(5);
            assertThat(item.competencyTitle()).isNotBlank();
            assertThat(item.lectureUnitId()).isNotNull();
            assertThat(item.lectureUnitName()).isNotBlank();
        });
        assertThat(items).filteredOn(item -> item.decision().accepted()).hasSize(EXPECTED_ITEMS - EXPECTED_CELLS);
        assertThat(items.stream().map(QuizQuestionPoolItemDTO::lectureUnitId).distinct()).hasSize(3);
        assertThat(slideDeckReachedThePrompt).isTrue();

        List<QuizQuestionPoolItemDTO> ofFirstCompetency = request
                .get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions?competencyId=" + firstCompetency.getId(), HttpStatus.OK, QuizQuestionPoolPageDTO.class)
                .items();
        assertThat(ofFirstCompetency).hasSize(EXPECTED_ITEMS_OF_FIRST_COMPETENCY);
        assertThat(ofFirstCompetency).allMatch(item -> item.competencyId().equals(firstCompetency.getId()));
        assertThat(ofFirstCompetency.stream().map(QuizQuestionPoolItemDTO::lectureUnitId).distinct()).hasSize(2);

        QuizQuestionPoolPageDTO secondPage = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions?page=1&size=10", HttpStatus.OK,
                QuizQuestionPoolPageDTO.class);
        assertThat(secondPage.totalCount()).isEqualTo(EXPECTED_ITEMS);
        assertThat(secondPage.items()).hasSize(10);
        assertThat(secondPage.items()).containsExactlyElementsOf(items.subList(10, 20));

        QuizQuestionPoolPageDTO flagged = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions?flagged=true", HttpStatus.OK,
                QuizQuestionPoolPageDTO.class);
        assertThat(flagged.totalCount()).as("the first question of every cell has a factual-error severity of 0.2, which flags but passes the gate").isEqualTo(EXPECTED_CELLS);
        assertThat(flagged.items()).allSatisfy(item -> {
            assertThat(item.flagged()).isTrue();
            assertThat(item.decision().accepted()).isTrue();
            assertThat(item.decision().verdicts()).filteredOn(QuizQuestionFilterVerdictDTO::flagged).hasSize(1);
        });
        assertThat(items).filteredOn(item -> Boolean.TRUE.equals(item.flagged())).hasSize(EXPECTED_CELLS);

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true)).andExpect(jsonPath("$.enqueued").value(0));
        awaitBuild();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReviveItemsAfterTransientGenerationFailures() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        generationFails.set(true);

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isAccepted());
        awaitBuild();

        QuizQuestionPoolProgressDTO failed = poolService.getProgress(courseId);
        assertThat(failed.failed()).isEqualTo(EXPECTED_ITEMS);
        assertThat(failed.judged()).isZero();

        generationFails.set(false);
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.enqueued").value(0));
        awaitBuild();

        QuizQuestionPoolProgressDTO recovered = poolService.getProgress(courseId);
        assertThat(recovered.failed()).isZero();
        assertThat(recovered.judged()).isEqualTo(EXPECTED_ITEMS);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReturnBadRequestWhenCourseHasNoCompetencies() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Course empty = CourseFactory.generateMinimalCourse();
        empty.setTitle("Hyperion Pool Empty Course");
        empty = courseRepository.save(empty);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), empty, CourseRole.INSTRUCTOR);

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", empty.getId())).andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldEnqueueNothingWhenNoLectureUnitIsLinked() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Course unlinked = CourseFactory.generateMinimalCourse();
        unlinked.setTitle("Hyperion Pool Unlinked Course");
        unlinked = courseRepository.save(unlinked);
        competencyUtilService.createCompetency(unlinked, "3");
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), unlinked, CourseRole.INSTRUCTOR);

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", unlinked.getId())).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.started").value(true)).andExpect(jsonPath("$.enqueued").value(0));
        long unlinkedId = unlinked.getId();
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> !poolService.getProgress(unlinkedId).running());

        assertThat(poolService.getProgress(unlinkedId).total()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutorOnBuild() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnForbiddenForEditorOnBuild() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnProgressAndQuestionsForEditor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        QuizQuestionPoolProgressDTO progress = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/progress", HttpStatus.OK, QuizQuestionPoolProgressDTO.class);
        assertThat(progress.total()).isZero();
        assertThat(progress.running()).isFalse();
        assertThat(request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.OK, QuizQuestionPoolPageDTO.class).items()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForStudentOnProgress() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/progress", HttpStatus.FORBIDDEN, QuizQuestionPoolProgressDTO.class);
        request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.FORBIDDEN, QuizQuestionPoolPageDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldClaimAnItemOnlyWithItsCurrentVersion() {
        QuizQuestionPoolItem item = savePendingItem(courseId);
        ZonedDateTime now = ZonedDateTime.now();

        assertThat(itemRepository.claim(item.getId(), QuizQuestionPoolItemState.PENDING, QuizQuestionPoolItemState.GENERATING, now, item.getVersion() + 1)).isZero();
        assertThat(itemRepository.claim(item.getId(), QuizQuestionPoolItemState.PENDING, QuizQuestionPoolItemState.GENERATING, now, item.getVersion())).isEqualTo(1);

        QuizQuestionPoolItem claimed = itemRepository.findByIdElseThrow(item.getId());
        assertThat(claimed.getState()).isEqualTo(QuizQuestionPoolItemState.GENERATING);
        assertThat(claimed.getVersion()).isEqualTo(item.getVersion() + 1);
        assertThat(claimed.getClaimedAt()).isNotNull();
        assertThat(itemRepository.claim(item.getId(), QuizQuestionPoolItemState.PENDING, QuizQuestionPoolItemState.GENERATING, now, item.getVersion())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldReadAndUpdateSettingsForInstructor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        HyperionCourseSettingsDTO defaults = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", HttpStatus.OK, HyperionCourseSettingsDTO.class);
        assertThat(defaults).isEqualTo(new HyperionCourseSettingsDTO(false, 12, false, 12, 1, 4, 20, 50, 30));

        HyperionCourseSettingsDTO updated = request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings",
                new HyperionCourseSettingsDTO(true, 30, true, 10, 1, 4, 25, 50, 25), HyperionCourseSettingsDTO.class, HttpStatus.OK);
        assertThat(updated).isEqualTo(new HyperionCourseSettingsDTO(true, 30, true, 10, 1, 4, 25, 50, 25));

        HyperionCourseSettingsDTO reloaded = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", HttpStatus.OK, HyperionCourseSettingsDTO.class);
        assertThat(reloaded).isEqualTo(new HyperionCourseSettingsDTO(true, 30, true, 10, 1, 4, 25, 50, 25));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRejectSettingsOutsideTheAllowedRange() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.performMvcRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/hyperion/courses/{courseId}/quiz-question-pool/settings", courseId)
                .contentType(MediaType.APPLICATION_JSON).content(
                        "{ \"poolGenerationEnabled\": true, \"singleChoiceQuestionsPerLectureUnit\": 0, \"multipleChoiceEnabled\": false, \"multipleChoiceQuestionsPerLectureUnit\": 12, "
                                + "\"multipleChoiceMinCorrectOptions\": 1, \"multipleChoiceMaxCorrectOptions\": 4, "
                                + "\"easyPercent\": 20, \"mediumPercent\": 50, \"hardPercent\": 30 }"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRejectACorrectOptionRangeThatIsOutOfOrder() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.put("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", new HyperionCourseSettingsDTO(true, 12, true, 12, 3, 2, 20, 50, 30),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldCancelARunningBuildAndDropThePendingItems() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        generationDelayMillis.set(1500);

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build", courseId)).andExpect(status().isAccepted());
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build/cancel", courseId)).andExpect(status().isAccepted());
        awaitBuild();

        QuizQuestionPoolProgressDTO progress = poolService.getProgress(courseId);
        assertThat(progress.running()).isFalse();
        assertThat(progress.toGenerate()).isZero();
        assertThat(progress.total()).isLessThan(EXPECTED_ITEMS);
        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build/cancel", courseId)).andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutorOnCancel() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");

        request.performMvcRequest(post("/api/hyperion/courses/{courseId}/quiz-question-pool/build/cancel", courseId)).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRejectADifficultyMixThatDoesNotAddUpToOneHundredPercent() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.put("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", new HyperionCourseSettingsDTO(true, 12, false, 12, 1, 4, 30, 50, 30),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnForbiddenForEditorOnSettings() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");

        request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", HttpStatus.FORBIDDEN, HyperionCourseSettingsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRecordReviewDecisionsForEditor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);

        QuizQuestionPoolItemDTO approved = request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.OK);
        assertThat(approved.reviewState()).isEqualTo(QuizQuestionReviewState.APPROVED);
        assertThat(approved.competencyTitle()).isNotBlank();

        QuizQuestionPoolItemDTO excluded = request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.EXCLUDED), QuizQuestionPoolItemDTO.class, HttpStatus.OK);
        assertThat(excluded.reviewState()).isEqualTo(QuizQuestionReviewState.EXCLUDED);
        assertThat(itemRepository.findByIdElseThrow(item.getId()).getReviewState()).isEqualTo(QuizQuestionReviewState.EXCLUDED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldNotReviewAQuestionOfAnotherCourse() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);
        Course other = CourseFactory.generateMinimalCourse();
        other.setTitle("Hyperion Pool Other Course");
        other = courseRepository.save(other);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), other, CourseRole.EDITOR);

        request.putWithResponseBody("/api/hyperion/courses/" + other.getId() + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.NOT_FOUND);
        assertThat(itemRepository.findByIdElseThrow(item.getId()).getReviewState()).isEqualTo(QuizQuestionReviewState.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldRejectAReviewOfAQuestionThatIsNotJudgedYet() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        QuizQuestionPoolItem item = savePendingItem(courseId);

        request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.BAD_REQUEST);
        assertThat(itemRepository.findByIdElseThrow(item.getId()).getReviewState()).isEqualTo(QuizQuestionReviewState.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldDeleteASingleQuestionForInstructor() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);

        request.delete("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId(), HttpStatus.NO_CONTENT);

        assertThat(itemRepository.findById(item.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldNotDeleteAQuestionOfAnotherCourse() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);
        Course other = CourseFactory.generateMinimalCourse();
        other.setTitle("Hyperion Pool Other Course");
        other = courseRepository.save(other);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), other, CourseRole.INSTRUCTOR);

        request.delete("/api/hyperion/courses/" + other.getId() + "/quiz-question-pool/questions/" + item.getId(), HttpStatus.NOT_FOUND);

        assertThat(itemRepository.findById(item.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldDeleteTheQuestionsOfOneCompetencyAndThenTheWholePool() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        saveJudgedItem(courseId);
        savePendingItem(courseId, firstCompetency.getId(), 1);
        QuizQuestionPoolItem ofOtherCompetency = savePendingItem(courseId, firstCompetency.getId() + 1, 0);

        LinkedMultiValueMap<String, String> ofFirst = new LinkedMultiValueMap<>();
        ofFirst.add("competencyId", String.valueOf(firstCompetency.getId()));
        QuizQuestionPoolDeletionDTO deleted = request.delete("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", ofFirst, null,
                QuizQuestionPoolDeletionDTO.class, HttpStatus.OK);
        assertThat(deleted.deleted()).isEqualTo(2);
        assertThat(itemRepository.findById(ofOtherCompetency.getId())).isPresent();

        QuizQuestionPoolDeletionDTO rest = request.delete("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", new LinkedMultiValueMap<>(), null,
                QuizQuestionPoolDeletionDTO.class, HttpStatus.OK);
        assertThat(rest.deleted()).isEqualTo(1);
        assertThat(request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.OK, QuizQuestionPoolPageDTO.class).totalCount()).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = { "USER", "EDITOR" })
    void shouldReturnForbiddenForEditorOnDelete() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "editor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);

        request.delete("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId(), HttpStatus.FORBIDDEN);
        request.delete("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.FORBIDDEN);
        assertThat(itemRepository.findById(item.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutorOnReview() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        QuizQuestionPoolItem item = saveJudgedItem(courseId);

        request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.FORBIDDEN);
    }

    private QuizQuestionPoolItem saveJudgedItem(long ownerCourseId) {
        QuizQuestionPoolItem item = savePendingItem(ownerCourseId);
        item.setState(QuizQuestionPoolItemState.FILTERED);
        return itemRepository.save(item);
    }

    private QuizQuestionPoolItem savePendingItem(long ownerCourseId) {
        return savePendingItem(ownerCourseId, firstCompetency.getId(), 0);
    }

    private QuizQuestionPoolItem savePendingItem(long ownerCourseId, long competencyId, int itemIndex) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setCourse(courseRepository.findByIdElseThrow(ownerCourseId));
        item.setCompetencyId(competencyId);
        item.setLectureUnitId(firstTextUnit.getId());
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.SINGLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setItemIndex(itemIndex);
        return itemRepository.save(item);
    }

    private void awaitBuild() {
        await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200)).until(() -> !poolService.getProgress(courseId).running());
    }

    private static String text(Prompt prompt, Class<? extends Message> type) {
        return prompt.getInstructions().stream().filter(type::isInstance).map(Message::getText).findFirst().orElse("");
    }

    private static ChatResponse response(String content) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
    }

    /** A question whose first {@code correctCount} options are correct. */
    private static String question(String type, String title, int correctCount) {
        return """
                { "type": "%s", "title": "%s", "questionText": "Which statement about %s holds?",
                  "options": [ { "text": "First claim", "correct": %s }, { "text": "Second claim", "correct": %s },
                               { "text": "Third claim", "correct": %s }, { "text": "Fourth claim", "correct": %s } ],
                  "explanation": "Because the marked claims follow from the material." }
                """.formatted(type, title, title, correctCount >= 1, correctCount >= 2, correctCount >= 3, correctCount >= 4);
    }

    private static String verdict(double factualError) {
        return """
                { "rationale": "checked", "modes": [
                  { "mode": "FACTUAL_ERROR", "severity": %s, "triggered": false, "justification": "a" },
                  { "mode": "AMBIGUOUS_CORRECT_ANSWER", "severity": 0.0, "triggered": false, "justification": "b" },
                  { "mode": "OFF_TOPIC", "severity": 0.0, "triggered": false, "justification": "c" },
                  { "mode": "NEAR_DUPLICATE", "severity": 0.9, "triggered": false, "justification": "d" },
                  { "mode": "ILL_FORMED_DISTRACTORS", "severity": 0.0, "triggered": false, "justification": "e" } ] }
                """.formatted(factualError);
    }
}
