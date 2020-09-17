package de.tum.in.www1.artemis.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.in.www1.artemis.domain.StaticCodeAnalysisConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;

/**
 * Reads static code analysis configurations from json files at application start-up and provides a Bean with
 * programming language specific default categories.
 */
@Configuration
public class StaticCodeAnalysisConfigurer {

    private final Logger log = LoggerFactory.getLogger(StaticCodeAnalysisConfigurer.class);

    private final ResourceLoader resourceLoader;

    private final Map<ProgrammingLanguage, StaticCodeAnalysisConfiguration> languageToConfiguration = new HashMap<>();

    public StaticCodeAnalysisConfigurer(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @PostConstruct
    private void init() {
        ObjectMapper objectMapper = new ObjectMapper();
        String configurationsPath = "classpath:templates/staticCodeAnalysis/*.*";
        Resource[] jsonConfigurations;

        try {
            jsonConfigurations = ResourcePatternUtils.getResourcePatternResolver(resourceLoader).getResources(configurationsPath);
        }
        catch (IOException e) {
            log.debug("Could not load static code analysis configuration resources: " + e.getMessage());
            return;
        }

        for (var jsonConfiguration : jsonConfigurations) {
            Optional<ProgrammingLanguage> optionalLanguage = getProgrammingLanguageFromFileName(jsonConfiguration.getFilename());
            if (optionalLanguage.isEmpty()) {
                log.debug("Could not determine programming language for file name " + jsonConfiguration.getFilename());
                continue;
            }

            // Catch possible errors here as well so that configurations for other programming languages can still be loaded
            try {
                StaticCodeAnalysisConfiguration configuration = objectMapper.readValue(jsonConfiguration.getFile(), StaticCodeAnalysisConfiguration.class);
                languageToConfiguration.put(optionalLanguage.get(), configuration);
            }
            catch (IOException e) {
                log.debug("Could not deserialize static code analysis configuration " + jsonConfiguration.getFilename() + e);
            }
        }
        log.debug("Successfully initialized static code analysis configuration");
    }

    @Bean(name = "staticCodeAnalysisConfiguration")
    public Map<ProgrammingLanguage, StaticCodeAnalysisConfiguration> staticCodeAnalysisConfiguration() {
        return languageToConfiguration;
    }

    private Optional<ProgrammingLanguage> getProgrammingLanguageFromFileName(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return Optional.empty();
        }

        for (var language : ProgrammingLanguage.values()) {
            if (fileName.toLowerCase().contains(language.toString().toLowerCase())) {
                return Optional.of(language);
            }
        }
        return Optional.empty();
    }
}
