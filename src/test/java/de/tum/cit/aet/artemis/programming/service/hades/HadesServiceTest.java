package de.tum.cit.aet.artemis.programming.service.hades;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import de.tum.cit.aet.artemis.core.config.ProgrammingLanguageConfiguration;
import de.tum.cit.aet.artemis.localci.service.ci.StatelessCIService.BuildStatus;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.exception.ContinuousIntegrationException;
import de.tum.cit.aet.artemis.programming.service.hades.dto.BuildTriggerRequestDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesBuildJobDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.HadesBuildResponseDTO;
import de.tum.cit.aet.artemis.programming.service.hades.dto.RepositoryDTO;

@ExtendWith(MockitoExtension.class)
class HadesServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ProgrammingLanguageConfiguration programmingLanguageConfiguration;

    private HadesService hadesService;

    @BeforeEach
    void setUp() {
        hadesService = new HadesService(restTemplate, programmingLanguageConfiguration);
        ReflectionTestUtils.setField(hadesService, "hadesServerUrl", "http://hades:8080");
        ReflectionTestUtils.setField(hadesService, "hadesAuthKey", "test-key");
        ReflectionTestUtils.setField(hadesService, "cloneImage", "clone-image:latest");
        ReflectionTestUtils.setField(hadesService, "username", "testuser");
        ReflectionTestUtils.setField(hadesService, "password", "testpassword");
        ReflectionTestUtils.setField(hadesService, "resultParserImage", "result-parser:latest");
        ReflectionTestUtils.setField(hadesService, "adapterEndPoint", "http://adapter:9090/results");
    }

    @Test
    void getBuildStatus_alwaysReturnsInactive() {
        var participation = mock(ProgrammingExerciseParticipation.class);
        assertThat(hadesService.getBuildStatus(participation)).isEqualTo(BuildStatus.INACTIVE);
    }

    @Test
    void getPlanKey_returnsEmptyString() throws ContinuousIntegrationException {
        assertThat(hadesService.getPlanKey("anything")).isEqualTo("");
    }

    @Test
    void health_whenPong_returnsHealthy() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok("{\"message\":\"pong\"}"));

        var health = hadesService.health();

        assertThat(health.isUp()).isTrue();
        assertThat(health.additionalInfo()).containsEntry("url", "http://hades:8080");
    }

    @Test
    void health_whenNonPongMessage_returnsUnhealthy() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenReturn(ResponseEntity.ok("{\"message\":\"not-pong\"}"));

        assertThat(hadesService.health().isUp()).isFalse();
    }

    @Test
    void health_whenException_returnsUnhealthy() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class))).thenThrow(new RuntimeException("connection refused"));

        assertThat(hadesService.health().isUp()).isFalse();
    }

    @Test
    void build_success_returnsUUID() throws ContinuousIntegrationException {
        var dto = buildTriggerRequest(Map.of());
        var expectedUuid = UUID.randomUUID();

        when(programmingLanguageConfiguration.getImage(any(), any())).thenReturn("java:21");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(HadesBuildResponseDTO.class)))
                .thenReturn(ResponseEntity.ok(new HadesBuildResponseDTO(expectedUuid.toString(), "Build queued")));

        assertThat(hadesService.build(dto)).isEqualTo(expectedUuid);
    }

    @Test
    void build_withResultIngestDirectoryOverride_usesProvidedDirectory() throws ContinuousIntegrationException {
        var dto = buildTriggerRequest(Map.of("resultIngestDirectory", "/shared/target/surefire-reports"));
        var expectedUuid = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<HadesBuildJobDTO>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(programmingLanguageConfiguration.getImage(any(), any())).thenReturn("java:21");
        when(restTemplate.postForEntity(anyString(), requestCaptor.capture(), eq(HadesBuildResponseDTO.class)))
                .thenReturn(ResponseEntity.ok(new HadesBuildResponseDTO(expectedUuid.toString(), "Build queued")));

        hadesService.build(dto);

        var parseResultStep = requestCaptor.getValue().getBody().steps().stream().filter(step -> "Parse Result".equals(step.name())).findFirst().orElseThrow();
        assertThat(parseResultStep.metadata()).containsEntry("INGEST_DIR", "/shared/target/surefire-reports");
    }

    @Test
    void build_withoutProjectType_keepsDefaultGradleIngestDir() throws ContinuousIntegrationException {
        var dto = buildTriggerRequest(Map.of());
        var expectedUuid = UUID.randomUUID();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<HadesBuildJobDTO>> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(programmingLanguageConfiguration.getImage(any(), any())).thenReturn("java:21");
        when(restTemplate.postForEntity(anyString(), requestCaptor.capture(), eq(HadesBuildResponseDTO.class)))
                .thenReturn(ResponseEntity.ok(new HadesBuildResponseDTO(expectedUuid.toString(), "Build queued")));

        hadesService.build(dto);

        var parseResultStep = requestCaptor.getValue().getBody().steps().stream().filter(step -> "Parse Result".equals(step.name())).findFirst().orElseThrow();
        assertThat(parseResultStep.metadata()).containsEntry("INGEST_DIR", "/shared/build/test-results/test");
    }

    @Test
    void build_whenRestTemplateThrowsOnErrorStatus_wrapsWithStatusAndBody() {
        var dto = buildTriggerRequest(Map.of());

        when(programmingLanguageConfiguration.getImage(any(), any())).thenReturn("java:21");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(HadesBuildResponseDTO.class)))
                .thenThrow(HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", null, "boom".getBytes(), null));

        assertThatExceptionOfType(ContinuousIntegrationException.class).isThrownBy(() -> hadesService.build(dto)).withCauseInstanceOf(ContinuousIntegrationException.class)
                .satisfies(e -> assertThat(e.getCause()).hasMessageContaining("500").hasMessageContaining("boom"));
    }

    @Test
    void build_nullResponseBody_throwsException() {
        var dto = buildTriggerRequest(Map.of());

        when(programmingLanguageConfiguration.getImage(any(), any())).thenReturn("java:21");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(HadesBuildResponseDTO.class))).thenReturn(ResponseEntity.ok((HadesBuildResponseDTO) null));

        assertThatExceptionOfType(ContinuousIntegrationException.class).isThrownBy(() -> hadesService.build(dto));
    }

    private BuildTriggerRequestDTO buildTriggerRequest(Map<String, String> additionalProperties) {
        return new BuildTriggerRequestDTO(1L, 2L, new RepositoryDTO("http://example.com/exercise.git", "abc123", null, null),
                new RepositoryDTO("http://example.com/test.git", "def456", null, null), List.of(), "mvn test", ScriptType.SHELL, "JAVA", additionalProperties);
    }
}
