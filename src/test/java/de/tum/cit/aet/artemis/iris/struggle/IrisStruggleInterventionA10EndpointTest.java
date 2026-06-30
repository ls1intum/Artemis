package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.account.util.UserUtilService;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.iris.AbstractIrisIntegrationTest;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessage;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageOrigin;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageSender;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.domain.message.IrisTextMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatMode;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.dto.CancelStruggleJobRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.EpisodeOutcomeAppliedDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.RevealAmbientRequestDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Integration tests for the four A10 endpoints: reveal, delete-proactive, cancel, and episode-outcome.
 * Boots H2 and validates the full HTTP stack including the Liquibase migration.
 */
class IrisStruggleInterventionA10EndpointTest extends AbstractIrisIntegrationTest {

    private static final String TEST_PREFIX = "a10endpoint";

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private IrisMessageRepository irisMessageRepository;

    @Autowired
    private IrisMessageService irisMessageService;

    @Autowired
    private IrisChatSessionService irisChatSessionService;

    private ProgrammingExercise exercise;

    @BeforeEach
    void initTestCase() {
        // student1 + student3 are opted in (CLOUD_AI); student2 is the opted-out case. student3 is a second opted-in
        // user, needed to seed a foreign-owned session for the cross-user delete-guard test (session creation enforces
        // opt-in, so the foreign owner cannot be the opted-out student2).
        userUtilService.addUsers(TEST_PREFIX, 3, 0, 0, 1);

        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        student1.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        userTestRepository.save(student1);

        var student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        student2.setSelectedLLMUsage(null);
        userTestRepository.save(student2);

        var student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        student3.setSelectedLLMUsage(AiSelectionDecision.CLOUD_AI);
        userTestRepository.save(student3);

        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        exercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);

        activateIrisFor(course);
        activateIrisFor(exercise);

        var courseSettings = irisSettingsService.getSettingsForCourse(course);
        irisSettingsService.updateCourseSettings(course.getId(),
                IrisCourseSettings.of(courseSettings.enabled(), courseSettings.customInstructions(), courseSettings.variant(), courseSettings.rateLimit(), true), true);
    }

    private long exerciseId() {
        return exercise.getId();
    }

    // ---- reveal ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reveal_createsRow_returns200WithDto() throws Exception {
        var body = new RevealAmbientRequestDTO("Fix the loop.", "ambient", "client-uuid-1");

        var dto = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-1/reveal", body, IrisMessageResponseDTO.class, HttpStatus.OK);

        assertThat(dto).isNotNull();
        assertThat(dto.id()).isNotNull();
        assertThat(dto.proactiveEpisodeId()).isEqualTo("ep-1");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reveal_retry_sameClientMessageId_returnsExistingDto() throws Exception {
        var body = new RevealAmbientRequestDTO("Fix the loop.", "ambient", "client-uuid-retry");

        var dto1 = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-retry/reveal", body, IrisMessageResponseDTO.class, HttpStatus.OK);

        var dto2 = request.postWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-retry/reveal", body, IrisMessageResponseDTO.class, HttpStatus.OK);

        assertThat(dto1.id()).isNotNull();
        assertThat(dto2.id()).isEqualTo(dto1.id()); // same row, no duplicate
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void reveal_optedOutUser_isForbidden() throws Exception {
        var body = new RevealAmbientRequestDTO("Fix the loop.", "ambient", "cid-x");
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-1/reveal", body, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void reveal_blankClientMessageId_isBadRequest() throws Exception {
        // The idempotency key is mandatory: a blank clientMessageId must be rejected (cannot dedupe on a NULL key).
        var body = new RevealAmbientRequestDTO("Fix the loop.", "ambient", "");
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-blank/reveal", body, HttpStatus.BAD_REQUEST);
    }

    // ---- episode-outcome ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void episodeOutcome_noRowYet_returnsAppliedFalse() throws Exception {
        var result = request.putWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-none/proactive-outcome", IrisProactiveOutcome.DISMISSED,
                EpisodeOutcomeAppliedDTO.class, HttpStatus.OK);
        assertThat(result.applied()).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void episodeOutcome_rowExists_returnsAppliedTrue() throws Exception {
        // Seed a proactive row for the episode
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student1);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("hint"));
        msg.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        msg.setProactiveEpisodeId("ep-exists");
        irisMessageService.saveMessage(msg, session, IrisMessageSender.LLM);

        var result = request.putWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-exists/proactive-outcome", IrisProactiveOutcome.DISMISSED,
                EpisodeOutcomeAppliedDTO.class, HttpStatus.OK);
        assertThat(result.applied()).isTrue();

        // Verify the outcome was actually written
        var outcomes = irisMessageRepository.findEpisodeOutcomes("ep-exists");
        assertThat(outcomes).containsExactly(IrisProactiveOutcome.DISMISSED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void episodeOutcome_optedOutStudent_isNotForbidden() throws Exception {
        // Recording a student's reaction to an already-delivered hint must never be rejected on the LLM opt-in gate.
        // student2 is opted out (selectedLLMUsage == null) yet must still be able to record an outcome (no 403).
        var result = request.putWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-optedout/proactive-outcome", IrisProactiveOutcome.DISMISSED,
                EpisodeOutcomeAppliedDTO.class, HttpStatus.OK);
        assertThat(result.applied()).isFalse();   // no row yet for this episode -> deferred, but NOT forbidden
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void episodeOutcome_differentValueAfterFirst_isIgnored_firstTerminalWins() throws Exception {
        // Seed one proactive row, write DISMISSED, then attempt RECOVERED: the first terminal outcome must stand.
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student1);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("hint"));
        msg.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        msg.setProactiveEpisodeId("ep-firstwins");
        irisMessageService.saveMessage(msg, session, IrisMessageSender.LLM);

        var first = request.putWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-firstwins/proactive-outcome", IrisProactiveOutcome.DISMISSED,
                EpisodeOutcomeAppliedDTO.class, HttpStatus.OK);
        assertThat(first.applied()).isTrue();

        var second = request.putWithResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/episodes/ep-firstwins/proactive-outcome", IrisProactiveOutcome.RECOVERED,
                EpisodeOutcomeAppliedDTO.class, HttpStatus.OK);
        assertThat(second.applied()).isTrue();   // applied=true (a row exists) but the value is NOT overwritten

        // First-terminal-wins: exactly one outcome, still DISMISSED.
        assertThat(irisMessageRepository.findEpisodeOutcomes("ep-firstwins")).containsExactly(IrisProactiveOutcome.DISMISSED);
    }

    // ---- delete-proactive ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProactive_nullOutcomeProactiveRow_deletesIt() throws Exception {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student1);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("hint"));
        msg.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        var saved = irisMessageService.saveMessage(msg, session, IrisMessageSender.LLM);

        request.delete("/api/iris/chat/exercises/" + exerciseId() + "/messages/" + saved.getId() + "/proactive", HttpStatus.NO_CONTENT);

        assertThat(irisMessageRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProactive_missingRow_isNoop204() throws Exception {
        request.delete("/api/iris/chat/exercises/" + exerciseId() + "/messages/99999/proactive", HttpStatus.NO_CONTENT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProactive_terminalOutcomeRow_isRefused204() throws Exception {
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student1);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("hint"));
        msg.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        msg.setProactiveOutcome(IrisProactiveOutcome.DISMISSED);
        var saved = irisMessageService.saveMessage(msg, session, IrisMessageSender.LLM);

        request.delete("/api/iris/chat/exercises/" + exerciseId() + "/messages/" + saved.getId() + "/proactive", HttpStatus.NO_CONTENT);

        // Row must still exist
        assertThat(irisMessageRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProactive_nonProactiveRow_isRefused204() throws Exception {
        // A non-proactive (regular USER) message must NOT be deletable via this path.
        var student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student1);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("a normal user message"));
        var saved = irisMessageService.saveMessage(msg, session, IrisMessageSender.USER);

        request.delete("/api/iris/chat/exercises/" + exerciseId() + "/messages/" + saved.getId() + "/proactive", HttpStatus.NO_CONTENT);

        assertThat(irisMessageRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void deleteProactive_otherUsersRow_isRefused204() throws Exception {
        // student1 must NOT be able to delete a proactive row that belongs to another user's (student3's) session.
        var student3 = userUtilService.getUserByLogin(TEST_PREFIX + "student3");
        var session = irisChatSessionService.getCurrentSessionOrCreateIfNotExists(IrisChatMode.PROGRAMMING_EXERCISE_CHAT, exerciseId(), student3);
        var msg = new IrisMessage();
        msg.addContent(new IrisTextMessageContent("hint"));
        msg.setOrigin(IrisMessageOrigin.PROACTIVE_STRUGGLE);
        var saved = irisMessageService.saveMessage(msg, session, IrisMessageSender.LLM);

        request.delete("/api/iris/chat/exercises/" + exerciseId() + "/messages/" + saved.getId() + "/proactive", HttpStatus.NO_CONTENT);

        assertThat(irisMessageRepository.findById(saved.getId())).isPresent();
    }

    // ---- cancel ----

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void cancel_noJobPending_isNoop204() throws Exception {
        var body = new CancelStruggleJobRequestDTO("tok-nonexistent");
        request.postWithoutResponseBody("/api/iris/chat/exercises/" + exerciseId() + "/struggle-intervention/cancel", body, HttpStatus.NO_CONTENT);
    }
}
