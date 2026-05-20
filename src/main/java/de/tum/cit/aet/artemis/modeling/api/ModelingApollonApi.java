package de.tum.cit.aet.artemis.modeling.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_APOLLON;

import java.io.IOException;
import java.io.InputStream;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.modeling.config.ModelingEnabled;
import de.tum.cit.aet.artemis.modeling.service.apollon.ApollonConversionService;

/**
 * API for Apollon conversion operations.
 */
@Conditional(ModelingEnabled.class)
@Profile(PROFILE_APOLLON)
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
     * @throws IOException if the conversion fails
     */
    public InputStream convertModel(String model) throws IOException {
        return apollonConversionService.convertModel(model);
    }
}
