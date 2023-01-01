package de.tum.in.www1.artemis.web.rest.errors;

import java.io.IOException;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import javax.ws.rs.BadRequestException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.socket.sockjs.SockJsMessageDeliveryException;
import org.zalando.problem.*;
import org.zalando.problem.spring.web.advice.ProblemHandling;
import org.zalando.problem.spring.web.advice.security.SecurityAdviceTrait;
import org.zalando.problem.violations.ConstraintViolationProblem;

import de.tum.in.www1.artemis.service.connectors.gitlab.GitLabException;
import tech.jhipster.web.util.HeaderUtil;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures. The error response follows RFC7807 - Problem Details for HTTP APIs
 * (https://tools.ietf.org/html/rfc7807)
 */
@ControllerAdvice
public class ExceptionTranslator implements ProblemHandling, SecurityAdviceTrait {

    private final Logger log = LoggerFactory.getLogger(ExceptionTranslator.class);

    private static final String FIELD_ERRORS_KEY = "fieldErrors";

    private static final String MESSAGE_KEY = "message";

    private static final String PATH_KEY = "path";

    private static final String VIOLATIONS_KEY = "violations";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    /**
     * Post-process the Problem payload to add the message key for the front-end if needed.
     */
    @Override
    public ResponseEntity<Problem> process(@Nullable ResponseEntity<Problem> entity, @NotNull NativeWebRequest request) {
        if (entity == null) {
            return null;
        }
        Problem problem = entity.getBody();
        if (!(problem instanceof ConstraintViolationProblem || problem instanceof DefaultProblem)) {
            return entity;
        }
        ProblemBuilder builder = Problem.builder().withType(Problem.DEFAULT_TYPE.equals(problem.getType()) ? ErrorConstants.DEFAULT_TYPE : problem.getType())
                .withStatus(problem.getStatus()).withTitle(problem.getTitle()).with(PATH_KEY, request.getNativeRequest(HttpServletRequest.class).getRequestURI());

        if (problem instanceof ConstraintViolationProblem) {
            builder.with(VIOLATIONS_KEY, ((ConstraintViolationProblem) problem).getViolations()).with(MESSAGE_KEY, ErrorConstants.ERR_VALIDATION);
        }
        else {
            builder.withCause(((DefaultProblem) problem).getCause()).withDetail(problem.getDetail()).withInstance(problem.getInstance());
            problem.getParameters().forEach(builder::with);
            if (!problem.getParameters().containsKey(MESSAGE_KEY) && problem.getStatus() != null) {
                builder.with(MESSAGE_KEY, "error.http." + problem.getStatus().getStatusCode());
            }
        }
        return new ResponseEntity<>(builder.build(), entity.getHeaders(), entity.getStatusCode());
    }

    @Override
    public ResponseEntity<Problem> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @Nonnull NativeWebRequest request) {
        BindingResult result = ex.getBindingResult();
        List<FieldErrorVM> fieldErrors = result.getFieldErrors().stream().map(f -> new FieldErrorVM(f.getObjectName().replaceFirst("DTO$", ""), f.getField(), f.getCode()))
                .toList();

        Problem problem = Problem.builder().withType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE).withTitle("Method argument not valid").withStatus(defaultConstraintViolationStatus())
                .with(MESSAGE_KEY, ErrorConstants.ERR_VALIDATION).with(FIELD_ERRORS_KEY, fieldErrors).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleEmailAlreadyUsedException(EmailAlreadyUsedException ex, NativeWebRequest request) {
        EmailAlreadyUsedException problem = new EmailAlreadyUsedException();
        return create(problem, request, HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleUsernameAlreadyUsedException(de.tum.in.www1.artemis.exception.UsernameAlreadyUsedException ex, NativeWebRequest request) {
        LoginAlreadyUsedException problem = new LoginAlreadyUsedException();
        return create(problem, request, HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handlePasswordViolatesRequirementsException(PasswordViolatesRequirementsException ex, NativeWebRequest request) {
        return create(new PasswordViolatesRequirementsException(), request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleBadRequestAlertException(BadRequestAlertException ex, NativeWebRequest request) {
        return create(ex, request, HeaderUtil.createFailureAlert(applicationName, true, ex.getEntityName(), ex.getErrorKey(), ex.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleConflictException(ConflictException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.CONFLICT).with(MESSAGE_KEY, ex.getMessage()).with("X-" + applicationName + "-error", "error." + ex.getErrorKey())
                .with("X-" + applicationName + "-params", ex.getEntityName()).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleConcurrencyFailure(ConcurrencyFailureException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.CONFLICT).with(MESSAGE_KEY, ErrorConstants.ERR_CONCURRENCY_FAILURE).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleEntityNotFoundException(EntityNotFoundException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.NOT_FOUND).with(MESSAGE_KEY, ErrorConstants.REQ_404_REASON).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleBadRequest(BadRequestException exception, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.BAD_REQUEST).with(MESSAGE_KEY, ErrorConstants.REQ_400_REASON).build();
        return create(exception, problem, request);
    }

    /**
     * @param e a specific exception
     * @param request the request
     * @return the exception wrapped into a http entity
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    // taken from https://mtyurt.net/post/spring-how-to-handle-ioexception-broken-pipe.html
    public Object exceptionHandler(IOException e, HttpServletRequest request) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "Broken pipe")) {
            log.info("Broken pipe IOException occurred: {}", e.getMessage());
            // socket is closed, cannot return any response
            return null;
        }
        else {
            return new HttpEntity<>(e.getMessage());
        }
    }

    /**
     * @param e a specific exception
     * @param request the request
     * @return the exception wrapped into a http entity
     */
    @ExceptionHandler(SockJsMessageDeliveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Object exceptionHandler(SockJsMessageDeliveryException e, HttpServletRequest request) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(e), "Session closed")) {
            // session is closed, cannot return any response
            log.info("Session closed SockJsMessageDeliveryException occurred: {}", e.getMessage());
            return null;
        }
        else {
            return new HttpEntity<>(e.getMessage());
        }
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleGitlabException(GitLabException ex, NativeWebRequest request) {
        final var problem = Problem.builder().withStatus(Status.INTERNAL_SERVER_ERROR).with(MESSAGE_KEY, ex.getMessage()).build();
        return create(ex, problem, request);
    }

}
