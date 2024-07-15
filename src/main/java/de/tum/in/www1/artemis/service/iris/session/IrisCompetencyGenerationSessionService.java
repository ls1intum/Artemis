package de.tum.in.www1.artemis.service.iris.session;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.competency.Competency;
import de.tum.in.www1.artemis.domain.competency.CompetencyTaxonomy;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisJobService;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisPipelineService;
import de.tum.in.www1.artemis.service.connectors.pyris.dto.pipeline.PyrisCompetencyExtractionPipelineExecutionDTO;
import de.tum.in.www1.artemis.service.connectors.pyris.job.CompetencyExtractionJob;
import de.tum.in.www1.artemis.service.iris.websocket.IrisWebsocketService;

/**
 * Service to handle the Competency generation subsytem of Iris.
 */
@Service
@Profile("iris")
public class IrisCompetencyGenerationSessionService {

    private static final Logger log = LoggerFactory.getLogger(IrisCompetencyGenerationSessionService.class);

    private final PyrisPipelineService pyrisPipelineService;

    private final IrisWebsocketService irisWebsocketService;

    private final PyrisJobService pyrisJobService;

    public IrisCompetencyGenerationSessionService(PyrisPipelineService pyrisPipelineService, IrisWebsocketService irisWebsocketService, PyrisJobService pyrisJobService) {
        this.pyrisPipelineService = pyrisPipelineService;
        this.irisWebsocketService = irisWebsocketService;
        this.pyrisJobService = pyrisJobService;
    }

    public void extractCompetencies(Course course, User user, String courseDescription) {
        pyrisPipelineService.executePipeline("competency-extraction", "default",
                pyrisJobService.createTokenForJob(token -> new CompetencyExtractionJob(token, course.getId(), user.getId())),
                executionDto -> new PyrisCompetencyExtractionPipelineExecutionDTO(executionDto, courseDescription, CompetencyTaxonomy.values()),
                stages -> irisWebsocketService.sendCompetencies(user, course.getId(), stages));
    }

    public void handleStatusUpdate() {
        // TODO: implement
    }

    private List<Competency> toCompetencies(JsonNode content) {
        List<Competency> competencies = new ArrayList<>();
        for (JsonNode node : content.get("competencies")) {
            try {
                Competency competency = new Competency();
                competency.setTitle(node.required("title").asText());

                // skip competency if IRIS only replied with a title containing the special response "!done!"
                if (node.get("description") == null && node.get("title").asText().equals("!done!")) {
                    log.info("Received special response \"!done!\", skipping parsing of competency.");
                    continue;
                }
                competency.setDescription(node.required("description").asText());
                competency.setTaxonomy(CompetencyTaxonomy.valueOf(node.required("taxonomy").asText()));

                competencies.add(competency);
            }
            catch (IllegalArgumentException e) {
                log.error("Missing fields, could not parse Competency: " + node.toPrettyString(), e);
            }
        }
        return competencies;
    }
}
