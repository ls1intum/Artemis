package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import net.sourceforge.plantuml.security.SecurityProfile;
import net.sourceforge.plantuml.security.SecurityUtils;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class PlantUmlService {

    private static final String PLANTUML_SECURITY_PROFILE_PROPERTY = "PLANTUML_SECURITY_PROFILE";

    static {
        // Pin the rendering profile instead of inheriting the library default, which is LEGACY and permits a diagram to
        // pull in local files and remote resources through directives such as !include. Diagram sources here come straight
        // from request bodies, so the restrictive profile is the only appropriate one. Artemis injects its theme as inline
        // content rather than as an included file, so nothing it needs depends on those directives.
        //
        // The property is read by PlantUML on first use, and this runs when the service class loads, i.e. before any
        // PlantUML class is touched. An explicit override from the environment is left alone.
        if (System.getProperty(PLANTUML_SECURITY_PROFILE_PROPERTY) == null && System.getenv(PLANTUML_SECURITY_PROFILE_PROPERTY) == null) {
            System.setProperty(PLANTUML_SECURITY_PROFILE_PROPERTY, SecurityProfile.SANDBOX.name());
        }
    }

    private static final Logger log = LoggerFactory.getLogger(PlantUmlService.class);

    private static final String DARK_THEME_FILE_NAME = "puml-theme-artemisdark.puml";

    private static final String LIGHT_THEME_FILE_NAME = "puml-theme-artemislight.puml";

    /**
     * Smetana is PlantUML's built-in Java layout engine that doesn't require external Graphviz installation.
     */
    private static final String SMETANA_PRAGMA = "!pragma layout smetana\n";

    private static final Pattern THEME_DIRECTIVE = Pattern.compile("(?m)^\\s*!theme\\b");

    private static final Pattern PRAGMA_LAYOUT_DIRECTIVE = Pattern.compile("(?m)^\\s*!pragma\\s+layout\\b");

    private static final Pattern START_UML_DIRECTIVE = Pattern.compile("(?im)^\\s*@startuml\\b");

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
    // Cached per node (see BlobCacheConfiguration): a rendered diagram is a pure function of its source, so nodes cannot
    // disagree and no cross-node eviction is needed.
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

    /**
     * Reports the profile PlantUML actually resolved.
     * <p>
     * The pin in the static initialiser depends on this class being loaded before any PlantUML class, which holds because
     * every render goes through here. Logging the resolved value makes a broken assumption visible instead of silent.
     */
    @PostConstruct
    public void logResolvedSecurityProfile() {
        SecurityProfile profile = SecurityUtils.getSecurityProfile();
        if (profile == SecurityProfile.SANDBOX || profile == SecurityProfile.ALLOWLIST) {
            log.info("PlantUML runs under the {} profile", profile);
        }
        else {
            log.error("PlantUML resolved the {} profile, which allows a diagram to reach local files and remote resources. "
                    + "Set {} to SANDBOX to restore the intended restriction.", profile, PLANTUML_SECURITY_PROFILE_PROPERTY);
        }
    }

    private String validateInputAndApplyTheme(final String plantUml, boolean useDarkTheme) {
        if (!StringUtils.hasText(plantUml)) {
            throw new IllegalArgumentException("The plantUml input cannot be empty");
        }
        if (plantUml.length() > 10000) {
            throw new IllegalArgumentException("Cannot parse plantUml input longer than 10.000 characters");
        }

        if (!THEME_DIRECTIVE.matcher(plantUml).find()) {
            // Apply Artemis theme (which includes Smetana pragma)
            String themeContent = useDarkTheme ? darkThemeContent : lightThemeContent;
            return injectAfterStartUml(plantUml, themeContent);
        }
        // User has custom theme - still apply Smetana to avoid Graphviz dependency
        if (!PRAGMA_LAYOUT_DIRECTIVE.matcher(plantUml).find()) {
            return injectAfterStartUml(plantUml, SMETANA_PRAGMA);
        }
        return plantUml;
    }

    private String injectAfterStartUml(final String plantUml, final String contentToInject) {
        return START_UML_DIRECTIVE.matcher(plantUml).replaceFirst("$0\n" + Matcher.quoteReplacement(contentToInject));
    }
}
