package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@Service
public class PlantUmlService {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private static final String DARK_THEME_FILE_NAME = "puml-theme-artemisdark.puml";

    private static final String LIGHT_THEME_FILE_NAME = "puml-theme-artemislight.puml";

    private static final Path PATH_TMP_THEME = Paths.get(System.getProperty("java.io.tmpdir"), "artemis-puml-theme");

    private final ResourceLoaderService resourceLoaderService;

    public PlantUmlService(ResourceLoaderService resourceLoaderService) throws IOException {
        this.resourceLoaderService = resourceLoaderService;

        // Delete on first launch to ensure updates
        Files.deleteIfExists(PATH_TMP_THEME.resolve(DARK_THEME_FILE_NAME));
        Files.deleteIfExists(PATH_TMP_THEME.resolve(LIGHT_THEME_FILE_NAME));
        ensureThemes();

        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
        System.setProperty("plantuml.allowlist.path", PATH_TMP_THEME.toAbsolutePath().toString());
    }

    private void ensureThemes() {
        Stream.of(DARK_THEME_FILE_NAME, LIGHT_THEME_FILE_NAME).forEach(fileName -> {
            var path = PATH_TMP_THEME.resolve(fileName);
            if (!Files.exists(path)) {
                log.info("Storing UML theme to temporary directory");
                var themeResource = resourceLoaderService.getResource("puml", fileName);
                try (var inputStream = themeResource.getInputStream()) {
                    Files.createDirectories(PATH_TMP_THEME);
                    Files.write(path, inputStream.readAllBytes());
                    log.info("UML theme stored successfully to {}", path);
                }
                catch (IOException e) {
                    log.error("Unable to store UML dark theme");
                    throw new RuntimeException("Unable to store UML dark theme", e); // NOPMD
                }
            }
        });
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
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
     * @param plantUml PlantUML command(s)
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
