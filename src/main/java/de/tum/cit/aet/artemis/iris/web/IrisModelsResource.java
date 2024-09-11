package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisModelDTO;

/**
 * REST controller for managing the models Pyris provides.
 */
@Profile(PROFILE_IRIS)
@RestController
@RequestMapping("api/")
public class IrisModelsResource {

    private final PyrisConnectorService pyrisConnectorService;

    public IrisModelsResource(PyrisConnectorService pyrisConnectorService) {
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * GET iris/models: Retrieve all available models offered by Pyris
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a List of the models
     */
    @GetMapping("iris/models")
    @EnforceAtLeastEditor
    public ResponseEntity<List<PyrisModelDTO>> getAllModels() {
        try {
            var models = pyrisConnectorService.getOfferedModels();
            return ResponseEntity.ok(models);
        }
        catch (PyrisConnectorException e) {
            throw new InternalServerErrorException("Could not fetch available Iris models");
        }
    }
}
