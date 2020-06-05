package de.tum.in.www1.artemis.service.scheduled;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.exception.NetworkingError;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.connectors.EmbeddingTrainingMaterialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;

import static java.time.Instant.now;

@Service
@Profile("automaticText")
public class EmbeddingTrainingMaterialScheduleService {

    private final Logger log = LoggerFactory.getLogger(TextClusteringScheduleService.class);

    private final Map<Long, ScheduledFuture> scheduledClusteringTasks = new HashMap<>();

    private final EmbeddingTrainingMaterialService embeddingTrainingMaterialService;

    private final Environment env;

    private final TaskScheduler scheduler;

    public EmbeddingTrainingMaterialScheduleService( EmbeddingTrainingMaterialService embeddingTrainingMaterialService,
                                         @Qualifier("taskScheduler") TaskScheduler scheduler, Environment env) {
        this.embeddingTrainingMaterialService = embeddingTrainingMaterialService;
        this.scheduler = scheduler;
        this.env = env;
    }


    public void scheduleMaterialUploadForNow(Attachment attachment) {
        // TODO: sanity checks.
        scheduler.schedule(trainingMaterialUploadRunnable(attachment), now());
    }

    @NotNull
    private Runnable trainingMaterialUploadRunnable(Attachment attachment) {
        return () -> {
            SecurityUtils.setAuthorizationObject();
            try {
                embeddingTrainingMaterialService.uploadAttachment(attachment);
            } catch (NetworkingError networkingError) {
                networkingError.printStackTrace();
            }
        };
    }


    public void cancelScheduledupload(Attachment attachment) {
        final ScheduledFuture future = scheduledClusteringTasks.get(attachment.getId());
        if (future != null) {
            future.cancel(false);
            scheduledClusteringTasks.remove(attachment.getId());
        }
    }

}
