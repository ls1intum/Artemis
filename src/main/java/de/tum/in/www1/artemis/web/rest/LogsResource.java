package de.tum.in.www1.artemis.web.rest;

import java.util.List;

import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import de.tum.in.www1.artemis.security.annotations.EnforceAdmin;
import de.tum.in.www1.artemis.versioning.IgnoreGlobalMapping;
import de.tum.in.www1.artemis.web.rest.vm.LoggerVM;

/**
 * Controller for view and managing Log Level at runtime.
 */
@RestController
@RequestMapping("/management")
// is automatically secured and can only be invoked by Admins
// TODO: are we sure about that?
public class LogsResource {

    // TODO: /admin
    // TODO: Is this actually public?
    @IgnoreGlobalMapping
    @GetMapping("/logs")
    @EnforceAdmin
    public List<LoggerVM> getList() {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        return context.getLoggerList().stream().map(LoggerVM::new).toList();
    }

    // TODO: /admin
    @IgnoreGlobalMapping
    @PutMapping("/logs")
    @EnforceAdmin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeLevel(@RequestBody LoggerVM jsonLogger) {
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger(jsonLogger.getName()).setLevel(Level.valueOf(jsonLogger.getLevel()));
    }
}
