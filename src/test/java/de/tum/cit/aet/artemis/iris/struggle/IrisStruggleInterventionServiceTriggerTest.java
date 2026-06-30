package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisCourseSettings;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisPipelineVariant;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.service.IrisMessageService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisDTOService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisPipelineService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.struggle.PyrisStruggleSignalDTO;
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;

/**
 * Plain Mockito unit test for the trigger side of {@link IrisStruggleInterventionService#prepareTrigger}. The three
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

    private IrisStruggleInterventionService service;

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
        service = new IrisStruggleInterventionService(programmingExerciseRepository, authCheckService, irisSettingsService, irisChatSessionRepository, pyrisDTOService,
                pyrisPipelineService, pyrisJobService, userRepository, irisChatSessionService, irisMessageService, irisChatWebsocketService);
        lenient().when(programmingExerciseRepository.findByIdElseThrow(EX)).thenReturn(exercise);
    }

    @Test
    void disabledSettings_doesNotReserveOrEnqueue() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(disabledSettings());

        var result = service.prepareTrigger(EX, user, null, null, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.courseDisabled()).isTrue();   // Iris disabled => course-off for proactive purposes
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void proactiveDisabled_marksCourseDisabled() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(proactiveOffSettings());

        var result = service.prepareTrigger(EX, user, null, null, null, null);

        assertThat(result.accepted()).isFalse();
        assertThat(result.courseDisabled()).isTrue();
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any());
    }

    @Test
    void enabled_reservesSlotAndReturnsToken() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX), any(), any(), any(), any())).thenReturn(Optional.of("tok"));

        var result = service.prepareTrigger(EX, user, null, null, null, null);

        assertThat(result.accepted()).isTrue();
        assertThat(result.trigger().jobToken()).isEqualTo("tok");
        verify(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(eq(Role.STUDENT), eq(exercise), eq(user));
    }

    @Test
    void overlappingTrigger_isSkipped() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong(), any(), any(), any(), any())).thenReturn(Optional.empty());

        var skipped = service.prepareTrigger(EX, user, null, null, null, null);

        assertThat(skipped.accepted()).isFalse();
        assertThat(skipped.courseDisabled()).isFalse();  // in-flight, NOT course-off
    }

    @Test
    void sendToPyris_userOptedOut_skipsEgressAndReleasesSlot() {
        // The user reloaded on the async thread is no longer opted into LLM usage (aiSelectionDecision == null) -
        // sendToPyris must bail before any Pyris egress and release the reserved single-flight slot.
        when(userRepository.findByIdElseThrow(USER_ID)).thenReturn(user);
        var prepared = new IrisStruggleInterventionService.PreparedTrigger(COURSE, EX, USER_ID, "default", "tok", null, null, null, null);
        var signal = new PyrisStruggleSignalDTO(new PyrisStruggleSignalDTO.AlertDTO(1, "FM", List.of("FM"), 0.7, "armed", false, false), List.of(), List.of(), 1);

        service.sendToPyris(prepared, signal, Map.of());

        verify(pyrisJobService).releaseStruggleInFlightJob("tok", USER_ID, EX);
        verifyNoInteractions(pyrisPipelineService);
    }

    private static IrisCourseSettings enabledSettings() {
        return new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null, true);   // Iris + proactive ON
    }

    private static IrisCourseSettings disabledSettings() {
        return new IrisCourseSettings(false, null, IrisPipelineVariant.DEFAULT, null, false);  // Iris OFF
    }

    private static IrisCourseSettings proactiveOffSettings() {
        return new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null, false);   // Iris ON, proactive OFF
    }
}
