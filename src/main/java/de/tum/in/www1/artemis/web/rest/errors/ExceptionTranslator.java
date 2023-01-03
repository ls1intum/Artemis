package de.tum.in.www1.artemis.web.rest.errors;

import static jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;
import static org.zalando.problem.Status.BAD_REQUEST;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.validation.BindingResult;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.accept.ContentNegotiationStrategy;
import org.springframework.web.accept.HeaderContentNegotiationStrategy;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.socket.sockjs.SockJsMessageDeliveryException;
import org.zalando.problem.*;

import de.tum.in.www1.artemis.exception.ArtemisAuthenticationException;
import de.tum.in.www1.artemis.exception.ContinuousIntegrationException;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.exception.VersionControlException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;
import tech.jhipster.web.util.HeaderUtil;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures. The error response follows RFC7807 - Problem Details for HTTP APIs
 * (<a href="https://tools.ietf.org/html/rfc7807">RFC7807</a>)
 */
// TODO: double check if this is still working with the new Spring version or if we can completely remove it
@ControllerAdvice
public class ExceptionTranslator {

    public static final String PROBLEM_VALUE = "application/problem+json";

    public static final MediaType PROBLEM = MediaType.parseMediaType(PROBLEM_VALUE);

    public static final String X_PROBLEM_VALUE = "application/x.problem+json";

    public static final MediaType X_PROBLEM = MediaType.parseMediaType(X_PROBLEM_VALUE);

    private final Logger log = LoggerFactory.getLogger(ExceptionTranslator.class);

    private static final String FIELD_ERRORS_KEY = "fieldErrors";

    private static final String MESSAGE_KEY = "message";

    private static final String PATH_KEY = "path";

    private static final String VIOLATIONS_KEY = "violations";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    /**
     * Post-process the Problem payload to add the message key for the front-end if needed.
     * @param entity
     * @param request
     * @return the response entities with the specified problem
     */
    public ResponseEntity<Problem> process(ResponseEntity<Problem> entity, @NotNull NativeWebRequest request) {
        Problem problem = entity.getBody();
        if (!(problem instanceof DefaultProblem)) {
            return entity;
        }
        ProblemBuilder builder = Problem.builder().withType(Problem.DEFAULT_TYPE.equals(problem.getType()) ? ErrorConstants.DEFAULT_TYPE : problem.getType())
                .withStatus(problem.getStatus()).withTitle(problem.getTitle()).with(PATH_KEY, request.getNativeRequest(HttpServletRequest.class).getRequestURI());

        builder.withCause(((DefaultProblem) problem).getCause()).withDetail(problem.getDetail()).withInstance(problem.getInstance());
        problem.getParameters().forEach(builder::with);
        if (!problem.getParameters().containsKey(MESSAGE_KEY) && problem.getStatus() != null) {
            builder.with(MESSAGE_KEY, "error.http." + problem.getStatus().getStatusCode());
        }
        return new ResponseEntity<>(builder.build(), entity.getHeaders(), entity.getStatusCode());
    }

    /**
     * handles requests for which the method is not available
     * @param ex
     * @param request
     * @return the response entity with information about this case (method not available)
     */
    public ResponseEntity<Problem> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, @NotNull NativeWebRequest request) {
        BindingResult result = ex.getBindingResult();
        List<FieldErrorVM> fieldErrors = result.getFieldErrors().stream().map(f -> new FieldErrorVM(f.getObjectName().replaceFirst("DTO$", ""), f.getField(), f.getCode()))
                .toList();

        Problem problem = Problem.builder().withType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE).withTitle("Method argument not valid").withStatus(BAD_REQUEST)
                .with(MESSAGE_KEY, ErrorConstants.ERR_VALIDATION).with(FIELD_ERRORS_KEY, fieldErrors).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleEmailAlreadyUsedException(@SuppressWarnings("UnusedParameters") EmailAlreadyUsedException ex, NativeWebRequest request) {
        EmailAlreadyUsedException problem = new EmailAlreadyUsedException();
        return create(problem, request, HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleUsernameAlreadyUsedException(@SuppressWarnings("UnusedParameters") de.tum.in.www1.artemis.exception.UsernameAlreadyUsedException ex,
            NativeWebRequest request) {
        LoginAlreadyUsedException problem = new LoginAlreadyUsedException();
        return create(problem, request, HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getMessage()));
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handlePasswordViolatesRequirementsException(@SuppressWarnings("UnusedParameters") PasswordViolatesRequirementsException ex,
            NativeWebRequest request) {
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
    public ResponseEntity<Problem> handleAccessForbiddenAlertException(AccessForbiddenAlertException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.FORBIDDEN).with(MESSAGE_KEY, ErrorConstants.REQ_403_REASON).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleArtemisAuthenticationException(ArtemisAuthenticationException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.INTERNAL_SERVER_ERROR).with(MESSAGE_KEY, ErrorConstants.ERR_AUTHENTICATION).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleGitException(GitException ex, NativeWebRequest request) {
        Problem problem = Problem.builder().withStatus(Status.INTERNAL_SERVER_ERROR).with(MESSAGE_KEY, ErrorConstants.ERR_AUTHENTICATION).build();
        return create(ex, problem, request);
    }

    /**
     * @param e a specific exception
     * @param request the request
     * @return the exception wrapped into a http entity
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    // taken from https://mtyurt.net/post/spring-how-to-handle-ioexception-broken-pipe.html
    public Object exceptionHandler(IOException e, @SuppressWarnings("UnusedParameters") HttpServletRequest request) {
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
     * @param ex a specific exception
     * @param request the request
     * @return the exception wrapped into a http entity
     */
    @ExceptionHandler(SockJsMessageDeliveryException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Object exceptionHandler(SockJsMessageDeliveryException ex, @SuppressWarnings("UnusedParameters") HttpServletRequest request) {
        if (StringUtils.containsIgnoreCase(ExceptionUtils.getRootCauseMessage(ex), "Session closed")) {
            // session is closed, cannot return any response
            log.info("Session closed SockJsMessageDeliveryException occurred: {}", ex.getMessage());
            return null;
        }
        else {
            return new HttpEntity<>(ex.getMessage());
        }
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleContinuousIntegrationException(ContinuousIntegrationException ex, NativeWebRequest request) {
        final var problem = Problem.builder().withStatus(Status.INTERNAL_SERVER_ERROR).with(MESSAGE_KEY, ex.getMessage()).build();
        return create(ex, problem, request);
    }

    @ExceptionHandler
    public ResponseEntity<Problem> handleVersionControlException(VersionControlException ex, NativeWebRequest request) {
        final var problem = Problem.builder().withStatus(Status.INTERNAL_SERVER_ERROR).with(MESSAGE_KEY, ex.getMessage()).build();
        return create(ex, problem, request);
    }

    /**
     * Creates a {@link Problem problem} {@link ResponseEntity response} for the given {@link Throwable throwable}
     * by taking any {@link ResponseStatus} annotation on the exception type or one of the causes into account.
     *
     * @param problem exception being caught
     * @param request incoming request
     * @return the problem response
     */
    private ResponseEntity<Problem> create(final ThrowableProblem problem, final NativeWebRequest request) {
        return create(problem, request, new HttpHeaders());
    }

    private ResponseEntity<Problem> create(final ThrowableProblem problem, final NativeWebRequest request, final HttpHeaders headers) {
        return create(problem, problem, request, headers);
    }

    private ResponseEntity<Problem> create(final Throwable throwable, final Problem problem, final NativeWebRequest request) {
        return create(throwable, problem, request, new HttpHeaders());
    }

    private ResponseEntity<Problem> create(final Throwable throwable, final Problem problem, final NativeWebRequest request, final HttpHeaders headers) {

        final HttpStatus status = HttpStatus.valueOf(Optional.ofNullable(problem.getStatus()).orElse(Status.INTERNAL_SERVER_ERROR).getStatusCode());

        log(throwable, status);

        if (status == HttpStatus.INTERNAL_SERVER_ERROR) {
            request.setAttribute(ERROR_EXCEPTION, throwable, SCOPE_REQUEST);
        }

        return process(negotiate(request).map(contentType -> ResponseEntity.status(status).headers(headers).contentType(contentType).body(problem)).orElseGet(() -> {
            final ResponseEntity<Problem> fallback = fallback(problem, headers);

            if (fallback.getBody() == null) {
                /*
                 * Ugly hack to workaround an issue with Tomcat and Spring as described in https://github.com/zalando/problem-spring-web/issues/84. The default fallback in case
                 * content negotiation failed is a 406 Not Acceptable without a body. Tomcat will then display its error page since no body was written and the response was not
                 * committed. In order to force Spring to flush/commit one would need to provide a body but that in turn would fail because Spring would then fail to negotiate the
                 * correct content type. Writing the status code, headers and flushing the body manually is a dirty way to bypass both parties, Tomcat and Spring, at the same time.
                 */

                var nativeRequest = request.getNativeResponse(HttpServletResponse.class);
                if (nativeRequest != null) {
                    try (final ServerHttpResponse response = new ServletServerHttpResponse(nativeRequest)) {
                        response.setStatusCode(fallback.getStatusCode());
                        response.getHeaders().putAll(fallback.getHeaders());
                        try {
                            response.getBody(); // just so we're actually flushing the body...
                            response.flush();
                        }
                        catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

            return fallback;
        }), request);
    }

    private void log(final Throwable throwable, final HttpStatus status) {
        if (status.is4xxClientError()) {
            log.warn("{}: {}", status.getReasonPhrase(), throwable.getMessage());
        }
        else if (status.is5xxServerError()) {
            log.error(status.getReasonPhrase(), throwable);
        }
    }

    private Optional<MediaType> negotiate(final NativeWebRequest request) {
        final ContentNegotiationStrategy negotiator = new HeaderContentNegotiationStrategy();
        final List<MediaType> mediaTypes;
        try {
            mediaTypes = negotiator.resolveMediaTypes(request);
        }
        catch (HttpMediaTypeNotAcceptableException e) {
            throw new RuntimeException(e);
        }
        return getProblemMediaType(mediaTypes);
    }

    private static ResponseEntity<Problem> fallback(final Problem problem, final HttpHeaders headers) {
        return ResponseEntity.status(HttpStatus.valueOf(Optional.ofNullable(problem.getStatus()).orElse(Status.INTERNAL_SERVER_ERROR).getStatusCode())).headers(headers)
                .contentType(PROBLEM).body(problem);
    }

    /**
     * get the problem media type
     * @param mediaTypes
     * @return the problem media type
     */
    public static Optional<MediaType> getProblemMediaType(final List<MediaType> mediaTypes) {
        for (final MediaType mediaType : mediaTypes) {
            if (mediaType.includes(APPLICATION_JSON) || mediaType.includes(PROBLEM)) {
                return Optional.of(PROBLEM);
            }
            else if (mediaType.includes(X_PROBLEM)) {
                return Optional.of(X_PROBLEM);
            }
        }

        return Optional.empty();
    }
}
