package de.tum.cit.aet.artemis.iris.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.util.List;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisVariantDTO;

/**
 * REST controller for managing the variants Pyris provides.
 */
@Profile(PROFILE_IRIS)
@Lazy
@RestController
@RequestMapping("api/iris/")
public class IrisVariantsResource {

    /**
     * GET variants/{feature}: Retrieve all available variants offered by Pyris for a certain feature
     *
     * @param featureRaw the feature for which to retrieve the variants
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body a List of the variants
     */
    @GetMapping("variants/{feature}")
    @EnforceAtLeastEditor
    public ResponseEntity<List<PyrisVariantDTO>> getAllVariants(@PathVariable("feature") String featureRaw) {
        var variants = List.of(new PyrisVariantDTO("default", "default", "Default Iris behaviour"), new PyrisVariantDTO("advanced", "advanced", "Advanced Iris behaviour"));
        return ResponseEntity.ok(variants);
    }
}
