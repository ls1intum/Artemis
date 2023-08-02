package de.tum.in.www1.artemis.service.monaco;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.AdditionalAnswers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;

@Service
public class LspServiceTest {

    private static final String TEST_PREFIX = "monacoeditorservice";

    private final LspServerStatus UNHEALTHY_LSP = new LspServerStatus("https://unhealthy", false, false, 0, 0);

    private final LspServerStatus HEALTHY_LSP = new LspServerStatus("https://healthy", true, false, 0, 0.1);

    private final LspServerStatus PAUSED_LSP = new LspServerStatus("https://paused", true, true, 0, 0.1);

    private final List<String> lspServerUrls = List.of(HEALTHY_LSP.getUrl(), UNHEALTHY_LSP.getUrl(), PAUSED_LSP.getUrl());

    private final List<LspServerStatus> lspServerStatuses = List.of(HEALTHY_LSP, UNHEALTHY_LSP, PAUSED_LSP);

    private final String REPOSITORY_URL = "https://vcs/repo.git";

    private LspService lspService;

    @Mock
    private HttpClient httpClient;

    @Mock
    private StatusLine statusLine;

    @Mock
    private ParticipationRepository participationRepository;

    private Participation participation;

    private ProgrammingExerciseParticipation programmingExerciseParticipation;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        this.lspService = new LspService(this.lspServerStatuses, httpClient);

        this.participation = new StudentParticipation();
        this.programmingExerciseParticipation = new ProgrammingExerciseStudentParticipation();
        this.programmingExerciseParticipation.setRepositoryUrl(REPOSITORY_URL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(participationRepository);
    }

    @Test
    void initLspTest() throws Exception {
        String repoPath = "/tmp/repo";
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("serverUrl", HEALTHY_LSP.getUrl());
        jsonObject.put("repoPath", repoPath);
        int status = 200;

        var httpResponse = this.generateResponseStub(status, jsonObject.toString());
        when(statusLine.getStatusCode()).thenReturn(status);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(httpResponse);

        HttpResponse result = this.lspService.initLsp(HEALTHY_LSP.getUrl(), REPOSITORY_URL);

        verify(httpClient).execute(any());
        assertThat(result).isEqualTo(httpResponse);
    }

    @Test
    void initTerminalTest() throws Exception {
        String repoPath = "/tmp/repo";
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("serverUrl", HEALTHY_LSP.getUrl());
        jsonObject.put("repoPath", repoPath);
        jsonObject.put("containerId", "123containerId321");
        int status = 200;

        var httpResponse = this.generateResponseStub(status, jsonObject.toString());
        when(statusLine.getStatusCode()).thenReturn(status);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any())).thenReturn(httpResponse);

        HttpResponse result = this.lspService.initTerminal(HEALTHY_LSP.getUrl(), REPOSITORY_URL, programmingExercise);

        verify(httpClient).execute(any());
        assertThat(result).isEqualTo(httpResponse);
    }

    @Test
    void getLspServerUrlTest() {
        LspService lspServiceStub = spy(new LspService(lspServerStatuses, httpClient));
        when(lspServiceStub.checkLspServerHealth(any())).then(AdditionalAnswers.returnsFirstArg());

        String result = lspServiceStub.getLspServerUrl();

        assertThat(result.equals(HEALTHY_LSP.getUrl())).isTrue();
    }

    @Test
    void getLspServersUrlTest() {
        List<String> actualList = this.lspService.getLspServersUrl();
        assertThat(actualList.containsAll(lspServerUrls)).isTrue();
    }

    @Test
    void getLspServersStatusTest() {
        LspService lspServiceStub = spy(new LspService(this.lspServerUrls));
        int expectedListSize = this.lspServerStatuses.size();

        when(lspServiceStub.checkLspServerHealth(any())).thenReturn(HEALTHY_LSP);
        List<LspServerStatus> actualList = lspServiceStub.getLspServersStatus(true);
        assertThat(actualList.size()).isEqualTo(expectedListSize);
    }

    @Test
    void addLspServerTest() {
        String newHealthyUrl = "https://new-healthy";
        LspService lspServiceStub = spy(new LspService(this.lspServerUrls));
        var lspServerStatusBefore = lspServiceStub.getLspServersStatus(false).size();

        when(lspServiceStub.checkLspServerHealth(any())).thenReturn(new LspServerStatus(newHealthyUrl, true, false, 0, 0));
        lspServiceStub.addLspServer(newHealthyUrl);

        assertThat(lspServiceStub.getLspServersStatus(false).size()).isEqualTo(lspServerStatusBefore + 1);
    }

    @Test
    void addLspServerTest_alreadyExisting() {
        var lspServerStatusBefore = this.lspService.getLspServersStatus(false);
        assertThatExceptionOfType(LspException.class).isThrownBy(() -> this.lspService.addLspServer(HEALTHY_LSP.getUrl()));
        assertThat(this.lspService.getLspServersStatus(false).size()).isEqualTo(lspServerStatusBefore.size());
    }

    @Test
    void pauseLspServer() {
        boolean pausedState = this.lspService.pauseLspServer(HEALTHY_LSP.getUrl());
        boolean unpausedState = this.lspService.pauseLspServer(PAUSED_LSP.getUrl());

        List<LspServerStatus> statuses = this.lspService.getLspServersStatus(false);

        assertThat(pausedState).isTrue();
        assertThat(statuses.stream().filter(entry -> entry.getUrl().equals(HEALTHY_LSP.getUrl())).findFirst().get().isPaused()).isTrue();
        assertThat(unpausedState).isFalse();
        assertThat(statuses.stream().filter(entry -> entry.getUrl().equals(PAUSED_LSP.getUrl())).findFirst().get().isPaused()).isFalse();
    }

    @Test
    void checkLspServerHealth() throws Exception {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("url", HEALTHY_LSP.getUrl());
        int status = 200;

        var httpResponse = this.generateResponseStub(status, jsonObject.toString());
        when(statusLine.getStatusCode()).thenReturn(status);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);

        LspServerStatus result = this.lspService.checkLspServerHealth(HEALTHY_LSP);

        assertThat(result.isHealthy()).isTrue();
    }

    @Test
    void checkLspServerHealth_fail() throws Exception {
        int status = 500;

        var httpResponse = this.generateResponseStub(status, "");
        when(statusLine.getStatusCode()).thenReturn(status);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);

        LspServerStatus result = this.lspService.checkLspServerHealth(HEALTHY_LSP);

        assertThat(result.isHealthy()).isFalse();
    }

    private HttpResponse generateResponseStub(int status, String content) throws Exception {
        var statusLineStub = mock(StatusLine.class);
        var httpEntityStub = mock(HttpEntity.class);
        var httpResponseStub = mock(HttpResponse.class);

        InputStream contentStream = new ByteArrayInputStream(content.getBytes());
        when(httpResponseStub.getStatusLine()).thenReturn(statusLineStub);
        when(httpResponseStub.getEntity()).thenReturn(httpEntityStub);
        when(httpEntityStub.getContent()).thenReturn(contentStream);
        when(statusLineStub.getStatusCode()).thenReturn(status);

        return httpResponseStub;
    }
}
