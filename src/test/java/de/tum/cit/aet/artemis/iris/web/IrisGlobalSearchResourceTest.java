package de.tum.cit.aet.artemis.iris.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.UserAiPreferenceService;
import de.tum.cit.aet.artemis.account.test_repository.UserTestRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.globalsearch.api.SearchableEntityPrefetchApi;
import de.tum.cit.aet.artemis.globalsearch.dto.SearchableEntityCandidateDTO;
import de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException;
import de.tum.cit.aet.artemis.iris.service.IrisAccessContextService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisJobService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.GlobalSearchAskRequestDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisAccessContextDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.search.PyrisEntityCandidateDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Isolated Mockito test for {@link IrisGlobalSearchResource#ask}, in particular the entity-candidate
 * prefetch step: a Weaviate failure there must degrade to an empty candidate list rather than fail
 * the whole request, since the lecture-content-only answer could still succeed. Follows the same
 * bare-unit-test pattern as {@code HyperionCodeGenerationResourceTest}.
 */
class IrisGlobalSearchResourceTest {

    @Mock
    private PyrisConnectorService pyrisConnectorService;

    @Mock
    private PyrisJobService pyrisJobService;

    @Mock
    private UserTestRepository userRepository;

    @Mock
    private UserAiPreferenceService userAiPreferenceService;

    @Mock
    private IrisAccessContextService irisAccessContextService;

    @Mock
    private SearchableEntityPrefetchApi searchableEntityPrefetchApi;

    @Mock
    private IrisSettingsService irisSettingsService;

    private IrisGlobalSearchResource resource;

    private User testUser;

    private final Principal principal = () -> "student1";

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        resource = new IrisGlobalSearchResource(pyrisConnectorService, pyrisJobService, userRepository, userAiPreferenceService, irisAccessContextService,
                Optional.of(searchableEntityPrefetchApi), irisSettingsService);

        testUser = new User();
        testUser.setId(1L);
        testUser.setLogin("student1");
        when(userRepository.getUserWithCourseRolesAndAuthorities()).thenReturn(testUser);
        when(userAiPreferenceService.findDecision(1L)).thenReturn(AiSelectionDecision.CLOUD_AI);
        // Unrestricted, matching an unscoped request's "no ceiling to narrow" path through
        // lectureSearchScope — resolveAccessContext never legitimately returns null in production.
        when(irisAccessContextService.resolveAccessContext(testUser)).thenReturn(new PyrisAccessContextDTO(null, null, null, null, null, null, true));
    }

    @Test
    void ask_whenEntityPrefetchSucceeds_forwardsTheCandidates() {
        var candidate = new SearchableEntityCandidateDTO("exercise", 8L, 11L, "Test course", "RNN and LSTM Fundamentals", "A quiz", null, "/courses/11/exercises/8", null, null,
                null, null, null, null, null, 10.0, null, null, "quiz", null, null, null);
        when(searchableEntityPrefetchApi.prefetchCandidates(eq(testUser), anyString(), anyInt(), eq((List<Long>) null), eq(List.of()))).thenReturn(List.of(candidate));

        var requestDTO = new GlobalSearchAskRequestDTO("is there an rnn quiz", 5, UUID.randomUUID());
        ResponseEntity<Void> response = resource.ask(requestDTO, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(pyrisConnectorService).executeGlobalSearchIrisAnswer(eq(requestDTO.query()), eq(requestDTO.limit()), eq(requestDTO.runId().toString()),
                eq(AiSelectionDecision.CLOUD_AI), any(), eq(List.of(PyrisEntityCandidateDTO.of(candidate))), eq((List<Long>) null), eq((List<Long>) null), eq(false));
    }

    @Test
    void ask_whenEntityPrefetchFailsWithWeaviateException_answersFromLectureContentOnlyInsteadOfFailing() {
        when(searchableEntityPrefetchApi.prefetchCandidates(eq(testUser), anyString(), anyInt(), eq((List<Long>) null), eq(List.of())))
                .thenThrow(new WeaviateException("Weaviate is down", new RuntimeException("connection refused")));

        var requestDTO = new GlobalSearchAskRequestDTO("what is backpropagation", 5, UUID.randomUUID());
        ResponseEntity<Void> response = resource.ask(requestDTO, principal);

        // The endpoint still accepts the run and asks Pyris to answer from lecture content alone,
        // instead of surfacing a 500 for a job token that pyrisJobService already registered.
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(pyrisConnectorService).executeGlobalSearchIrisAnswer(eq(requestDTO.query()), eq(requestDTO.limit()), eq(requestDTO.runId().toString()),
                eq(AiSelectionDecision.CLOUD_AI), any(), eq(List.of()), eq((List<Long>) null), eq((List<Long>) null), eq(false));
        verify(pyrisJobService).addGlobalSearchAnswerJob(anyString(), anyString());
    }

    @Test
    void ask_whenNoPrefetchApiIsConfigured_answersFromLectureContentOnly() {
        resource = new IrisGlobalSearchResource(pyrisConnectorService, pyrisJobService, userRepository, userAiPreferenceService, irisAccessContextService, Optional.empty(),
                irisSettingsService);

        var requestDTO = new GlobalSearchAskRequestDTO("what is backpropagation", 5, UUID.randomUUID());
        ResponseEntity<Void> response = resource.ask(requestDTO, principal);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        verify(pyrisConnectorService).executeGlobalSearchIrisAnswer(eq(requestDTO.query()), eq(requestDTO.limit()), eq(requestDTO.runId().toString()),
                eq(AiSelectionDecision.CLOUD_AI), any(), eq(List.of()), eq((List<Long>) null), eq((List<Long>) null), eq(false));
    }
}
