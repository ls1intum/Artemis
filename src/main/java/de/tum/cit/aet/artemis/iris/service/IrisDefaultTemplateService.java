package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import de.tum.cit.aet.artemis.iris.domain.IrisTemplate;

/**
 * Service that loads default Iris templates from the resources/templates/iris folder.
 */
@Profile(PROFILE_IRIS)
@Service
public class IrisDefaultTemplateService {

    private static final Logger log = LoggerFactory.getLogger(IrisDefaultTemplateService.class);

    private final ResourceLoaderService resourceLoaderService;

    public IrisDefaultTemplateService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Loads the default Iris template with the given file name.
     * For example, "chat.hbs" will load the template from "resources/templates/iris/chat.hbs".
     *
     * @param templateFileName The file name of the template to load.
     * @return The loaded Iris template, or an empty template if an IO error occurred.
     */
    public IrisTemplate load(String templateFileName) {
        Path filePath = Path.of("templates", "iris", templateFileName);
        Resource resource = resourceLoaderService.getResource(filePath);
        try {
            String fileContent = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            return new IrisTemplate(fileContent);
        }
        catch (IOException e) {
            log.error("Error while loading Iris template from file: {}", filePath, e);
            return new IrisTemplate("");
        }
    }

    /**
     * Loads the global template version from the "resources/templates/iris/template-version.txt" file.
     *
     * @return an Optional containing the version loaded from the file, or an empty Optional if there was an error.
     */
    public Optional<Integer> loadGlobalTemplateVersion() {
        Path filePath = Path.of("templates", "iris", "template-version.txt");
        Resource resource = resourceLoaderService.getResource(filePath);
        try {
            String fileContent = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            int version = Integer.parseInt(fileContent.trim());
            return Optional.of(version);
        }
        catch (IOException e) {
            log.error("Error while loading global template version from file: {}", filePath, e);
        }
        catch (NumberFormatException e) {
            log.error("Content of {} was not a parseable int!", filePath, e);
        }
        return Optional.empty();
    }
}
