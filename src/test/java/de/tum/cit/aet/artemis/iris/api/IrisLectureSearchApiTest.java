package de.tum.cit.aet.artemis.iris.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.iris.dto.IrisLectureSnippetDTO;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisLectureSearchResultDTO;

@ExtendWith(MockitoExtension.class)
class IrisLectureSearchApiTest {

    @Mock
    private PyrisConnectorService connector;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IrisAccessContextService accessContextService;

    @InjectMocks
    private IrisLectureSearchApi api;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void maintenanceSearch_restrictsManualAndSystemRunsToAuthorizedCourse(boolean automatic) {
        if (automatic) {
            SecurityUtils.setSystemAuthorizationObject();
        }
        else {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("instructor", null, List.of()));
        }
        var result = new PyrisLectureSearchResultDTO(new PyrisLectureSearchResultDTO.CourseDTO(42L, "Algorithms"), new PyrisLectureSearchResultDTO.LectureDTO(1L, "Graphs"),
                new PyrisLectureSearchResultDTO.LectureUnitDTO(2L, "Unreleased slides", "/slides", 1, "pdf", Map.of(), null), "Shortest paths");
        when(connector.searchLectures(eq("graphs"), eq(2), eq(List.of(42L)), isNull(), any())).thenReturn(List.of(result));
        var before = ZonedDateTime.now();

        assertThat(api.searchLecturesForCourseMaintenance("graphs", 2, 42L)).containsExactly(new IrisLectureSnippetDTO("Graphs", "Unreleased slides", "Shortest paths"));

        var context = ArgumentCaptor.forClass(PyrisAccessContextDTO.class);
        verify(connector).searchLectures(eq("graphs"), eq(2), eq(List.of(42L)), isNull(), context.capture());
        assertThat(context.getValue().courseIds()).containsExactly(42L);
        assertThat(context.getValue().editorCourseIds()).containsExactly(42L);
        assertThat(context.getValue().staffCourseIds()).containsExactly(42L);
        assertThat(context.getValue().taCourseIds()).isEmpty();
        assertThat(context.getValue().studentCourseIds()).isEmpty();
        assertThat(context.getValue().unrestricted()).isFalse();
        assertThat(context.getValue().now().toInstant()).isBetween(before.toInstant(), ZonedDateTime.now().toInstant());
        verifyNoInteractions(userRepository, accessContextService);
    }

    @Test
    void maintenanceSearch_rejectsInvalidCourseBeforeCallingConnector() {
        assertThatThrownBy(() -> api.searchLecturesForCourseMaintenance("graphs", 2, 0L)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(connector, userRepository, accessContextService);
    }

    @Test
    void userSearch_preservesExistingAccessResolution() {
        User user = new User();
        var context = new PyrisAccessContextDTO(List.of(42L, 99L), List.of(42L), List.of(), List.of(99L), List.of(42L), ZonedDateTime.now(), false);
        when(userRepository.getUserWithCourseRolesAndAuthorities()).thenReturn(user);
        when(accessContextService.resolveAccessContext(user)).thenReturn(context);
        when(connector.searchLectures("graphs", 2, List.of(99L), null, context)).thenReturn(List.of());

        assertThat(api.searchLectures("graphs", 2, List.of(99L))).isEmpty();

        verify(userRepository).getUserWithCourseRolesAndAuthorities();
        verify(accessContextService).resolveAccessContext(user);
        verify(connector).searchLectures("graphs", 2, List.of(99L), null, context);
    }
}
