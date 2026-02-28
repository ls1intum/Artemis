package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class PlantUmlService {

    private static final Logger log = LoggerFactory.getLogger(PlantUmlService.class);

    private static final String DARK_THEME_FILE_NAME = "puml-theme-artemisdark.puml";

    private static final String LIGHT_THEME_FILE_NAME = "puml-theme-artemislight.puml";

    private final ResourceLoaderService resourceLoaderService;

    private String darkThemeContent;

    private String lightThemeContent;

    public PlantUmlService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Loads theme content from resources when the service is initialized.
     * EventListener cannot be used here, as the bean is lazy
     * <a href="https://docs.spring.io/spring-framework/reference/core/beans/context-introduction.html#context-functionality-events-annotation">Spring Docs</a>
     */
    @PostConstruct
    public void applicationReady() {
        darkThemeContent = loadThemeContent(DARK_THEME_FILE_NAME);
        lightThemeContent = loadThemeContent(LIGHT_THEME_FILE_NAME);
    }

    private String loadThemeContent(String fileName) {
        final var themeResource = resourceLoaderService.getResource(Path.of("puml", fileName));
        try (var inputStream = themeResource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        catch (IOException e) {
            log.error("Unable to load PlantUML theme: {}", fileName, e);
            throw new RuntimeException("Unable to load PlantUML theme: " + fileName, e); // NOPMD
        }
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return The generated PNG as a byte array
     * @throws IOException if generateImage can't create the PNG
     */
    @Cacheable(value = "plantUmlPng", unless = "#result == null || #result.length == 0")
    public byte[] generatePng(final String plantUml, final boolean useDarkTheme) throws IOException {
        var input = validateInputAndApplyTheme(plantUml, useDarkTheme);
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(input);
            reader.outputImage(bos, new FileFormatOption(FileFormat.PNG));
            return bos.toByteArray();
        }
    }

    /**
     * Generate SVG diagram for given PlantUML commands
     *
     * @param plantUml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the SVG
     */
    @Cacheable(value = "plantUmlSvg", unless = "#result == null || #result.isEmpty() || #result.contains('Syntax Error') || #result.contains('Cannot load')")
    public String generateSvg(final String plantUml, final boolean useDarkTheme) throws IOException {
        var input = validateInputAndApplyTheme(plantUml, useDarkTheme);
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(input);
            reader.outputImage(bos, new FileFormatOption(FileFormat.SVG));
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private String validateInputAndApplyTheme(final String plantUml, boolean useDarkTheme) {
        if (!StringUtils.hasText(plantUml)) {
            throw new IllegalArgumentException("The plantUml input cannot be empty");
        }
        if (plantUml.length() > 10000) {
            throw new IllegalArgumentException("Cannot parse plantUml input longer than 10.000 characters");
        }

        if (!plantUml.contains("!theme")) {
            String themeContent = useDarkTheme ? darkThemeContent : lightThemeContent;
            return plantUml.replace("@startuml", "@startuml\n" + themeContent);
        }
        return plantUml;
    }
}
