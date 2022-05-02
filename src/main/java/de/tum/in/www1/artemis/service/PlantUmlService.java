package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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

    private static final Path PATH_TMP_THEME = Paths.get(System.getProperty("java.io.tmpdir"), "artemis-puml-theme");

    private static final Path PATH_DARK_THEME_FILE = Paths.get(System.getProperty("java.io.tmpdir"), "artemis-puml-theme", DARK_THEME_FILE_NAME);

    private final ResourceLoaderService resourceLoaderService;

    public PlantUmlService(ResourceLoaderService resourceLoaderService) throws IOException {
        this.resourceLoaderService = resourceLoaderService;

        // Delete on first launch to ensure updates
        Files.deleteIfExists(PATH_DARK_THEME_FILE);
        ensureDarkTheme();

        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
        System.setProperty("plantuml.allowlist.path", PATH_TMP_THEME.toAbsolutePath().toString());
    }

    private void ensureDarkTheme() {
        if (!Files.exists(PATH_DARK_THEME_FILE)) {
            log.info("Storing dark UML theme to temporary directory");
            var themeResource = resourceLoaderService.getResource("puml", DARK_THEME_FILE_NAME);
            try (var inputStream = themeResource.getInputStream()) {
                Files.createDirectories(PATH_TMP_THEME);
                var path = Files.write(PATH_DARK_THEME_FILE, inputStream.readAllBytes());
                log.info("Dark UML theme stored successfully to " + path);
            }
            catch (IOException e) {
                log.error("Unable to store UML dark theme");
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @return The generated PNG as a byte array
     * @throws IOException if generateImage can't create the PNG
     */
    @Cacheable(value = "plantUmlPng", unless = "#result == null || #result.length == 0")
    public byte[] generatePng(final String plantUml, boolean useDarkTheme) throws IOException {
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
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the SVG
     */
    @Cacheable(value = "plantUmlSvg", unless = "#result == null || #result.isEmpty()")
    public String generateSvg(final String plantUml, boolean useDarkTheme) throws IOException {
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

        if (useDarkTheme && !plantUml.contains("!theme")) {
            ensureDarkTheme();
            return plantUml.replace("@startuml", "@startuml\n!theme artemisdark from " + PATH_TMP_THEME.toAbsolutePath());
        }
        return plantUml;
    }
}
