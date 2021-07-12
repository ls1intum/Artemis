package de.tum.in.www1.artemis.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import net.sourceforge.plantuml.FileFormat;
import net.sourceforge.plantuml.FileFormatOption;
import net.sourceforge.plantuml.SourceStringReader;

@Service
public class PlantUmlService {

    private final Logger log = LoggerFactory.getLogger(PlantUmlService.class);

    private final ExecutorService executorService;

    private final BlockingQueue<Runnable> linkedBlockingDeque;

    public PlantUmlService() {
        int threads = Runtime.getRuntime().availableProcessors();
        this.linkedBlockingDeque = new LinkedBlockingDeque<>(threads * 2);
        this.executorService = new ThreadPoolExecutor(threads, threads * 2, 30, TimeUnit.SECONDS, linkedBlockingDeque, new ThreadPoolExecutor.AbortPolicy());
        System.setProperty("PLANTUML_SECURITY_PROFILE", "ALLOWLIST");
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantUml PlantUML command(s)
     * @return The generated PNG as a byte array
     * @throws IOException if generateImage can't create the PNG
     */
    public byte[] generatePng(final String plantUml) throws IOException {
        log.info("Generate plantUml svg with " + linkedBlockingDeque.size() + " requests in the queue");
        validateInput(plantUml);
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);
            generateImage(reader, bos, new FileFormatOption(FileFormat.PNG));
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
    public String generateSvg(final String plantUml) throws IOException {
        log.info("Generage plantUml svg with " + linkedBlockingDeque.size() + " requests in the queue");
        validateInput(plantUml);
        try (final var bos = new ByteArrayOutputStream()) {
            final var reader = new SourceStringReader(plantUml);
            generateImage(reader, bos, new FileFormatOption(FileFormat.SVG));
            return bos.toString(StandardCharsets.UTF_8);
        }
    }

    private void validateInput(final String plantUml) {
        if (!StringUtils.hasText(plantUml)) {
            throw new IllegalArgumentException("The plantUml input cannot be empty");
        }
        if (plantUml.length() > 10000) {
            throw new IllegalArgumentException("Cannot parse plantUml input longer than 10.000 characters");
        }
    }

    // invoke the rendering with a 5s timeout to avoid issues with long running operations
    private void generateImage(SourceStringReader reader, ByteArrayOutputStream bos, FileFormatOption option) {
        Callable<Object> task = () -> reader.outputImage(bos, option);
        Future<Object> future = null;
        try {
            future = executorService.submit(task);
            // blocking method call
            future.get(5, TimeUnit.SECONDS);
        }
        catch (TimeoutException | InterruptedException | ExecutionException ex) {
            log.warn("Exception in PlantUmlService generateImage: {}", ex.getClass().getName());
        }
        catch (RejectedExecutionException ex) {
            log.warn("PlantUmlService overloaded with too many requests. Reject current request");
        }
        finally {
            if (future != null) {
                future.cancel(true); // may or may not desire this
            }
        }
    }
}
