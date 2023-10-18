package de.tum.in.www1.artemis.service.iris;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.domain.iris.IrisTemplate;
import de.tum.in.www1.artemis.service.ResourceLoaderService;

/**
 * Constants for the Iris subsystem.
 */
@Component
public final class IrisDefaultTemplateService {

    private final Logger log = LoggerFactory.getLogger(IrisDefaultTemplateService.class);

    private final ResourceLoaderService resourceLoaderService;

    public IrisDefaultTemplateService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    public IrisTemplate load(String templateFileName) {
        Path filePath = Path.of("templates", "iris", templateFileName);
        Resource resource = resourceLoaderService.getResource(filePath);
        try {
            String fileContent = IOUtils.toString(resource.getInputStream(), StandardCharsets.UTF_8);
            return new IrisTemplate(fileContent);
        }
        catch (Exception e) {
            log.error("Error while loading Iris template from file: {}", filePath, e);
            return new IrisTemplate("");
        }
    }
}
