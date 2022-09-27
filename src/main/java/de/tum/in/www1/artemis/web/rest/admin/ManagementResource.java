package de.tum.in.www1.artemis.web.rest.admin;

import java.util.List;
import java.util.Map;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.service.feature.Feature;
import de.tum.in.www1.artemis.service.feature.FeatureToggleService;
import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

@RestController
@RequestMapping("api/admin/")
public class ManagementResource {

    private final FeatureToggleService featureToggleService;

    public ManagementResource(FeatureToggleService featureToggleService) {
        this.featureToggleService = featureToggleService;
    }

    /**
     * PUT management/feature-toggle -- Updates all given features by enabling/disabling them. (Map of feature -> shouldBeEnabled)
     *
     * @see FeatureToggleService
     * @param features A map of features (feature -> shouldBeActivated)
     * @return A list of all enabled features
     */
    @PutMapping("management/feature-toggle")
    @EnforceAdmin
    public ResponseEntity<List<Feature>> toggleFeatures(@RequestBody Map<Feature, Boolean> features) {
        featureToggleService.updateFeatureToggles(features);

        return new ResponseEntity<>(featureToggleService.enabledFeatures(), HttpStatus.OK);
    }

    /**
     * GET management/logs -- Gets the current log levels.
     * @return A list of all loggers with their log level
     */
    @GetMapping("management/logs")
    @EnforceAdmin
    public ResponseEntity<List<LoggerVM>> getList() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return ResponseEntity.ok(context.getLoggerList().stream().map(LoggerVM::new).toList());
    }

    /**
     * PUT management/logs -- Changes the log level of a logger.
     * @param jsonLogger The logger with the new log level
     * @return The updated logger
     */
    @PutMapping("management/logs")
    @EnforceAdmin
    public ResponseEntity<LoggerVM> changeLevel(@RequestBody LoggerVM jsonLogger) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(jsonLogger.getName());
        logger.setLevel(Level.valueOf(jsonLogger.getLevel()));
        return ResponseEntity.ok(new LoggerVM(logger));
    }
}
