package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.admin.service.LLMTokenUsageService;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.StruggleInterventionJob;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisProactiveEpisodeService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleTriggerService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit test for the trigger side of {@link IrisStruggleTriggerService#prepareTrigger}. The three
 * behaviors are the contract: disabled course settings -> no reserve and empty; enabled -> reserve + token + STUDENT
 * role check; overlapping run (single-flight factory returns empty) -> empty.
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionServiceTriggerTest {

    @Mock
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Mock
    private AuthorizationCheckService authCheckService;

    @Mock
    private IrisSettingsService irisSettingsService;

    @Mock
    private IrisChatSessionRepository irisChatSessionRepository;

    @Mock
    private PyrisDTOService pyrisDTOService;

    @Mock
    private PyrisPipelineService pyrisPipelineService;

    @Mock
    private PyrisJobService pyrisJobService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private IrisChatSessionService irisChatSessionService;

    @Mock
    private IrisMessageService irisMessageService;

    @Mock
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private IrisSessionRepository irisSessionRepository;

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Mock
    private UserAiPreferenceService userAiPreferenceService;

    @Mock
    private LLMTokenUsageService llmTokenUsageService;

    private IrisStruggleTriggerService service;

    private static final long EX = 42L;

    private static final long COURSE = 7L;

    private static final long USER_ID = 3L;

    private ProgrammingExercise exercise;

    private Course course;

    private User user;

    @BeforeEach
    void setUp() {
        course = new Course();
        course.setId(COURSE);
        exercise = new ProgrammingExercise();
        exercise.setId(EX);
        exercise.setCourse(course);
        user = new User();
        user.setId(USER_ID);
        user.setLogin("student1");
        // The episode service is the real one on the same mocked repositories: prepareTrigger registers the episode
        // through it, and these tests assert on that registration.
        var episodeService = new IrisProactiveEpisodeService(irisProactiveEpisodeRepository, irisMessageRepository, transactionManager);
        service = new IrisStruggleTriggerService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisChatWebsocketService, userAiPreferenceService, episodeService);
        lenient().when(programmingExerciseRepository.findByIdElseThrow(EX)).thenReturn(exercise);
    }

    @Test
    void cancelOutstandingStruggleJob_matchingToken_removesJob() {
        service.cancelOutstandingStruggleJob(user, EX, "tok-A");
        verify(pyrisJobService).removeStruggleJobIfTokenMatches(USER_ID, EX, "tok-A");
    }

    @Test
    void disabledSettings_doesNotReserveOrEnqueue() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(disabledSettings());

        var result = service.prepareTrigger(EX, user, null, null, null, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.courseDisabled()).isTrue();   // Iris disabled => course-off for proactive purposes
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void proactiveDisabled_marksCourseDisabled() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(proactiveOffSettings());

        var result = service.prepareTrigger(EX, user, null, null, null, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.courseDisabled()).isTrue();
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void enabled_reservesSlotAndReturnsToken() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any())).thenReturn(Optional.of("tok"));

        var result = service.prepareTrigger(EX, user, null, null, null, null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.trigger().jobToken()).isEqualTo("tok");
        verify(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(eq(Role.STUDENT), eq(exercise), eq(user));
    }

    @Test
    void enabled_forwardsProactivityModeToJobAndPreparedTrigger() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), eq("pull"))).thenReturn(Optional.of("tok"));

        var result = service.prepareTrigger(EX, user, null, null, null, null, "pull");

        assertThat(result.accepted()).isTrue();
        // the mode is stamped on the immutable trigger snapshot AND on the Hazelcast job, so the async
        // terminal callback (handleDecision) can deterministically enforce Pull.
        assertThat(result.trigger().proactivityMode()).isEqualTo("pull");
        verify(pyrisJobService).addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), eq("pull"));
    }

    @Test
    void overlappingTrigger_isSkipped() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        var skipped = service.prepareTrigger(EX, user, null, null, null, null, null);

        assertThat(skipped.accepted()).isFalse();
        assertThat(skipped.courseDisabled()).isFalse();  // in-flight, NOT course-off
    }

    @Test
    void sendToPyris_userOptedOut_skipsEgressAndReleasesSlot() {
        // The user reloaded on the async thread is no longer opted into LLM usage (aiSelectionDecision == null) -
        // sendToPyris must bail before any Pyris egress and release the reserved single-flight slot.
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", null, null, null, null, null);
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);

        service.sendToPyris(prepared, signal, Map.of());

        verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        verifyNoInteractions(pyrisPipelineService);
    }

    @Test
    void sendToPyris_userOptedOut_emitsTerminalCompletionBeforeReleasingSlot() {
        // The endpoint already answered 202, so the client is waiting on a terminal frame. When the async re-check
        // finds consent revoked, sendToPyris must emit the intent-shaped completion (so the in-flight decide clears)
        // BEFORE releasing the slot, mirroring the dispatch-failure path - not release silently and leave the client
        // hanging until timeout.
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
        when(userAiPreferenceService.hasOptedIntoLlmUsage(USER_ID)).thenReturn(false);
        when(pyrisJobService.getJob("tok")).thenReturn(new StruggleInterventionJob("tok", COURSE, EX, USER_ID, "decide", "ep-9", null, null, null));
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", "decide", null, null, null, null);
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);

        service.sendToPyris(prepared, signal, Map.of());

        InOrder inOrder = inOrder(irisChatWebsocketService, pyrisJobService);
        inOrder.verify(irisChatWebsocketService).sendStruggleEvent(eq(user), argThat(e -> "decide".equals(e.kind()) && "silent".equals(e.action()) && "ep-9".equals(e.episodeId())
                && e.message() == null && e.sessionId() == null && e.messageId() == null));
        inOrder.verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        verifyNoInteractions(pyrisPipelineService);
    }

    @Test
    void sendToPyris_userOptedOut_confirmCloseEmitsUnresolvedCloseBeforeReleasingSlot() {
        // Same bail path, confirm_close intent: the terminal frame must be the close-shaped completion with
        // resolved=false (a failed close must not read as "the episode is resolved"), again before the release.
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
        when(userAiPreferenceService.hasOptedIntoLlmUsage(USER_ID)).thenReturn(false);
        when(pyrisJobService.getJob("tok")).thenReturn(new StruggleInterventionJob("tok", COURSE, EX, USER_ID, "confirm_close", "ep-9", "progress", null, null));
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", "confirm_close", null, "progress", null, null);
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);

        service.sendToPyris(prepared, signal, Map.of());

        InOrder inOrder = inOrder(irisChatWebsocketService, pyrisJobService);
        inOrder.verify(irisChatWebsocketService).sendStruggleEvent(eq(user),
                argThat(e -> "confirm_close".equals(e.kind()) && Boolean.FALSE.equals(e.resolved()) && "ep-9".equals(e.episodeId())));
        inOrder.verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        verifyNoInteractions(pyrisPipelineService);
    }

    private static IrisCourseSettings enabledSettings() {
        return new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null, null, true, null);   // Iris + proactive ON
    }

    private static IrisCourseSettings disabledSettings() {
        return new IrisCourseSettings(false, null, IrisPipelineVariant.DEFAULT, null, null, false, null);  // Iris OFF
    }

    private static IrisCourseSettings proactiveOffSettings() {
        return new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null, null, false, null);   // Iris ON, proactive OFF
    }
}
