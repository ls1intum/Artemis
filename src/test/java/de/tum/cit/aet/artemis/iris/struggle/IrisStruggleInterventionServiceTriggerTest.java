package de.tum.cit.aet.artemis.iris.struggle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
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
import de.tum.cit.aet.artemis.iris.service.session.IrisChatSessionService;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.iris.service.websocket.IrisChatWebsocketService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Plain Mockito unit test for the trigger side of {@link IrisStruggleInterventionService#prepareTrigger}. The three
 * behaviors are the contract: disabled course settings -> no reserve and empty; enabled -> reserve + token + STUDENT
 * role check; overlapping run (single-flight factory returns empty) -> empty.
 */
@ExtendWith(MockitoExtension.class)
class IrisStruggleInterventionServiceTriggerTest {

    @Mock
    private ProgrammingExerciseRepository programmingExerciseRepository;

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
    private UserRepository userRepository;

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
        when(programmingExerciseRepository.findByIdElseThrow(EX)).thenReturn(exercise);
    }

    @Test
    void disabledSettings_doesNotReserveOrEnqueue() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(disabledSettings());

        var result = service.prepareTrigger(EX, user);

        assertThat(result).isEmpty();
        verify(pyrisJobService, never()).addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong());
    }

    @Test
    void enabled_reservesSlotAndReturnsToken() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(eq(COURSE), eq(USER_ID), eq(EX))).thenReturn(Optional.of("tok"));

        var result = service.prepareTrigger(EX, user);

        assertThat(result).isPresent();
        assertThat(result.get().jobToken()).isEqualTo("tok");
        verify(authCheckService).checkHasAtLeastRoleForExerciseElseThrow(eq(Role.STUDENT), eq(exercise), eq(user));
    }

    @Test
    void overlappingTrigger_isSkipped() {
        when(irisSettingsService.getSettingsForCourse(course)).thenReturn(enabledSettings());
        when(pyrisJobService.addStruggleInterventionJobIfNonePending(anyLong(), anyLong(), anyLong())).thenReturn(Optional.empty());

        assertThat(service.prepareTrigger(EX, user)).isEmpty();
    }

    private static IrisCourseSettings enabledSettings() {
        return new IrisCourseSettings(true, null, IrisPipelineVariant.DEFAULT, null);
    }

    private static IrisCourseSettings disabledSettings() {
        return new IrisCourseSettings(false, null, IrisPipelineVariant.DEFAULT, null);
    }
}
