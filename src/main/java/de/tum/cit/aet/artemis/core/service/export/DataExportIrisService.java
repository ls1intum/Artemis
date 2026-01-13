package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import de.tum.cit.aet.artemis.core.dto.export.IrisChatSessionExportDTO;
import de.tum.cit.aet.artemis.core.dto.export.IrisMessageExportDTO;
import de.tum.cit.aet.artemis.iris.api.IrisDataExportApi;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;

/**
 * Service for creating Iris (AI tutor) data exports for GDPR compliance.
 * <p>
 * This service exports all AI tutor chat sessions and messages for a user,
 * including both the user's questions and the AI's responses.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportIrisService {

    private static final Logger log = LoggerFactory.getLogger(DataExportIrisService.class);

    private final Optional<IrisDataExportApi> irisDataExportApi;

    private final ObjectMapper objectMapper;

    public DataExportIrisService(Optional<IrisDataExportApi> irisDataExportApi) {
        this.irisDataExportApi = irisDataExportApi;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Creates the Iris data export containing all AI tutor chat sessions and messages.
     * If Iris is not enabled, this method does nothing.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createIrisExport(long userId, Path workingDirectory) throws IOException {
        if (irisDataExportApi.isEmpty()) {
            log.debug("Iris is not enabled, skipping Iris data export for user {}", userId);
            return;
        }
        var chatSessions = irisDataExportApi.get().findAllChatSessionsWithMessagesByUserId(userId);
        createIrisExportFile(workingDirectory, chatSessions);
    }

    /**
     * Creates a JSON file containing all Iris chat sessions and messages.
     *
     * @param workingDirectory the directory where the export file should be created
     * @param chatSessions     the list of chat sessions to be exported
     * @throws IOException if the file cannot be created
     */
    private void createIrisExportFile(Path workingDirectory, List<IrisChatSession> chatSessions) throws IOException {
        if (chatSessions == null || chatSessions.isEmpty()) {
            return;
        }

        List<IrisChatSessionExportDTO> exportDTOs = chatSessions.stream().map(this::convertToExportDTO).toList();

        Path outputFile = workingDirectory.resolve("iris_chat_sessions.json");
        objectMapper.writeValue(outputFile.toFile(), exportDTOs);
    }

    /**
     * Converts an IrisChatSession to an export DTO.
     *
     * @param session the chat session to convert
     * @return the export DTO
     */
    private IrisChatSessionExportDTO convertToExportDTO(IrisChatSession session) {
        List<IrisMessageExportDTO> messages = session.getMessages().stream()
                .map(message -> new IrisMessageExportDTO(message.getId(), message.getSentAt(), message.getSender() != null ? message.getSender().name() : null,
                        message.getContent().stream().map(IrisMessageContent::getContentAsString).filter(Objects::nonNull).reduce((a, b) -> a + "\n" + b).orElse(null),
                        message.getHelpful()))
                .toList();

        return new IrisChatSessionExportDTO(session.getId(), session.getUserId(), session.getCreationDate(), messages);
    }
}
