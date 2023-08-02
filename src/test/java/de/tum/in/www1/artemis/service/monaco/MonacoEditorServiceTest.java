package de.tum.in.www1.artemis.service.monaco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.BadRequestException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.monaco.LspConfig;
import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.*;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

@Service
public class MonacoEditorServiceTest {

    private static final String TEST_PREFIX = "monacoeditorservice";

    private final LspServerStatus UNHEALTHY_LSP = new LspServerStatus("https://unhealthy", false, false, 0, 0);

    private final LspServerStatus HEALTHY_LSP = new LspServerStatus("https://healthy", true, false, 0, 0.1);

    private final LspServerStatus PAUSED_LSP = new LspServerStatus("https://paused", true, true, 0, 0.1);

    private final String REPOSITORY_URL = "https://vcs/repo.git";

    private MonacoEditorService monacoEditorService;

    @Mock
    private LspService lspService;

    @Mock
    private ParticipationRepository participationRepository;

    private Participation participation;

    private ProgrammingExerciseParticipation programmingExerciseParticipation;

    private AutoCloseable closeable;

    @BeforeEach
    void init() {
        closeable = MockitoAnnotations.openMocks(this);
        monacoEditorService = new MonacoEditorService(participationRepository, lspService);

        when(lspService.getLspServersUrl()).thenReturn(List.of(HEALTHY_LSP.getUrl(), UNHEALTHY_LSP.getUrl(), PAUSED_LSP.getUrl()));

        this.participation = new StudentParticipation();
        this.programmingExerciseParticipation = new ProgrammingExerciseStudentParticipation();
        this.programmingExerciseParticipation.setRepositoryUrl(REPOSITORY_URL);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        reset(lspService, participationRepository);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initLspTest() throws Exception {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("serverUrl", HEALTHY_LSP.getUrl());
        jsonObject.put("repoPath", REPOSITORY_URL);

        var httpResponseStub = this.generateResponseStub(200, jsonObject.toString());
        when(lspService.initLsp(any(), any())).thenReturn(httpResponseStub);
        when(lspService.getLspServerUrl()).thenReturn(HEALTHY_LSP.getUrl());

        LspConfig lspConfig = monacoEditorService.initLsp((Participation) programmingExerciseParticipation);
        assertThat(lspConfig.getServerUrl().toString()).isEqualTo(HEALTHY_LSP.getUrl());
        assertThat(lspConfig.getRepoPath()).isEqualTo(REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initLspTest_fail() throws Exception {
        var httpResponseStub = this.generateResponseStub(500, "");
        when(lspService.initLsp(any(), any())).thenReturn(httpResponseStub);
        when(lspService.getLspServerUrl()).thenReturn(HEALTHY_LSP.getUrl());
        when(lspService.getLspServersStatus(anyBoolean())).thenReturn(List.of(HEALTHY_LSP, UNHEALTHY_LSP));

        assertThatExceptionOfType(LspException.class).isThrownBy(() -> monacoEditorService.initLsp((Participation) programmingExerciseParticipation));

        verify(this.lspService).checkLspServerHealth(eq(HEALTHY_LSP));
        verify(this.lspService, never()).checkLspServerHealth(eq(UNHEALTHY_LSP));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initLspTest_WrongParticipation() throws Exception {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> monacoEditorService.initLsp(this.participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTerminalTest() throws Exception {
        JSONObject jsonObject = new JSONObject();

        jsonObject.put("serverUrl", HEALTHY_LSP.getUrl());
        jsonObject.put("repoPath", REPOSITORY_URL);
        jsonObject.put("containerId", "123containerid456");

        var httpResponseStub = this.generateResponseStub(200, jsonObject.toString());
        when(lspService.initTerminal(any(), any(), any())).thenReturn(httpResponseStub);

        LspConfig lspConfig = monacoEditorService.initTerminal((Participation) programmingExerciseParticipation, HEALTHY_LSP.getUrl());
        assertThat(lspConfig.getServerUrl().toString()).isEqualTo(HEALTHY_LSP.getUrl());
        assertThat(lspConfig.getRepoPath()).isEqualTo(REPOSITORY_URL);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTerminalTest_unavailable() throws Exception {
        var httpResponseStub = this.generateResponseStub(429, "");
        when(lspService.initTerminal(any(), any(), any())).thenReturn(httpResponseStub);

        assertThatExceptionOfType(BadRequestAlertException.class)
                .isThrownBy(() -> monacoEditorService.initTerminal((Participation) programmingExerciseParticipation, HEALTHY_LSP.getUrl()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTerminalTest_fail() throws Exception {
        var httpResponseStub = this.generateResponseStub(500, "");
        when(lspService.initTerminal(any(), any(), any())).thenReturn(httpResponseStub);

        assertThatExceptionOfType(LspException.class).isThrownBy(() -> monacoEditorService.initTerminal((Participation) programmingExerciseParticipation, HEALTHY_LSP.getUrl()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTerminalTest_WrongParticipation() {
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> monacoEditorService.initTerminal(this.participation, HEALTHY_LSP.getUrl()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void initTerminalTest_InvalidServer() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> monacoEditorService.initTerminal((Participation) programmingExerciseParticipation, "https://invalid"));
    }

    @Test
    void forwardFileUpdatesTest() throws Exception {
        FileSubmission fileSubmission = new FileSubmission();
        fileSubmission.setFileName("file");
        fileSubmission.setFileContent("content");
        List<FileSubmission> updates = List.of(fileSubmission);

        when(this.participationRepository.findByIdElseThrow(1L)).thenReturn((Participation) programmingExerciseParticipation);

        this.monacoEditorService.forwardFileUpdates(1L, updates, HEALTHY_LSP.getUrl());

        verify(participationRepository).findByIdElseThrow(1L);
    }

    @Test
    void forwardFileRenameTest() {
        FileMove fileMove = new FileMove("/src/current", "/src/new");
        this.monacoEditorService.forwardFileRename((Participation) programmingExerciseParticipation, fileMove, HEALTHY_LSP.getUrl());
    }

    @Test
    void forwardFileRemovalTest() {
        this.monacoEditorService.forwardFileRemoval((Participation) programmingExerciseParticipation, "/src/filetoremove", HEALTHY_LSP.getUrl());
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
