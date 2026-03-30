package de.tum.cit.aet.artemis.core.exception;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import de.tum.cit.aet.artemis.core.util.HeaderUtil;

/**
 * Controller advice to translate the server side exceptions to client-friendly json structures. The error response follows
 * <a href="https://tools.ietf.org/html/rfc9457">RFC 9457 - Problem Details for HTTP APIs</a>
 */
@ControllerAdvice
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionTranslator.class);

    private static final String FIELD_ERRORS_KEY = "fieldErrors";

    private static final String MESSAGE_KEY = "message";

    private static final String PATH_KEY = "path";

    private static final String VIOLATIONS_KEY = "violations";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    /**
     * Post-process a ProblemDetail to add the request path and a default message key if not already present.
     */
    private ProblemDetail postProcess(@Nullable ProblemDetail detail, @NonNull NativeWebRequest request) {
        if (detail == null) {
            return null;
        }
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        if (servletRequest != null) {
            detail.setProperty(PATH_KEY, servletRequest.getRequestURI());
        }
        if (detail.getProperties() == null || !detail.getProperties().containsKey(MESSAGE_KEY)) {
            HttpStatusCode statusCode = HttpStatusCode.valueOf(detail.getStatus());
            detail.setProperty(MESSAGE_KEY, "error.http." + statusCode.value());
        }
        return detail;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(@NonNull MethodArgumentNotValidException ex, @NonNull HttpHeaders headers, @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        BindingResult result = ex.getBindingResult();
        List<FieldErrorVM> fieldErrors = result.getFieldErrors().stream().map(f -> new FieldErrorVM(f.getObjectName().replaceFirst("DTO$", ""), f.getField(), f.getCode()))
                .toList();

        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setType(ErrorConstants.CONSTRAINT_VIOLATION_TYPE);
        detail.setTitle("Method argument not valid");
        detail.setProperty(MESSAGE_KEY, ErrorConstants.ERR_VALIDATION);
        detail.setProperty(FIELD_ERRORS_KEY, fieldErrors);

        if (request instanceof NativeWebRequest nativeWebRequest) {
            postProcess(detail, nativeWebRequest);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).headers(headers).body(detail);
    }

    /**
     * Handles {@link EmailAlreadyUsedException} and returns a 400 response with failure alert headers.
     *
     * @param ex      the exception indicating the email is already in use
     * @param request the current web request
     * @return a {@link ResponseEntity} with problem details
     */
    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleEmailAlreadyUsedException(EmailAlreadyUsedException ex, NativeWebRequest request) {
        EmailAlreadyUsedException problem = new EmailAlreadyUsedException();
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getBody().getTitle());
        ProblemDetail detail = problem.getBody();
        postProcess(detail, request);
        return ResponseEntity.status(problem.getStatusCode()).headers(headers).body(detail);
    }

    /**
     * Handles {@link UsernameAlreadyUsedException} and returns a 400 response with failure alert headers.
     *
     * @param ex      the exception indicating the username is already in use
     * @param request the current web request
     * @return a {@link ResponseEntity} with problem details
     */
    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleUsernameAlreadyUsedException(UsernameAlreadyUsedException ex, NativeWebRequest request) {
        LoginAlreadyUsedException problem = new LoginAlreadyUsedException();
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, problem.getEntityName(), problem.getErrorKey(), problem.getBody().getTitle());
        ProblemDetail detail = problem.getBody();
        postProcess(detail, request);
        return ResponseEntity.status(problem.getStatusCode()).headers(headers).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handlePasswordViolatesRequirementsException(PasswordViolatesRequirementsException ex, NativeWebRequest request) {
        ProblemDetail detail = ex.getBody();
        postProcess(detail, request);
        return ResponseEntity.status(ex.getStatusCode()).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleBadRequestAlertException(BadRequestAlertException ex, NativeWebRequest request) {
        HttpHeaders headers = HeaderUtil.createFailureAlert(applicationName, true, ex.getEntityName(), ex.getErrorKey(), ex.getBody().getTitle());
        ProblemDetail detail = ex.getBody();
        postProcess(detail, request);
        return ResponseEntity.status(ex.getStatusCode()).headers(headers).body(detail);
    }

    /**
     * Handles {@link ConflictException} and returns a 409 Conflict response.
     *
     * @param ex      the conflict exception
     * @param request the current web request
     * @return a {@link ResponseEntity} with problem details and HTTP 409 status
     */
    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleConflictException(ConflictException ex, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setProperty(MESSAGE_KEY, ex.getBody().getTitle());
        detail.setProperty("X-" + applicationName + "-error", "error." + ex.getErrorKey());
        detail.setProperty("X-" + applicationName + "-params", ex.getEntityName());
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleConcurrencyFailure(ConcurrencyFailureException ex, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        detail.setProperty(MESSAGE_KEY, ErrorConstants.ERR_CONCURRENCY_FAILURE);
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleEntityNotFoundException(EntityNotFoundException ex, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        detail.setProperty(MESSAGE_KEY, ErrorConstants.REQ_404_REASON);
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleBadRequest(BadRequestException exception, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        detail.setProperty(MESSAGE_KEY, StringUtils.firstNonBlank(exception.getMessage(), ErrorConstants.REQ_400_REASON));
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(detail);
    }

    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleNotAllowedException(NotAllowedException ex, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED);
        detail.setProperty(MESSAGE_KEY, StringUtils.firstNonBlank(ex.getMessage(), "Method not allowed"));
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(detail);
    }

    /**
     * @param e       a specific exception
     * @param request the request
     * @return the exception wrapped into a http entity
     */
    @ExceptionHandler(IOException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    // taken from https://mtyurt.net/post/spring-how-to-handle-ioexception-broken-pipe.html
    public Object exceptionHandler(IOException e, HttpServletRequest request) {
        if (Strings.CI.contains(ExceptionUtils.getRootCauseMessage(e), "Broken pipe")) {
            log.info("Broken pipe IOException occurred: {}", e.getMessage());
            // socket is closed, cannot return any response
            return null;
        }
        else {
            return new HttpEntity<>(e.getMessage());
        }
    }

    // NoResourceFoundException is handled by the parent ResponseEntityExceptionHandler

    /**
     * Handles {@link RateLimitExceededException} exceptions that occur when a client
     * exceeds the configured rate limit for API requests.
     *
     * <p>
     * This method constructs an HTTP response with status code {@code 429 (Too Many Requests)}.
     * If the thrown exception specifies a retry delay, the response will include a
     * {@code Retry-After} header indicating the number of seconds the client should wait
     * before retrying the request.
     * </p>
     *
     * @param ex the {@link RateLimitExceededException} thrown when the request rate limit is exceeded
     * @return a {@link ResponseEntity} containing a descriptive message ("Too Many Requests"),
     *         optional {@code Retry-After} header, and HTTP status {@code 429 (Too Many Requests)}
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<String> handle(RateLimitExceededException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getRetryAfterSeconds() > 0) {
            headers.add("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        }
        return new ResponseEntity<>("Too Many Requests", headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Handles {@link de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException} and returns a 500 response.
     *
     * @param ex      the Weaviate operation exception
     * @param request the current web request
     * @return a {@link ResponseEntity} with problem details and HTTP 500 status
     */
    @ExceptionHandler
    public ResponseEntity<ProblemDetail> handleWeaviateException(de.tum.cit.aet.artemis.globalsearch.exception.WeaviateException ex, NativeWebRequest request) {
        log.error("Weaviate operation failed: {}", ex.getMessage(), ex);
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        detail.setTitle("Weaviate Error");
        detail.setDetail("An internal error occurred while processing the request");
        detail.setProperty(MESSAGE_KEY, "error.weaviateOperationFailed");
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(detail);
    }

    /**
     * Handles {@link PasskeyAuthenticationException} and returns a 403 Forbidden response.
     *
     * @param ex      the passkey authentication exception
     * @param request the current web request
     * @return a {@link ResponseEntity} with problem details and HTTP 403 status
     */
    @ExceptionHandler(PasskeyAuthenticationException.class)
    public ResponseEntity<ProblemDetail> handlePasskeyAuthenticationException(PasskeyAuthenticationException ex, NativeWebRequest request) {
        ProblemDetail detail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        detail.setTitle("Forbidden");
        detail.setDetail(ex.getMessage());
        detail.setProperty(MESSAGE_KEY, ex.getErrorKey());
        detail.setProperty("reason", ex.getReason().name());
        postProcess(detail, request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(detail);
    }
}
