package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.util.TimeLogUtil.formatDurationFrom;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.programming.service.PlantUmlService;
import tech.jhipster.config.JHipsterProperties;

@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/plantuml/")
public class PlantUmlResource {

    private static final Logger log = LoggerFactory.getLogger(PlantUmlResource.class);

    private final PlantUmlService plantUmlService;

    private final CacheControl cache;

    public PlantUmlResource(PlantUmlService plantUmlService, JHipsterProperties jHipsterProperties) {
        this.plantUmlService = plantUmlService;
        this.cache = CacheControl.maxAge(jHipsterProperties.getHttp().getCache().getTimeToLiveInDays(), TimeUnit.DAYS).cachePrivate();
    }

    /**
     * Generate PNG diagram for given PlantUML commands
     *
     * @param plantuml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping("png")
    @EnforceAtLeastStudent
    public ResponseEntity<byte[]> generatePng(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.nanoTime();
        final var png = plantUmlService.generatePng(plantuml, useDarkTheme);
        log.debug("PlantUml.generatePng took {}", formatDurationFrom(start));
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).cacheControl(cache).body(png);
    }

    /**
     * Generate svn diagram for given PlantUML commands
     *
     * @param plantuml     PlantUML command(s)
     * @param useDarkTheme whether the dark theme should be used
     * @return ResponseEntity PNG stream
     * @throws IOException if generateImage can't create the PNG
     */
    @GetMapping("svg")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<String> generateSvg(@RequestParam("plantuml") String plantuml, @RequestParam(value = "useDarkTheme", defaultValue = "false") boolean useDarkTheme)
            throws IOException {
        long start = System.nanoTime();
        final var svg = plantUmlService.generateSvg(plantuml, useDarkTheme);
        log.debug("PlantUml.generateSvg took {}", formatDurationFrom(start));
        return ResponseEntity.ok().cacheControl(cache).body(svg);
    }
}
