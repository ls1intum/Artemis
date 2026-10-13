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
import java.util.List;
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
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationLanguage;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionGenerationType;
import de.tum.cit.aet.artemis.hyperion.dto.QuizQuestionPoolItemDTO;
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
     * The first competency has a text unit and a slide-deck unit, the second a text unit: three lecture units, each with two question types
     * and three difficulties, give eighteen cells; twelve questions per lecture unit give two items per cell.
     */
    private static final int EXPECTED_ITEMS = 36;

    private static final int EXPECTED_CELLS = 18;

    private static final int EXPECTED_ITEMS_OF_FIRST_COMPETENCY = 24;

    /** A phrase on the first slide of the test deck, which must reach the generation prompt through the PDF text extraction. */
    private static final String SLIDE_DECK_PHRASE = "Control Structures";

    private static final Pattern NUMBER_OF_QUESTIONS = Pattern.compile("Number of Questions: (\\d+)");

    private static final Pattern QUESTION_TYPE = Pattern.compile("Question Type: ([a-z-]+)");

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
        LectureUnit textUnit = lectureUtilService.createTextUnit(lecture);
        firstTextUnit = textUnit;
        AttachmentVideoUnit slideDeck = lectureUtilService.createAttachmentVideoUnitWithSlidesAndFile(lecture, 1, true);
        slideDeck.setName("Control structures slides");
        slideDeck = attachmentVideoUnitRepository.save(slideDeck);
        LectureUnit secondTextUnit = lectureUtilService.createTextUnit(lecture);
        competencyUtilService.linkLectureUnitToCompetency(firstCompetency, textUnit);
        competencyUtilService.linkLectureUnitToCompetency(firstCompetency, slideDeck);
        competencyUtilService.linkLectureUnitToCompetency(secondCompetency, secondTextUnit);

        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "student1"), course, CourseRole.STUDENT);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1"), course, CourseRole.TEACHING_ASSISTANT);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), course, CourseRole.EDITOR);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"), course, CourseRole.INSTRUCTOR);

        questionCounter.set(0);
        generationFails.set(false);
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
                return response(verdict(user.contains("Title: Reject") ? 1.0 : 0.0));
            }
            if (generationFails.get()) {
                throw new RuntimeException("model unavailable");
            }
            if (user.contains(SLIDE_DECK_PHRASE)) {
                slideDeckReachedThePrompt.set(true);
            }
            Matcher count = NUMBER_OF_QUESTIONS.matcher(user);
            Matcher type = QUESTION_TYPE.matcher(user);
            assertThat(count.find()).isTrue();
            assertThat(type.find()).isTrue();
            int number = Integer.parseInt(count.group(1));
            StringBuilder questions = new StringBuilder();
            for (int index = 0; index < number; index++) {
                if (index > 0) {
                    questions.append(",");
                }
                int id = questionCounter.incrementAndGet();
                questions.append(question(type.group(1), (index == number - 1 ? "Reject " : "") + "Q" + id, type.group(1).equals("multiple-choice")));
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

        List<QuizQuestionPoolItemDTO> items = request.getList("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.OK, QuizQuestionPoolItemDTO.class);
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

        List<QuizQuestionPoolItemDTO> ofFirstCompetency = request.getList(
                "/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions?competencyId=" + firstCompetency.getId(), HttpStatus.OK, QuizQuestionPoolItemDTO.class);
        assertThat(ofFirstCompetency).hasSize(EXPECTED_ITEMS_OF_FIRST_COMPETENCY);
        assertThat(ofFirstCompetency).allMatch(item -> item.competencyId().equals(firstCompetency.getId()));
        assertThat(ofFirstCompetency.stream().map(QuizQuestionPoolItemDTO::lectureUnitId).distinct()).hasSize(2);

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
        assertThat(request.getList("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.OK, QuizQuestionPoolItemDTO.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = { "USER", "STUDENT" })
    void shouldReturnForbiddenForStudentOnProgress() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/progress", HttpStatus.FORBIDDEN, QuizQuestionPoolProgressDTO.class);
        request.getList("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions", HttpStatus.FORBIDDEN, QuizQuestionPoolItemDTO.class);
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
        assertThat(defaults).isEqualTo(new HyperionCourseSettingsDTO(false, 12));

        HyperionCourseSettingsDTO updated = request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings",
                new HyperionCourseSettingsDTO(true, 30), HyperionCourseSettingsDTO.class, HttpStatus.OK);
        assertThat(updated).isEqualTo(new HyperionCourseSettingsDTO(true, 30));

        HyperionCourseSettingsDTO reloaded = request.get("/api/hyperion/courses/" + courseId + "/quiz-question-pool/settings", HttpStatus.OK, HyperionCourseSettingsDTO.class);
        assertThat(reloaded).isEqualTo(new HyperionCourseSettingsDTO(true, 30));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = { "USER", "INSTRUCTOR" })
    void shouldRejectSettingsOutsideTheAllowedRange() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        request.performMvcRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/hyperion/courses/{courseId}/quiz-question-pool/settings", courseId)
                .contentType(MediaType.APPLICATION_JSON).content("{ \"poolGenerationEnabled\": true, \"questionsPerLectureUnit\": 0 }")).andExpect(status().isBadRequest());
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
        QuizQuestionPoolItem item = savePendingItem(courseId);

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
        QuizQuestionPoolItem item = savePendingItem(courseId);
        Course other = CourseFactory.generateMinimalCourse();
        other.setTitle("Hyperion Pool Other Course");
        other = courseRepository.save(other);
        userUtilService.enrollUserInCourse(userUtilService.getUserByLogin(TEST_PREFIX + "editor1"), other, CourseRole.EDITOR);

        request.putWithResponseBody("/api/hyperion/courses/" + other.getId() + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.NOT_FOUND);
        assertThat(itemRepository.findByIdElseThrow(item.getId()).getReviewState()).isEqualTo(QuizQuestionReviewState.PENDING);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = { "USER", "TA" })
    void shouldReturnForbiddenForTutorOnReview() throws Exception {
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        QuizQuestionPoolItem item = savePendingItem(courseId);

        request.putWithResponseBody("/api/hyperion/courses/" + courseId + "/quiz-question-pool/questions/" + item.getId() + "/review",
                new QuizQuestionPoolReviewDTO(QuizQuestionReviewState.APPROVED), QuizQuestionPoolItemDTO.class, HttpStatus.FORBIDDEN);
    }

    private QuizQuestionPoolItem savePendingItem(long ownerCourseId) {
        QuizQuestionPoolItem item = new QuizQuestionPoolItem();
        item.setCourse(courseRepository.findByIdElseThrow(ownerCourseId));
        item.setCompetencyId(firstCompetency.getId());
        item.setLectureUnitId(firstTextUnit.getId());
        item.setLanguage(QuizQuestionGenerationLanguage.EN);
        item.setQuestionType(QuizQuestionGenerationType.SINGLE_CHOICE);
        item.setDifficulty(QuizQuestionDifficulty.MEDIUM);
        item.setItemIndex(0);
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

    private static String question(String type, String title, boolean twoCorrect) {
        return """
                { "type": "%s", "title": "%s", "questionText": "Which statement about %s holds?",
                  "options": [ { "text": "First claim", "correct": true }, { "text": "Second claim", "correct": %s },
                               { "text": "Third claim", "correct": false }, { "text": "Fourth claim", "correct": false } ],
                  "explanation": "Because the first claim follows from the material." }
                """.formatted(type, title, title, twoCorrect);
    }

    private static String verdict(double factualError) {
        return """
                { "rationale": "checked", "modes": [
                  { "mode": "FACTUAL_ERROR", "severity": %s, "triggered": false, "justification": "a" },
                  { "mode": "AMBIGUOUS_CORRECT_ANSWER", "severity": 0.0, "triggered": false, "justification": "b" },
                  { "mode": "OFF_TOPIC", "severity": 0.0, "triggered": false, "justification": "c" },
                  { "mode": "NEAR_DUPLICATE", "severity": 0.0, "triggered": false, "justification": "d" },
                  { "mode": "ILL_FORMED_DISTRACTORS", "severity": 0.0, "triggered": false, "justification": "e" } ] }
                """.formatted(factualError);
    }
}
