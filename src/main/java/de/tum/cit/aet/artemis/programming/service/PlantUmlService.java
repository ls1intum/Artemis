package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.service.ResourceLoaderService;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@Profile(PROFILE_CORE)
@Service
public class PlantUmlService {

    private static final Logger log = LoggerFactory.getLogger(PlantUmlService.class);

    private static final String DARK_THEME_FILE_NAME = "puml-theme-artemisdark.puml";

    private static final String LIGHT_THEME_FILE_NAME = "puml-theme-artemislight.puml";

    private static final Path PATH_TMP_THEME = Paths.get(System.getProperty("java.io.tmpdir"), "artemis-puml-theme");

    private final ResourceLoaderService resourceLoaderService;

    public PlantUmlService(ResourceLoaderService resourceLoaderService) {
        this.resourceLoaderService = resourceLoaderService;
    }

    /**
     * Initializes themes and sets system properties for PlantUML security when the application is ready.
     *
     * <p>
     * Deletes temporary theme files to ensure updates, ensures themes are available, and configures PlantUML security settings.
     *
     * @throws IOException if an I/O error occurs during file deletion
     */
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() throws IOException {
        // Delete on first launch to ensure updates
        Files.deleteIfExists(PATH_TMP_THEME.resolve(DARK_THEME_FILE_NAME));
        Files.deleteIfExists(PATH_TMP_THEME.resolve(LIGHT_THEME_FILE_NAME));
        ensureThemes();

        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
        System.setProperty("plantuml.allowlist.path", PATH_TMP_THEME.toAbsolutePath().toString());
    }

    private void ensureThemes() {
        Stream.of(DARK_THEME_FILE_NAME, LIGHT_THEME_FILE_NAME).forEach(fileName -> {
            final Path path = PATH_TMP_THEME.resolve(fileName);
            if (!Files.exists(path)) {
                log.debug("Storing UML theme to temporary directory");
                final var themeResource = resourceLoaderService.getResource(Path.of("puml", fileName));
                try (var inputStream = themeResource.getInputStream()) {
                    FileUtils.copyToFile(inputStream, path.toFile());
                    log.debug("UML theme stored successfully to {}", path);
                }
                catch (IOException e) {
                    log.error("Unable to store UML dark theme", e);
                    throw new RuntimeException("Unable to store UML dark theme", e); // NOPMD
                }
            }
        });
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
    @Cacheable(value = "plantUmlSvg", unless = "#result == null || #result.isEmpty()")
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
            ensureThemes();
            if (useDarkTheme) {
                return plantUml.replace("@startuml", "@startuml\n!theme artemisdark from " + PATH_TMP_THEME.toAbsolutePath());
            }
            else {
                return plantUml.replace("@startuml", "@startuml\n!theme artemislight from " + PATH_TMP_THEME.toAbsolutePath());
            }
        }
        return plantUml;
    }
}
