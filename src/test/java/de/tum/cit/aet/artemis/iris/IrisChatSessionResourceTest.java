package de.tum.cit.aet.artemis.iris;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.DisplayPriority;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CustomAuditEventRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisTutorSuggestionSession;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionCountDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisChatSessionResponseDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.util.IrisChatSessionFactory;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * HTTP-contract tests for {@link de.tum.cit.aet.artemis.iris.web.IrisChatSessionResource}: status
 * codes, DTO shape, role-based authorization, Location headers, citation-service invocation, audit
 * events, and mode-specific edge cases. Business-logic behaviours (day reuse, access rules, mode
 * validation) are covered by the service-level test.
 */
class IrisChatSessionResourceTest extends AbstractIrisChatSessionTest {

    private static final String TEST_PREFIX = "irischatsessionresource";

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    @Autowired
    private IrisChatSessionRepository irisChatSessionRepository;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private CustomAuditEventRepository auditEventRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private ChannelRepository channelRepository;

    @Override
    protected String getTestPrefix() {
        return TEST_PREFIX;
    }

    // =========================================================================
    // Overview endpoint — GET /{courseId}/sessions/overview
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overview_returnsEmptyWhenNoSessionsWithMessages() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        // Sessions without messages must be filtered out by the sidebar.
        irisSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, user));
        irisSessionRepository.save(IrisChatSessionFactory.createLectureSessionForUser(lecture, user));

        List<IrisChatSessionDTO> result = request.getList(overviewUrl(), HttpStatus.OK, IrisChatSessionDTO.class);
        assertThat(result).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overview_returnsAllSessionsWithMessagesAndExposesTitleAndEntityName() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        var courseSession = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user);
        courseSession.setTitle("Course title");
        saveChatSessionWithMessages(courseSession);
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createTextExerciseSessionForUserWithMessages(textExercise, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUserWithMessages(programmingExercise, user));

        List<IrisChatSessionDTO> result = request.getList(overviewUrl(), HttpStatus.OK, IrisChatSessionDTO.class);

        assertThat(result).hasSize(4);
        assertThat(findByEntityId(result, course.getId()).title()).isEqualTo("Course title");
        assertThat(findByEntityId(result, lecture.getId()).entityName()).isEqualTo(lecture.getTitle());
        assertThat(findByEntityId(result, textExercise.getId()).entityName()).isEqualTo(textExercise.getShortName());
        assertThat(findByEntityId(result, programmingExercise.getId()).entityName()).isEqualTo(programmingExercise.getShortName());
        assertThat(findByEntityId(result, lecture.getId()).title()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overview_returnsEmptyWhenIrisDisabledForCourse() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));

        disableIrisFor(course);

        assertThat(request.getList(overviewUrl(), HttpStatus.OK, IrisChatSessionDTO.class)).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void overview_returnsEmptyWhenUserHasNotOptedIntoLLM() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));

        user.setSelectedLLMUsage(AiSelectionDecision.NO_AI);
        userTestRepository.save(user);

        assertThat(request.getList(overviewUrl(), HttpStatus.OK, IrisChatSessionDTO.class)).isEmpty();
    }

    // =========================================================================
    // getSessionById — GET /{courseId}/session/{sessionId}
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionById_returns200AndInvokesCitationService() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = irisSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, user));

        IrisChatSessionResponseDTO response = request.get(sessionUrl(session.getId()), HttpStatus.OK, IrisChatSessionResponseDTO.class);

        assertThat(response.id()).isEqualTo(session.getId());
        verify(irisCitationService).resolveCitationInfoFromMessages(any());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionById_returns404WhenMissing() throws Exception {
        request.get(sessionUrl(NON_EXISTENT_ID), HttpStatus.NOT_FOUND, IrisChatSessionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void getSessionById_returns403WhenNotOwner() throws Exception {
        User otherUser = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession otherUsersSession = irisSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, otherUser));

        request.get(sessionUrl(otherUsersSession.getId()), HttpStatus.FORBIDDEN, IrisChatSessionResponseDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getSessionById_returns403WhenIrisDisabledForCourse() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = irisSessionRepository.save(IrisChatSessionFactory.createCourseChatSessionForUser(course, user));

        disableIrisFor(course);

        request.get(sessionUrl(session.getId()), HttpStatus.FORBIDDEN, IrisChatSessionResponseDTO.class);
    }

    // =========================================================================
    // Delete endpoints
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllSessions_returns204AndRemovesSessionsAndAuditsEvent() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));
        var tutorSuggestionSession = IrisChatSessionFactory.createSessionWithMessages(new IrisTutorSuggestionSession(createPostForTutorSuggestion(user).getId(), user));
        saveChatSessionWithMessages(tutorSuggestionSession);
        Instant before = Instant.now().minusSeconds(1);

        request.delete("/api/iris/chat/sessions", HttpStatus.NO_CONTENT);

        assertThat(request.getList(overviewUrl(), HttpStatus.OK, IrisChatSessionDTO.class)).isEmpty();
        assertThat(irisSessionRepository.findById(tutorSuggestionSession.getId())).isEmpty();
        List<AuditEvent> auditEvents = auditEventRepository.find(user.getLogin(), before, Constants.DELETE_ALL_IRIS_SESSIONS);
        assertThat(auditEvents).hasSize(1);
        assertThat(auditEvents.getFirst().getData()).containsEntry("sessions", "3").containsEntry("messages", "6");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteAllSessions_returns204WhenNothingToDelete() throws Exception {
        request.delete("/api/iris/chat/sessions", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteSession_returns204AndAuditsEvent() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user);
        saveChatSessionWithMessages(session);
        Instant before = Instant.now().minusSeconds(1);

        request.delete("/api/iris/chat/sessions/" + session.getId(), HttpStatus.NO_CONTENT);

        assertThat(irisChatSessionRepository.findById(session.getId())).isEmpty();
        List<AuditEvent> auditEvents = auditEventRepository.find(user.getLogin(), before, Constants.DELETE_IRIS_SESSION);
        assertThat(auditEvents).hasSize(1);
        assertThat(auditEvents.getFirst().getData()).containsEntry("sessionId", String.valueOf(session.getId()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void deleteSession_returns403ForNonOwner() throws Exception {
        User owner = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSession session = IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, owner);
        saveChatSessionWithMessages(session);

        request.delete("/api/iris/chat/sessions/" + session.getId(), HttpStatus.FORBIDDEN);

        assertThat(irisChatSessionRepository.findById(session.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteSession_returns404WhenMissing() throws Exception {
        request.delete("/api/iris/chat/sessions/" + NON_EXISTENT_ID, HttpStatus.NOT_FOUND);
    }

    // =========================================================================
    // Session/message count — GET /sessions/count
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void sessionsCount_returnsCountsForCurrentUser() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        IrisChatSessionCountDTO initial = request.get("/api/iris/chat/sessions/count", HttpStatus.OK, IrisChatSessionCountDTO.class);

        saveChatSessionWithMessages(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createLectureSessionForUserWithMessages(lecture, user));
        saveChatSessionWithMessages(IrisChatSessionFactory.createSessionWithMessages(new IrisTutorSuggestionSession(createPostForTutorSuggestion(user).getId(), user)));

        IrisChatSessionCountDTO updated = request.get("/api/iris/chat/sessions/count", HttpStatus.OK, IrisChatSessionCountDTO.class);
        assertThat(updated.sessions()).isEqualTo(initial.sessions() + 3);
        // The factory creates two messages per session (one from Iris, one from the user).
        assertThat(updated.messages()).isEqualTo(initial.messages() + 6);
    }

    // =========================================================================
    // Mode-uniform getCurrent / createSession (parametrized across 4 chat modes)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrent_returns200AndPersistsSession(IrisChatMode mode) throws Exception {
        long entityId = entityIdFor(mode);

        var response = request.postWithResponseBody(currentUrl(mode, entityId), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        assertThat(response.id()).isNotNull();
        assertThat(response.mode()).isEqualTo(mode);
        IrisChatSession persisted = irisChatSessionRepository.findById(response.id()).orElseThrow();
        assertThat(persisted.getMode()).isEqualTo(mode);
        assertThat(persisted.getCourseId()).isEqualTo(course.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrent_invokesCitationService() throws Exception {
        request.postWithResponseBody(currentUrl(IrisChatMode.COURSE_CHAT, course.getId()), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);

        verify(irisCitationService).enrichSessionWithCitationInfo(any());
    }

    @ParameterizedTest
    @EnumSource(value = IrisChatMode.class, names = { "COURSE_CHAT", "LECTURE_CHAT", "TEXT_EXERCISE_CHAT", "PROGRAMMING_EXERCISE_CHAT" })
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_returns201WithLocationHeader(IrisChatMode mode) throws Exception {
        long entityId = entityIdFor(mode);

        URI location = request.post(createUrl(mode, entityId), null, HttpStatus.CREATED, MediaType.APPLICATION_JSON, true, null);

        assertThat(location).isNotNull();
        String path = location.getPath();
        assertThat(path).matches("/api/iris/chat/" + course.getId() + "/session/\\d+");
        long createdId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));
        IrisChatSession persisted = irisChatSessionRepository.findById(createdId).orElseThrow();
        assertThat(persisted.getMode()).isEqualTo(mode);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void createSession_doesNotInvokeCitationService() throws Exception {
        request.postWithResponseBody(createUrl(IrisChatMode.COURSE_CHAT, course.getId()), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);

        verify(irisCitationService, never()).enrichSessionWithCitationInfo(any());
    }

    // =========================================================================
    // Role-based authorization (annotation applies uniformly across modes — one mode suffices)
    // =========================================================================

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getCurrent_tutorCanAccess() throws Exception {
        var response = request.postWithResponseBody(currentUrl(IrisChatMode.COURSE_CHAT, course.getId()), null, IrisChatSessionResponseDTO.class, HttpStatus.OK);
        assertThat(response.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createSession_instructorCanCreate() throws Exception {
        var response = request.postWithResponseBody(createUrl(IrisChatMode.COURSE_CHAT, course.getId()), null, IrisChatSessionResponseDTO.class, HttpStatus.CREATED);
        assertThat(response.id()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getCurrent_forbiddenWhenStudentNotEnrolledInCourse() throws Exception {
        Course otherCourse = courseUtilService.createCourseWithCustomStudentGroupName("iris-resource-restricted", "restricted-students");
        activateIrisFor(otherCourse);

        request.postWithResponseBody(currentUrl(IrisChatMode.COURSE_CHAT, otherCourse.getId()), null, IrisChatSessionResponseDTO.class, HttpStatus.FORBIDDEN);
    }

    // =========================================================================
    // Mode-specific edge cases
    // =========================================================================

    @Nested
    class CourseChat {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCurrent_notFoundForUnknownCourseId() throws Exception {
            request.postWithResponseBody(currentUrl(IrisChatMode.COURSE_CHAT, NON_EXISTENT_ID), null, IrisChatSessionResponseDTO.class, HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class LectureChat {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCurrent_notFoundForUnknownLectureId() throws Exception {
            request.postWithResponseBody(currentUrl(IrisChatMode.LECTURE_CHAT, NON_EXISTENT_ID), null, IrisChatSessionResponseDTO.class, HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class TextExerciseChat {

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void getCurrent_conflictForExamExercise() throws Exception {
            long examExerciseId = createExamTextExerciseId();
            request.postWithResponseBody(currentUrl(IrisChatMode.TEXT_EXERCISE_CHAT, examExerciseId), null, IrisChatSessionResponseDTO.class, HttpStatus.CONFLICT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void createSession_conflictForExamExercise() throws Exception {
            long examExerciseId = createExamTextExerciseId();
            request.postWithResponseBody(createUrl(IrisChatMode.TEXT_EXERCISE_CHAT, examExerciseId), null, IrisChatSessionResponseDTO.class, HttpStatus.CONFLICT);
        }

        private long createExamTextExerciseId() {
            var exam = examUtilService.addExamWithExerciseGroup(course, true);
            exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, false);
            TextExercise examExercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().iterator().next();
            activateIrisFor(examExercise);
            return examExercise.getId();
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private String overviewUrl() {
        return "/api/iris/chat/" + course.getId() + "/sessions/overview";
    }

    private String sessionUrl(long sessionId) {
        return "/api/iris/chat/" + course.getId() + "/session/" + sessionId;
    }

    private String currentUrl(IrisChatMode mode, long entityId) {
        return "/api/iris/chat/sessions/current?mode=" + mode.name() + "&entityId=" + entityId;
    }

    private String createUrl(IrisChatMode mode, long entityId) {
        return "/api/iris/chat/sessions?mode=" + mode.name() + "&entityId=" + entityId;
    }

    private void saveChatSessionWithMessages(IrisSession session) {
        irisSessionRepository.save(session);
        irisMessageRepository.saveAll(session.getMessages());
    }

    private Post createPostForTutorSuggestion(User user) {
        Channel channel = new Channel();
        channel.setCourse(course);
        channel.setName("ts-" + UUID.randomUUID().toString().substring(0, 8));
        channel = channelRepository.save(channel);

        Post post = new Post();
        post.setAuthor(user);
        post.setContent("Test tutor suggestion post");
        post.setConversation(channel);
        post.setDisplayPriority(DisplayPriority.NONE);
        return postRepository.save(post);
    }

    private IrisChatSessionDTO findByEntityId(List<IrisChatSessionDTO> sessions, long entityId) {
        return sessions.stream().filter(dto -> dto.entityId() == entityId).findFirst().orElseThrow();
    }
}
