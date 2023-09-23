package de.tum.in.www1.artemis.web.rest.iris;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastEditor;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorException;
import de.tum.in.www1.artemis.service.connectors.iris.IrisConnectorService;
import de.tum.in.www1.artemis.service.connectors.iris.dto.IrisModelDTO;
import de.tum.in.www1.artemis.web.rest.errors.InternalServerErrorException;

/**
 * REST controller for managing the models Pyris provides.
 */
@RestController
@Profile("iris")
@RequestMapping("api/")
public class IrisModelsResource {

    private final IrisConnectorService irisConnectorService;

    public IrisModelsResource(IrisConnectorService irisConnectorService) {
        this.irisConnectorService = irisConnectorService;
    }

    /**
     * GET iris/models: Retrieve all available models offered by Pyris
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a List of the models
     */
    @GetMapping("iris/models")
    @EnforceAtLeastEditor
    public ResponseEntity<List<IrisModelDTO>> getAllModels() {
        try {
            var models = irisConnectorService.getOfferedModels();
            return ResponseEntity.ok(models);
        }
        catch (IrisConnectorException e) {
            throw new InternalServerErrorException("Could not fetch available Iris models");
        }
    }
}
