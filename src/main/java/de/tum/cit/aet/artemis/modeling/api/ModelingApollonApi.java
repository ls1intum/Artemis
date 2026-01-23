package de.tum.cit.aet.artemis.modeling.api;

import java.io.InputStream;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.modeling.config.ApollonEnabled;
import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.service.apollon.ApollonConversionService;

/**
 * API for Apollon conversion operations.
 */
@Conditional({ ModelingEnabled.class, ApollonEnabled.class })
@Controller
@Lazy
public class ModelingApollonApi extends AbstractModelingApi {

    private final ApollonConversionService apollonConversionService;

    public ModelingApollonApi(ApollonConversionService apollonConversionService) {
        this.apollonConversionService = apollonConversionService;
    }

    /**
     * Converts a modeling submission model to PDF.
     *
     * @param model the model JSON string to convert
     * @return an InputStream containing the PDF data
     */
    public InputStream convertModel(String model) {
        return apollonConversionService.convertModel(model);
    }
}
