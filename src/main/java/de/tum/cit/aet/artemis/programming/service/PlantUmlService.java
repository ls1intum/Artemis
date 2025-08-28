package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.NativeImageUtil.isNativeImage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import jakarta.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import de.tum.cit.aet.artemis.core.service.ResourceLoaderService;
import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

/**
 * Renders PlantUML either in-process (JVM) via the library,
 * or (when running as a GraalVM native image) by invoking the PlantUML native CLI.
 *
 * For native runs, make sure a 'plantuml' executable is on PATH (or configure artemis.plantuml.binary-path).
 * Recommended flags for the CLI are used: "-pipe -t{svg|png}".
 */
@Profile(PROFILE_CORE)
@Service
public class PlantUmlService {

    private static final Logger log = LoggerFactory.getLogger(PlantUmlService.class);

    private static final String DARK_THEME_FILE_NAME = "puml-theme-artemisdark.puml";

    private static final String LIGHT_THEME_FILE_NAME = "puml-theme-artemislight.puml";

    private final Path PATH_TMP_THEME;

    private final ResourceLoaderService resourceLoaderService;

    /**
     * Path to the PlantUML native CLI binary used when running as a GraalVM native image.
     * Default: "plantuml" (must be on PATH). You can also set an absolute path.
     */
    private final String plantumlBinaryPath;

    /**
     * Max time to wait for the external PlantUML process (native mode).
     */
    private final Duration cliTimeout;

    public PlantUmlService(ResourceLoaderService resourceLoaderService, @Value("${artemis.temp-path}") Path tempPath,
            @Value("${artemis.plantuml.binary-path:plantuml}") String plantumlBinaryPath, @Value("${artemis.plantuml.cli-timeout-seconds:20}") long cliTimeoutSeconds) {
        this.resourceLoaderService = resourceLoaderService;
        this.PATH_TMP_THEME = tempPath.resolve("artemis-puml-theme");
        this.plantumlBinaryPath = plantumlBinaryPath;
        this.cliTimeout = Duration.ofSeconds(Math.max(cliTimeoutSeconds, 1));
    }

    /**
     * Initializes themes and sets system properties for PlantUML security when the application is ready.
     * <p>
     * EventListener cannot be used here, as the bean is lazy.
     * </p>
     */
    @PostConstruct
    public void applicationReady() throws IOException {
        // Delete on first launch to ensure updates
        Files.deleteIfExists(PATH_TMP_THEME.resolve(DARK_THEME_FILE_NAME));
        Files.deleteIfExists(PATH_TMP_THEME.resolve(LIGHT_THEME_FILE_NAME));
        ensureThemes();

        // Restrict PlantUML include paths to our temp theme directory
        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
        System.setProperty("plantuml.allowlist.path", PATH_TMP_THEME.toAbsolutePath().toString());
        log.debug("PlantUML allowlist set to {}", PATH_TMP_THEME.toAbsolutePath());
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
                    log.error("Unable to store UML theme {}", fileName, e);
                    throw new RuntimeException("Unable to store UML theme: " + fileName, e); // NOPMD
                }
            }
        });
    }

    /**
     * Generate PNG diagram for given PlantUML commands.
     *
     * @param plantUml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return The generated PNG as a byte array
     * @throws IOException if generation fails
     */
    @Cacheable(value = "plantUmlPng", unless = "#result == null || #result.length == 0")
    public byte[] generatePng(final String plantUml, final boolean useDarkTheme) throws IOException, InterruptedException {
        final var input = validateInputAndApplyTheme(plantUml, useDarkTheme);

        if (isNativeImage()) {
            return runPlantUmlCli(input, "png");
        }

        try (final var bos = new ByteArrayOutputStream()) {
            new SourceStringReader(input).outputImage(bos, new FileFormatOption(FileFormat.PNG));
            return bos.toByteArray();
        }
    }

    /**
     * Generate SVG diagram for given PlantUML commands.
     *
     * @param plantUml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return The generated SVG text
     * @throws IOException if generation fails
     */
    @Cacheable(value = "plantUmlSvg", unless = "#result == null || #result.isEmpty()")
    public String generateSvg(final String plantUml, final boolean useDarkTheme) throws IOException, InterruptedException {
        final var input = validateInputAndApplyTheme(plantUml, useDarkTheme);

        if (isNativeImage()) {
            return new String(runPlantUmlCli(input, "svg"), StandardCharsets.UTF_8);
        }

        try (final var bos = new ByteArrayOutputStream()) {
            new SourceStringReader(input).outputImage(bos, new FileFormatOption(FileFormat.SVG));
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private String validateInputAndApplyTheme(final String plantUml, boolean useDarkTheme) {
        if (!StringUtils.hasText(plantUml)) {
            throw new IllegalArgumentException("The plantUml input cannot be empty");
        }
        if (plantUml.length() > 10_000) {
            throw new IllegalArgumentException("Cannot parse plantUml input longer than 10,000 characters");
        }

        if (!plantUml.contains("!theme")) {
            ensureThemes();
            final String themeLine = useDarkTheme ? "!theme artemisdark from " + PATH_TMP_THEME.toAbsolutePath() : "!theme artemislight from " + PATH_TMP_THEME.toAbsolutePath();

            // Insert the theme right after @startuml
            if (plantUml.contains("@startuml")) {
                return plantUml.replace("@startuml", "@startuml\n" + themeLine);
            }
        }
        return plantUml;
    }

    /**
     * Invoke the PlantUML native CLI with "-pipe -t{format}", write UML to stdin and read the bytes from stdout.
     */
    private byte[] runPlantUmlCli(String uml, String format) throws IOException, InterruptedException {
        final List<String> cmd = List.of(plantumlBinaryPath, "-pipe", "-t" + format);
        log.debug("Executing PlantUML CLI: {}", String.join(" ", cmd));

        final ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectError(Redirect.INHERIT); // stream stderr to container logs for easier debugging
        final Process p = pb.start();

        try (OutputStream stdin = p.getOutputStream()) {
            stdin.write(uml.getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        final byte[] out = p.getInputStream().readAllBytes();

        final boolean finished = p.waitFor(cliTimeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("PlantUML CLI timed out after " + cliTimeout.getSeconds() + "s");
        }

        final int exit = p.exitValue();
        if (exit != 0 || out.length == 0) {
            throw new IOException("PlantUML CLI failed: exit=" + exit + ", bytes=" + out.length);
        }

        return out;
    }
}
