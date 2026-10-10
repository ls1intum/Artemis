package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisGlobalSearchHandoffDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisPendingContextDTO;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Tests for continuing a global search answer in the Iris course chat ({@code POST api/iris/chat/sessions/global-search-handoff}): the chat opens on the chosen topic
 * with the question and the answer already in it, and is authorized exactly like "New Chat" and choosing a chat topic.
 */
class IrisGlobalSearchHandoffTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irisglobalsearchhandoff";

    private static final String HANDOFF_URL = "/api/iris/chat/sessions/global-search-handoff";

    private static final String QUESTION = "Why is merge sort O(n log n)?";

    private static final String ANSWER = "It halves the input on every level, and every level merges all n elements [cite:L:1:3:::Divide and Conquer:].";

    @Autowired
    private ExamUtilService examUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_toTheCourse_opensACourseChatHoldingTheQuestionAndTheAnswer() throws Exception {
        var created = handoff(handoffDTO(null), HttpStatus.CREATED);

        assertThat(created.mode()).isEqualTo(IrisChatMode.COURSE_CHAT);
        assertThat(created.title()).isEqualTo(QUESTION);
        var messages = loadMessages(created.id());
        assertThat(messages).extracting(IrisMessageResponseDTO::sender).containsExactly(IrisMessageSender.USER, IrisMessageSender.LLM);
        assertThat(textOf(messages.getFirst())).isEqualTo(QUESTION);
        // The answer keeps its text and its citation; only the version field the stamping appends is new.
        assertThat(textOf(messages.getLast())).startsWith(ANSWER.substring(0, ANSWER.length() - 2));
        verify(irisCitationService).stampCitationVersionsWithCurrentMaterial(ANSWER, course.getId());
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_toALectureOrExercise_opensTheChatOnThatTopic(IrisChatMode mode) throws Exception {
        var created = handoff(handoffDTO(new IrisPendingContextDTO(mode, entityIdFor(mode))), HttpStatus.CREATED);

        assertThat(created.mode()).isEqualTo(mode);
        assertThat(created.entityId()).isEqualTo(entityIdFor(mode));
        // The topic is recorded first, exactly as when the student chooses it, so the chat shows its divider above the question.
        assertThat(loadMessages(created.id())).extracting(IrisMessageResponseDTO::sender).containsExactly(IrisMessageSender.CTXSWAP, IrisMessageSender.USER, IrisMessageSender.LLM);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_reusesTodaysEmptyChatLikeNewChat() throws Exception {
        var emptyChat = request.postWithResponseBody("/api/iris/chat/sessions?courseId=" + course.getId(), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        var created = handoff(handoffDTO(null), HttpStatus.CREATED);

        assertThat(created.id()).isEqualTo(emptyChat.id());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_twice_opensTwoSeparateChats() throws Exception {
        var first = handoff(handoffDTO(null), HttpStatus.CREATED);
        var second = handoff(handoffDTO(null), HttpStatus.CREATED);

        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(loadMessages(first.id())).hasSize(2);
        assertThat(loadMessages(second.id())).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_isForbiddenWhenIrisIsDisabledInTheCourse() throws Exception {
        disableIrisFor(course);

        request.post(HANDOFF_URL, handoffDTO(null), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_isForbiddenForACourseTheStudentIsNotIn() throws Exception {
        Course otherCourse = courseUtilService.createCourse();
        activateIrisFor(otherCourse);

        request.post(HANDOFF_URL, new IrisGlobalSearchHandoffDTO(otherCourse.getId(), null, QUESTION, ANSWER), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_isRejectedForAnExamExercise() throws Exception {
        var exam = examUtilService.addExerciseGroupsAndExercisesToExam(examUtilService.addExamWithExerciseGroup(course, true), false);
        var examExercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().iterator().next();
        activateIrisFor(examExercise);

        request.post(HANDOFF_URL, handoffDTO(new IrisPendingContextDTO(IrisChatMode.TEXT_EXERCISE_CHAT, examExercise.getId())), HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void handoff_rejectsABlankAnswerAndAnOverlongQuestion() throws Exception {
        request.post(HANDOFF_URL, new IrisGlobalSearchHandoffDTO(course.getId(), null, QUESTION, " "), HttpStatus.BAD_REQUEST);
        request.post(HANDOFF_URL, new IrisGlobalSearchHandoffDTO(course.getId(), null, "a".repeat(IrisGlobalSearchHandoffDTO.MAX_QUESTION_LENGTH + 1), ANSWER),
                HttpStatus.BAD_REQUEST);
    }

    private IrisGlobalSearchHandoffDTO handoffDTO(@Nullable IrisPendingContextDTO context) {
        return new IrisGlobalSearchHandoffDTO(course.getId(), context, QUESTION, ANSWER);
    }

    private IrisChatSessionResponseDTO handoff(IrisGlobalSearchHandoffDTO handoff, HttpStatus expectedStatus) throws Exception {
        return request.postWithResponseBody(HANDOFF_URL, handoff, IrisChatSessionResponseDTO.class, expectedStatus);
    }

    /** Loads the session the way the chat page does when it opens it. */
    private List<IrisMessageResponseDTO> loadMessages(long sessionId) throws Exception {
        return request.get("/api/iris/chat/courses/" + course.getId() + "/sessions/" + sessionId, HttpStatus.OK, IrisChatSessionResponseDTO.class).messages();
    }

    private static String textOf(IrisMessageResponseDTO message) {
        return message.content().getFirst().textContent();
    }
}
