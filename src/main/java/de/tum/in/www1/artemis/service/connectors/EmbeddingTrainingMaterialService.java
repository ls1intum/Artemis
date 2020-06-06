package de.tum.in.www1.artemis.service.connectors;

import static de.tum.in.www1.artemis.service.connectors.RemoteArtemisServiceConnector.authenticationHeaderForSecret;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.service.FileService;

@Service
@Profile("automaticText")
public class EmbeddingTrainingMaterialService {

    private final Logger log = LoggerFactory.getLogger(TextSimilarityClusteringService.class);

    // region Request/Response DTOs
    private static class Request {

        public long courseId;

        public String fileName;

        public byte[] fileData;

        public Request(long courseId, String fileName, byte[] fileData) {
            this.courseId = courseId;
            this.fileName = fileName;
            this.fileData = fileData;
        }
    }

    private static class Response {

        public String remotePath;
    }
    // endregion

    @Value("${artemis.automatic-text.material-upload-url}")
    private String API_ENDPOINT;

    @Value("${artemis.automatic-text.secret}")
    private String API_SECRET;

    private FileService fileService = new FileService();

    private RemoteArtemisServiceConnector<EmbeddingTrainingMaterialService.Request, EmbeddingTrainingMaterialService.Response> connector = new RemoteArtemisServiceConnector<>(log,
            EmbeddingTrainingMaterialService.Response.class);

    /**
     * uploads attachment to Athene to be used for the incremental training of the ELMo model
     * @param attachment - the attachment to be uploaded
     * @throws NetworkingError - thrown if the upload fails because of network issue
     */
    public void uploadAttachment(Attachment attachment) throws NetworkingError {
        log.info("Calling Remote Service to upload training material for the embedding component.");
        final long courseId = attachment.getLecture().getCourse().getId();
        final String fileName = attachment.getName();
        byte[] fileData = null;
        try {
            fileData = fileService.getFileForPath(fileService.actualPathForPublicPath(attachment.getLink()));
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        if (fileData != null) {
            final EmbeddingTrainingMaterialService.Request request = new EmbeddingTrainingMaterialService.Request(courseId, fileName, fileData);
            final EmbeddingTrainingMaterialService.Response response = connector.invokeWithRetry(API_ENDPOINT, request, authenticationHeaderForSecret(API_SECRET), 2);
            log.info("File successfully uploaded to " + response.remotePath);
        }
    }
}
