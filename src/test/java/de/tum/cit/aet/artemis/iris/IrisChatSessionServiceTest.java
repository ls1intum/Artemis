package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.iris.domain.message.IrisJsonMessageContent;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service-level tests for {@link IrisChatSessionService}. Focuses on business logic:
 * access checks, session creation/reuse semantics per chat mode, exam-exercise guarding,
 * and Iris-enablement validation. HTTP-layer concerns are covered by
 * {@link IrisChatSessionResourceTest}.
 */
class IrisChatSessionServiceTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irischatsessionservice";

    @Autowired
    private IrisChatSessionService irisChatSessionService;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    private User student1() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student1");
    }

    private User student2() {
        return userUtilService.getUserByLogin(TEST_PREFIX + "student2");
    }

    private IrisChatSession newSessionFor(IrisChatMode mode, User user) {
        return switch (mode) {
            case COURSE_CHAT -> new IrisChatSession(course, user);
            case LECTURE_CHAT -> new IrisChatSession(lecture, user);
            case TEXT_EXERCISE_CHAT -> new IrisChatSession(textExercise, user, mode);
            case PROGRAMMING_EXERCISE_CHAT -> new IrisChatSession(programmingExercise, user, mode);
            default -> throw new IllegalArgumentException("Unsupported mode for chat session: " + mode);
        };
    }

    // =========================================================================
    // checkHasAccessTo
    // =========================================================================

    @Nested
    class CheckHasAccessTo {

        @ParameterizedTest
        @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
        void allowsSessionOwner(IrisChatMode mode) {
            User user = student1();
            IrisChatSession session = newSessionFor(mode, user);
            session.setId(1L);

            assertThatNoException().isThrownBy(() -> irisChatSessionService.checkHasAccessTo(user, session));
        }

        @Test
        void throwsForDifferentUser() {
            IrisChatSession session = newSessionFor(IrisChatMode.COURSE_CHAT, student2());
            session.setId(1L);

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> irisChatSessionService.checkHasAccessTo(student1(), session))
                    .withMessageContaining("Iris Session");
        }

        @Test
        void throwsWhenUserHasNotOptedIntoLLM() {
            User user = student1();
            user.setSelectedLLMUsage(null);
            IrisChatSession session = newSessionFor(IrisChatMode.COURSE_CHAT, user);
            session.setId(1L);

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> irisChatSessionService.checkHasAccessTo(user, session))
                    .withMessageContaining("not selected to use AI");
        }

        @Test
        void throwsWhenUserOptedOutOfLLM() {
            User user = student1();
            user.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
            IrisChatSession session = newSessionFor(IrisChatMode.COURSE_CHAT, user);
            session.setId(1L);

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> irisChatSessionService.checkHasAccessTo(user, session))
                    .withMessageContaining("not selected to use AI");
        }

        @Test
        void allowsLocalAI() {
            User user = student1();
            user.setSelectedLLMUsage(AiSelectionDecision.LOCAL_AI);
            IrisChatSession session = newSessionFor(IrisChatMode.COURSE_CHAT, user);
            session.setId(1L);

            assertThatNoException().isThrownBy(() -> irisChatSessionService.checkHasAccessTo(user, session));
        }

        @Test
        void throwsWhenUserNotEnrolledInCourse() {
            Course otherCourse = courseUtilService.createCourseWithCustomStudentGroupName("iris-chat-service-other", "other-group");
            IrisChatSession session = new IrisChatSession(otherCourse, student1());
            session.setId(1L);

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> irisChatSessionService.checkHasAccessTo(student1(), session));
        }
    }

    // =========================================================================
    // getCurrentSessionOrCreateIfNotExists
    // =========================================================================

    @Nested
    class GetCurrentSessionOrCreateIfNotExists {

        @ParameterizedTest
        @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
        void createsNewSessionWhenNoneExists(IrisChatMode mode) {
            User user = student1();
            long entityId = entityIdFor(mode);

            IrisChatSession result = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(mode, entityId, user);

            assertThat(result.getId()).isNotNull();
            assertThat(result.getMode()).isEqualTo(mode);
            assertThat(result.getEntityId()).isEqualTo(entityId);
            assertThat(result.getUserId()).isEqualTo(user.getId());
        }

        @ParameterizedTest
        @EnumSource(value = IrisChatMode.class, names = { "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
        void reusesExistingSessionEvenIfCreatedYesterday(IrisChatMode mode) {
            User user = student1();
            IrisChatSession existing = newSessionFor(mode, user);
            existing.setCreationDate(ZonedDateTime.now().minusDays(1));
            irisChatSessionRepository.save(existing);

            IrisChatSession result = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(mode, entityIdFor(mode), user);

            assertThat(result.getId()).isEqualTo(existing.getId());
        }

        @Test
        void courseChatCreatesNewSessionWhenLastOneIsFromYesterday() {
            User user = student1();
            IrisChatSession yesterday = new IrisChatSession(course, user);
            yesterday.setCreationDate(ZonedDateTime.now().minusDays(1));
            irisChatSessionRepository.save(yesterday);

            IrisChatSession result = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.COURSE_CHAT, course.getId(), user);

            assertThat(result.getId()).isNotEqualTo(yesterday.getId());
        }

        @Test
        void courseChatReusesSessionCreatedToday() {
            User user = student1();
            IrisChatSession today = irisChatSessionRepository.save(new IrisChatSession(course, user));

            IrisChatSession result = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.COURSE_CHAT, course.getId(), user);

            assertThat(result.getId()).isEqualTo(today.getId());
        }

        @Test
        void throwsWhenUserHasNotOptedIntoLLM() {
            User user = student1();
            user.setSelectedLLMUsage(null);

            assertThatExceptionOfType(AccessForbiddenException.class)
                    .isThrownBy(() -> irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.COURSE_CHAT, course.getId(), user));
        }

        @Test
        void throwsWhenIrisDisabledForCourse() {
            disableIrisFor(course);

            assertThatExceptionOfType(AccessForbiddenAlertException.class)
                    .isThrownBy(() -> irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.COURSE_CHAT, course.getId(), student1()));
        }

        @Test
        void throwsConflictForExamExerciseInTextMode() {
            TextExercise examExercise = createExamTextExercise();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.TEXT_EXERCISE_CHAT, examExercise.getId(), student1()));
        }
    }

    // =========================================================================
    // createSession
    // =========================================================================

    @Nested
    class CreateSession {

        @ParameterizedTest
        @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
        void alwaysCreatesNewSessionEvenWhenOneExists(IrisChatMode mode) {
            User user = student1();
            IrisChatSession existing = irisChatSessionRepository.save(newSessionFor(mode, user));

            IrisChatSession result = irisChatSessionService.createSession(mode, entityIdFor(mode), user);

            assertThat(result.getId()).isNotNull().isNotEqualTo(existing.getId());
            assertThat(result.getMode()).isEqualTo(mode);
        }

        @Test
        void throwsWhenUserHasNotOptedIntoLLM() {
            User user = student1();
            user.setSelectedLLMUsage(null);

            assertThatExceptionOfType(AccessForbiddenException.class).isThrownBy(() -> irisChatSessionService.createSession(IrisChatMode.COURSE_CHAT, course.getId(), user));
        }

        @Test
        void throwsWhenIrisDisabledForCourse() {
            disableIrisFor(course);

            assertThatExceptionOfType(AccessForbiddenAlertException.class)
                    .isThrownBy(() -> irisChatSessionService.createSession(IrisChatMode.COURSE_CHAT, course.getId(), student1()));
        }

        @Test
        void throwsConflictForExamExerciseInTextMode() {
            TextExercise examExercise = createExamTextExercise();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> irisChatSessionService.createSession(IrisChatMode.TEXT_EXERCISE_CHAT, examExercise.getId(), student1()));
        }

        @Test
        void setsLocalizedTitleOnCreation() {
            IrisChatSession result = irisChatSessionService.createSession(IrisChatMode.COURSE_CHAT, course.getId(), student1());

            assertThat(result.getTitle()).isNotBlank();
        }
    }

    // =========================================================================
    // applyContextChange — pending context change applied atomically with sendMessage
    // =========================================================================

    @Nested
    class ApplyContextChange {

        @Test
        void switchesModeAndEntityIdAndAppendsCtxswapMarker() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.COURSE_CHAT, user));

            irisChatSessionService.applyContextChange(session, IrisChatMode.LECTURE_CHAT, lecture.getId(), user);

            IrisChatSession reloaded = irisChatSessionRepository.findById(session.getId()).orElseThrow();
            assertThat(reloaded.getMode()).isEqualTo(IrisChatMode.LECTURE_CHAT);
            assertThat(reloaded.getEntityId()).isEqualTo(lecture.getId());

            var markers = irisMessageRepository.findAllBySessionIdOrderBySentAtAscIdAsc(session.getId()).stream().filter(m -> m.getSender() == IrisMessageSender.CTXSWAP).toList();
            assertThat(markers).hasSize(1);
            var markerContent = (IrisJsonMessageContent) markers.getFirst().getContent().getFirst();
            assertThat(markerContent.getJsonNode().get("transition").asText()).isEqualTo("added");
            assertThat(markerContent.getJsonNode().get("entityMode").asText()).isEqualTo(IrisChatMode.LECTURE_CHAT.name());
            assertThat(markerContent.getJsonNode().get("entityId").asLong()).isEqualTo(lecture.getId());
            assertThat(markerContent.getJsonNode().get("name").asText()).isEqualTo(lecture.getTitle());
        }

        @Test
        void switchingBackToCourseLabelsMarkerWithRemovedEntityName() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.LECTURE_CHAT, user));

            irisChatSessionService.applyContextChange(session, IrisChatMode.COURSE_CHAT, course.getId(), user);

            var markers = irisMessageRepository.findAllBySessionIdOrderBySentAtAscIdAsc(session.getId()).stream().filter(m -> m.getSender() == IrisMessageSender.CTXSWAP).toList();
            assertThat(markers).hasSize(1);
            var markerContent = (IrisJsonMessageContent) markers.getFirst().getContent().getFirst();
            assertThat(markerContent.getJsonNode().get("transition").asText()).isEqualTo("removed");
            assertThat(markerContent.getJsonNode().get("entityMode").asText()).isEqualTo(IrisChatMode.LECTURE_CHAT.name());
            assertThat(markerContent.getJsonNode().get("entityId").asLong()).isEqualTo(lecture.getId());
            assertThat(markerContent.getJsonNode().get("name").asText()).isEqualTo(lecture.getTitle());
        }

        @Test
        void switchingBetweenLectureAndExerciseLabelsMarkerAsChanged() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.LECTURE_CHAT, user));

            irisChatSessionService.applyContextChange(session, IrisChatMode.TEXT_EXERCISE_CHAT, textExercise.getId(), user);

            var markers = irisMessageRepository.findAllBySessionIdOrderBySentAtAscIdAsc(session.getId()).stream().filter(m -> m.getSender() == IrisMessageSender.CTXSWAP).toList();
            assertThat(markers).hasSize(1);
            var markerContent = (IrisJsonMessageContent) markers.getFirst().getContent().getFirst();
            assertThat(markerContent.getJsonNode().get("transition").asText()).isEqualTo("changed");
            assertThat(markerContent.getJsonNode().get("entityMode").asText()).isEqualTo(IrisChatMode.TEXT_EXERCISE_CHAT.name());
            assertThat(markerContent.getJsonNode().get("entityId").asLong()).isEqualTo(textExercise.getId());
            assertThat(markerContent.getJsonNode().get("name").asText()).isEqualTo(textExercise.getTitle());
        }

        @Test
        void isIdempotentWhenContextUnchanged() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.COURSE_CHAT, user));

            irisChatSessionService.applyContextChange(session, IrisChatMode.COURSE_CHAT, course.getId(), user);

            assertThat(irisMessageRepository.findAllBySessionIdOrderBySentAtAscIdAsc(session.getId())).isEmpty();
        }

        @Test
        void throwsConflictWhenCourseChatEntityDoesNotMatchSessionCourse() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.LECTURE_CHAT, user));

            Course otherCourse = courseUtilService.createCourse();
            activateIrisFor(otherCourse);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> irisChatSessionService.applyContextChange(session, IrisChatMode.COURSE_CHAT, otherCourse.getId(), user));
        }

        @Test
        void throwsConflictForExamExerciseTarget() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.COURSE_CHAT, user));
            TextExercise examExercise = createExamTextExercise();

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> irisChatSessionService.applyContextChange(session, IrisChatMode.TEXT_EXERCISE_CHAT, examExercise.getId(), user));
        }

        @Test
        void throwsConflictWhenTargetEntityBelongsToDifferentCourse() {
            User user = student1();
            IrisChatSession session = irisChatSessionRepository.save(newSessionFor(IrisChatMode.COURSE_CHAT, user));

            Course otherCourse = courseUtilService.createCourse();
            Lecture otherLecture = lectureUtilService.createLecture(otherCourse);
            activateIrisFor(otherCourse);

            assertThatExceptionOfType(ConflictException.class)
                    .isThrownBy(() -> irisChatSessionService.applyContextChange(session, IrisChatMode.LECTURE_CHAT, otherLecture.getId(), user));
        }

    }

    // =========================================================================
    // checkIrisEnabledFor
    // =========================================================================

    @Nested
    class CheckIrisEnabledFor {

        @Test
        void passesWhenEnabled() {
            IrisChatSession session = new IrisChatSession(course, student1());

            assertThatCode(() -> irisChatSessionService.checkIrisEnabledFor(session)).doesNotThrowAnyException();
        }

        @Test
        void throwsWhenDisabled() {
            disableIrisFor(course);
            IrisChatSession session = new IrisChatSession(course, student1());

            assertThatThrownBy(() -> irisChatSessionService.checkIrisEnabledFor(session)).isInstanceOf(AccessForbiddenAlertException.class);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TextExercise createExamTextExercise() {
        var exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
        TextExercise examExercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().iterator().next();
        activateIrisFor(examExercise);
        return examExercise;
    }
}
