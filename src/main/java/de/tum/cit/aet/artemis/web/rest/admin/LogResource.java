package de.tum.cit.aet.artemis.web.rest.admin;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAdmin;
import de.tum.cit.aet.artemis.web.rest.vm.LoggerVM;

/**
 * Controller for view and managing Log Level at runtime.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/admin/")
public class LogResource {

    /**
     * GET logs -- Gets the current log levels.
     *
     * @return A list of all loggers with their log level
     */
    @GetMapping("logs")
    @EnforceAdmin
    public ResponseEntity<List<LoggerVM>> getList() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return ResponseEntity.ok(context.getLoggerList().stream().map(LoggerVM::new).toList());
    }

    /**
     * PUT logs -- Changes the log level of a logger.
     *
     * @param jsonLogger The logger with the new log level
     * @return The updated logger
     */
    @PutMapping("logs")
    @EnforceAdmin
    public ResponseEntity<LoggerVM> changeLevel(@RequestBody LoggerVM jsonLogger) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger(jsonLogger.getName());
        logger.setLevel(Level.valueOf(jsonLogger.getLevel()));
        return ResponseEntity.ok(new LoggerVM(logger));
    }
}
