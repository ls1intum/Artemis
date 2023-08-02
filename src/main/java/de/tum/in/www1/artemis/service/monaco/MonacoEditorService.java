package de.tum.in.www1.artemis.service.monaco;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.BadRequestException;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.monaco.LspConfig;
import de.tum.in.www1.artemis.domain.monaco.LspServerStatus;
import de.tum.in.www1.artemis.domain.participation.Participation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.LspException;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.web.rest.dto.FileMove;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.repository.FileSubmission;

/**
 * Service class of the Monaco editor powering the
 * web-ide.
 */
@Service
public class MonacoEditorService {

    private final Logger log = LoggerFactory.getLogger(MonacoEditorService.class);

    private final ParticipationRepository participationRepository;

    private final LspService lspService;

    public MonacoEditorService(ParticipationRepository participationRepository, LspService lspService) {
        this.participationRepository = participationRepository;
        this.lspService = lspService;
    }

    /**
     * Requests the initialization of a participation to an external monaco server
     *
     * @param participation to initialize
     * @return The configuration parameters of the initialized LSP participation
     */
    public LspConfig initLsp(Participation participation) throws IOException, LspException {
        ProgrammingExerciseParticipation programmingParticipation = this.validationCheck(participation);

        String url = this.lspService.getLspServerUrl();
        String repositoryUrl = programmingParticipation.getRepositoryUrl();

        try {
            HttpResponse response = this.lspService.initLsp(url, repositoryUrl);

            if (response.getStatusLine().getStatusCode() != 200) {
                EntityUtils.consumeQuietly(response.getEntity());
                throw new LspException(response.toString());
            }

            LspConfig responseConfig = new ObjectMapper().readValue(response.getEntity().getContent(), LspConfig.class);
            responseConfig.setServerUrl(new URL(url));

            return responseConfig;
        }
        catch (LspException e) {
            Optional<LspServerStatus> suspectUnhealthy = this.lspService.getLspServersStatus(false).stream().filter(status -> status.getUrl().equals(url)).findFirst();
            suspectUnhealthy.ifPresent(this.lspService::checkLspServerHealth);
            throw e;
        }
    }

    /**
     * Requests the initialization of a dedicated terminal to an external monaco server
     *
     * @param participation the participation used to initialize the terminal with
     * @param serverUrl     Url of the external server to send the initialization request to
     * @return The configuration parameters of the initialized terminal
     */
    public LspConfig initTerminal(Participation participation, String serverUrl) throws IOException {
        ProgrammingExerciseParticipation programmingParticipation = this.validationCheck(participation);
        this.validateServerUrl(serverUrl);

        String repositoryUrl = programmingParticipation.getRepositoryUrl();
        ProgrammingExercise programmingExercise = programmingParticipation.getProgrammingExercise();

        HttpResponse response = this.lspService.initTerminal(serverUrl, repositoryUrl, programmingExercise);

        if (response.getStatusLine().getStatusCode() == 200) {
            LspConfig responseConfig = new ObjectMapper().readValue(response.getEntity().getContent(), LspConfig.class);
            responseConfig.setServerUrl(new URL(serverUrl));

            return responseConfig;
        }
        else if (response.getStatusLine().getStatusCode() == 429) {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new BadRequestAlertException("The server is currently downloading resources for your environment. Please try again in a moment..", "Monaco",
                    "artemisApp.monaco.errors.imageNotReady");
        }
        else {
            EntityUtils.consumeQuietly(response.getEntity());
            throw new LspException("LSP responded with HTTP " + response.getStatusLine().getStatusCode());
        }
    }

    /**
     * Overloaded method retrieving the participation and calling the actual 'forwardFileUpdates' method
     *
     * @param participationId The ID of the participation
     * @param fileUpdates     The list of files updates to forward
     * @param serverUrl       Url of the LSP server to forward the updates to
     */
    public void forwardFileUpdates(long participationId, List<FileSubmission> fileUpdates, String serverUrl) {
        Participation participation = participationRepository.findByIdElseThrow(participationId);
        forwardFileUpdates(participation, fileUpdates, serverUrl);
    }

    /**
     * Forwards a list of file changes to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param fileUpdates   List of FileSubmissions to forward
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileUpdates(Participation participation, List<FileSubmission> fileUpdates, String serverUrl) {
        ProgrammingExerciseParticipation programmingParticipation = this.validationCheck(participation);
        this.validateServerUrl(serverUrl);

        String repositoryUrl = programmingParticipation.getRepositoryUrl();
        try {
            this.lspService.forwardFileUpdates(serverUrl, repositoryUrl, fileUpdates);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding files updates to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Forwards a file renaming to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param fileMove      Entity representing the renaming action
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileRename(Participation participation, FileMove fileMove, String serverUrl) {
        ProgrammingExerciseParticipation programmingParticipation = this.validationCheck(participation);
        this.validateServerUrl(serverUrl);

        String repositoryUrl = programmingParticipation.getRepositoryUrl();
        try {
            this.lspService.forwardFileRename(serverUrl, repositoryUrl, fileMove);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding file rename to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Forwards a file removal to a given external monaco server.
     *
     * @param participation ProgrammingParticipation of the files to be forwarded
     * @param filename      Name of the file that has been removed
     * @param serverUrl     Url of the external server to send the updates to
     */
    public void forwardFileRemoval(Participation participation, String filename, String serverUrl) {
        ProgrammingExerciseParticipation programmingParticipation = this.validationCheck(participation);
        this.validateServerUrl(serverUrl);

        String repositoryUrl = programmingParticipation.getRepositoryUrl();
        try {
            this.lspService.forwardFileRemoval(serverUrl, repositoryUrl, filename);
        }
        catch (IOException e) {
            this.log.warn("Error while forwarding file removal to external server at '{}'. Reason: {}", serverUrl, e.getMessage());
        }
    }

    /**
     * Checks if the participation is a programming exercise participation and returns it.
     *
     * @param participation The participation to check
     * @return The participation as ProgrammingExerciseParticipation
     */
    private ProgrammingExerciseParticipation validationCheck(Participation participation) {
        if (!(participation instanceof ProgrammingExerciseParticipation programmingParticipation)) {
            throw new IllegalArgumentException();
        }

        return programmingParticipation;
    }

    /**
     * Method validating a provided LSP server URL by checking
     * if contained in the list of connected servers. If not,
     * an exception is thrown.
     *
     * @param serverUrl The serverUrl to validate
     */
    private void validateServerUrl(String serverUrl) {
        if (this.lspService.getLspServersUrl().stream().noneMatch(lspServer -> lspServer.equals(serverUrl))) {
            throw new BadRequestException();
        }
    }
}
