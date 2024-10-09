package de.tum.in.www1.artemis.web.rest.iris;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettingsType;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorException;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisVariantDTO;

/**
 * REST controller for managing the variants Pyris provides.
 */
@Profile("iris")
@RestController
@RequestMapping("api/")
public class IrisVariantsResource {

    private static final Logger log = LoggerFactory.getLogger(IrisVariantsResource.class);

    private final PyrisConnectorService pyrisConnectorService;

    public IrisVariantsResource(PyrisConnectorService pyrisConnectorService) {
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * GET iris/variants/{feature}: Retrieve all available variants offered by Pyris for a certain feature
     *
     * @param featureRaw the feature for which to retrieve the variants
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a List of the variants
     */
    @GetMapping("iris/variants/{feature}")
    @EnforceAtLeastEditor
    public ResponseEntity<List<PyrisVariantDTO>> getAllVariants(@PathVariable("feature") String featureRaw) {
        var feature = IrisSubSettingsType.valueOf(featureRaw.toUpperCase().replace("-", "_"));
        try {
            var variants = pyrisConnectorService.getOfferedVariants(feature);
            return ResponseEntity.ok(variants);
        }
        catch (PyrisConnectorException e) {
            log.error("Could not fetch available variants for feature {}", feature, e);
            throw new InternalServerErrorException("Could not fetch available variants for feature " + feature);
        }
    }
}
