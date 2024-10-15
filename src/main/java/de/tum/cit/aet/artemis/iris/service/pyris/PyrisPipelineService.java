package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.exception.IrisException;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.status.PyrisStageDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;

/**
 * Service responsible for executing the various Pyris pipelines in a type-safe manner.
 * Uses {@link PyrisConnectorService} to execute the pipelines and {@link PyrisJobService} to manage the jobs.
 */
@Service
@Profile(PROFILE_IRIS)
public class PyrisPipelineService {

    private static final Logger log = LoggerFactory.getLogger(PyrisPipelineService.class);

    private final PyrisConnectorService pyrisConnectorService;

    private final PyrisJobService pyrisJobService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public PyrisPipelineService(PyrisConnectorService pyrisConnectorService, PyrisJobService pyrisJobService) {
        this.pyrisConnectorService = pyrisConnectorService;
        this.pyrisJobService = pyrisJobService;
    }

    /**
     * Executes a pipeline on Pyris, identified by the given name and variant.
     * The pipeline execution is tracked by a unique job token, which is created for each new job automatically.
     * The caller must provide a mapper function to take this job token and produce a {@code PyrisJob} object to be registered.
     * The caller must additionally provide a mapper function to create the concrete DTO type for this pipeline from the base DTO.
     * The status of the pipeline execution is updated via a consumer that accepts a list of stages. This method will
     * call the consumer with the initial stages of the pipeline execution. Later stages will be sent back from Pyris,
     * and need to be handled in the endpoint that receives the status updates.
     * <p>
     *
     * @param name          the name of the pipeline to be executed
     * @param variant       the variant of the pipeline
     * @param jobFunction   a function from job ID to job. Creates a new {@code PyrisJob} which will be registered in Hazelcast
     * @param dtoMapper     a function to create the concrete DTO type for this pipeline from the base execution DTO
     * @param statusUpdater a consumer of stages to send status updates while the pipeline is being prepared
     */
    public void executePipeline(String name, String variant, Function<String, PyrisJob> jobFunction, Function<PyrisPipelineExecutionDTO, Object> dtoMapper,
            Consumer<List<PyrisStageDTO>> statusUpdater) {
        // Define the preparation stages of pipeline execution with their initial states
        // There will be more stages added in Pyris later
        var preparing = new PyrisStageDTO("Preparing", 10, null, null);
        var executing = new PyrisStageDTO("Executing pipeline", 30, null, null);

        // Send initial status update indicating that the preparation stage is in progress
        statusUpdater.accept(List.of(preparing.inProgress(), executing.notStarted()));

        String jobToken = pyrisJobService.registerJob(jobFunction);

        var baseDto = new PyrisPipelineExecutionDTO(new PyrisPipelineExecutionSettingsDTO(jobToken, List.of(), artemisBaseUrl), List.of(preparing.done()));
        var pipelineDto = dtoMapper.apply(baseDto);

        try {
            // Send a status update that preparation is done and pipeline execution is starting
            statusUpdater.accept(List.of(preparing.done(), executing.inProgress()));

            try {
                // Execute the pipeline using the connector service
                pyrisConnectorService.executePipeline(name, variant, pipelineDto);
            }
            catch (PyrisConnectorException | IrisException e) {
                log.error("Failed to execute {} pipeline", name, e);
                statusUpdater.accept(List.of(preparing.done(), executing.error("An internal error occurred")));
            }
        }
        catch (Exception e) {
            log.error("Failed to prepare {} pipeline execution", name, e);
            statusUpdater.accept(List.of(preparing.error("An internal error occurred"), executing.notStarted()));
        }
    }
}
