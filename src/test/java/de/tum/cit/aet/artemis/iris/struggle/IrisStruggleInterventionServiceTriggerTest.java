package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
import de.tum.cit.aet.artemis.core.exception.RateLimitExceededException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisProactiveProperties;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.dto.StruggleEpisodeDTO;
import de.tum.cit.aet.artemis.iris.exception.IrisRateLimitExceededException;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.IrisRateLimitService;
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
    private IrisChatWebsocketService irisChatWebsocketService;

    @Mock
    private IrisMessageRepository irisMessageRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    @Mock
    private UserAiPreferenceService userAiPreferenceService;

    @Mock
    private IrisRateLimitService irisRateLimitService;

    private IrisStruggleTriggerService service;

    private final IrisProactiveProperties proactiveProperties = new IrisProactiveProperties();

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
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisChatWebsocketService, userAiPreferenceService, episodeService,
                irisRateLimitService, proactiveProperties);
        lenient().when(programmingExerciseRepository.findByIdElseThrow(EX)).thenReturn(exercise);
        // Every trigger charges the admission cooldown first; a Mockito Optional defaults to empty, which would
        // reject them all with a 429. The cooldown's own cases stub this explicitly.
        lenient().when(pyrisJobService.chargeStruggleCooldown(anyLong(), anyLong(), any())).thenReturn(Optional.of("cool"));
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
    void rateLimitReached_throwsWithoutReservingOrRegistering() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        doThrow(new IrisRateLimitExceededException(new IrisRateLimitService.IrisRateLimitInformation(5, 5, 1))).when(irisRateLimitService).checkRateLimitElseThrow(eq(COURSE),
                eq(user));

        // Propagated, not converted into an unaccepted 202: that body means "a run is already going, await its
        // frame", and no run is going here, so the client would wait for a frame nobody owes it.
        assertThatExceptionOfType(IrisRateLimitExceededException.class).isThrownBy(() -> service.prepareTrigger(EX, user, null, null, null, null, null));

        // Nothing may be left behind, which is why the check sits ahead of the reservation: no slot is taken, so no
        // job entry and no episode row (both of which are written only after a successful reservation) can leak.
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
        verifyNoInteractions(irisProactiveEpisodeRepository);
    }

    @Test
    void cooldownActive_throwsWithoutReservingOrRegistering() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.chargeStruggleCooldown(eq(USER_ID), eq(EX), any())).thenReturn(Optional.empty());

        assertThatExceptionOfType(RateLimitExceededException.class).isThrownBy(() -> service.prepareTrigger(EX, user, null, null, null, null, null));

        // The charge runs ahead of the reservation, so a rejected trigger never publishes an in-flight marker that
        // a concurrent trigger would read as "a run is going, wait for its frame".
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any(), any());
        verifyNoInteractions(irisProactiveEpisodeRepository);
    }

    @Test
    void alreadyInFlight_refundsTheChargeItMade() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any())).thenReturn(Optional.empty());

        var result = service.prepareTrigger(EX, user, null, null, null, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.courseDisabled()).isFalse();
        // This trigger dispatches nothing; the run it defers to already paid for itself.
        verify(pyrisJobService).refundStruggleCooldown("cool", USER_ID, EX, null);
    }

    @Test
    void episodeRegistrationFails_refundsTheCooldownAndReleasesTheSlot() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any())).thenReturn(Optional.of("tok"));
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("db down"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.prepareTrigger(EX, user, null, new StruggleEpisodeDTO("ep-1", true, List.of()), null, null, null));

        // Nothing reached Pyris, so this run costs nothing upstream and must not spend the student's cooldown.
        verify(pyrisJobService).refundStruggleCooldown("cool", USER_ID, EX, null);
        verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
    }

    @Test
    void reservationThrows_refundsTheChargeItAlreadyMade() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("map down"));

        assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> service.prepareTrigger(EX, user, null, null, null, null, null));

        // Nothing was reserved and nothing will be dispatched, so the charge must not sit out its whole window.
        verify(pyrisJobService).refundStruggleCooldown("cool", USER_ID, EX, null);
    }

    @Test
    void episodeRegistrationFails_releasesTheSharedSlotBeforeHandingBackTheCharge() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any())).thenReturn(Optional.of("tok"));
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("db down"));

        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.prepareTrigger(EX, user, null, new StruggleEpisodeDTO("ep-1", true, List.of()), null, null, null));

        // The marker is shared by every intent and is what a concurrent trigger reads to decide it may wait for
        // someone else's frame, so it has to go first; the charge is keyed per intent and read by nobody else.
        InOrder inOrder = inOrder(pyrisJobService);
        inOrder.verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        inOrder.verify(pyrisJobService).refundStruggleCooldown("cool", USER_ID, EX, null);
    }

    @Test
    void episodeRegistrationFails_stillReleasesTheSlotWhenTheRefundThrows() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any(), any())).thenReturn(Optional.of("tok"));
        when(transactionManager.getTransaction(any())).thenThrow(new IllegalStateException("db down"));
        doThrow(new IllegalStateException("map down")).when(pyrisJobService).refundStruggleCooldown(any(), anyLong(), anyLong(), any());

        // The registration failure is the real error and must survive; a failing refund must not replace it, nor
        // strand the reservation until its TTL.
        assertThatExceptionOfType(IllegalStateException.class)
                .isThrownBy(() -> service.prepareTrigger(EX, user, null, new StruggleEpisodeDTO("ep-1", true, List.of()), null, null, null)).withMessage("db down")
                .satisfies(thrown -> assertThat(thrown.getSuppressed()).singleElement().extracting(Throwable::getMessage).isEqualTo("map down"));

        verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
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
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", "cool", null, null, null, null, null);
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), 1);

        service.sendToPyris(prepared, signal, Map.of());

        verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        // Nothing reached Pyris, so the admission charge must go back rather than block the student for the window.
        verify(pyrisJobService).refundStruggleCooldown("cool", USER_ID, EX, null);
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
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", "cool", "decide", null, null, null, null);
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
        var prepared = new IrisStruggleTriggerService.PreparedTrigger(COURSE, EX, USER_ID, "default", "moderate", "tok", "cool", "confirm_close", null, "progress", null, null);
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
