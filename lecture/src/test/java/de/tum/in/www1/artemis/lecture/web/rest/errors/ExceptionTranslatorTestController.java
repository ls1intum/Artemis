package de.tum.in.www1.artemis.lecture.web.rest.errors;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/exception-translator-test")
public class ExceptionTranslatorTestController {

    /**
     * Test endpoint
     */
    @PostMapping("/method-argument")
    public void methodArgument(@Valid @RequestBody TestDTO testDTO) {
    }

    /**
     * Test endpoint
     */
    @GetMapping("/missing-servlet-request-part")
    public void missingServletRequestPartException(@RequestPart String part) {
    }

    /**
     * Test endpoint
     */
    @GetMapping("/missing-servlet-request-parameter")
    public void missingServletRequestParameterException(@RequestParam String param) {
    }

    @GetMapping("/access-denied")
    public void accessdenied() {
        throw new AccessDeniedException("test access denied!");
    }

    @GetMapping("/unauthorized")
    public void unauthorized() {
        throw new BadCredentialsException("test authentication failed!");
    }

    @GetMapping("/response-status")
    public void exceptionWithResponseStatus() {
        throw new TestResponseStatusException();
    }

    @GetMapping("/internal-server-error")
    public void internalServerError() {
        throw new RuntimeException();
    }

    public static class TestDTO {

        @NotNull
        private String test;

        public String getTest() {
            return test;
        }

        public void setTest(String test) {
            this.test = test;
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "test response status")
    @SuppressWarnings("serial")
    public static class TestResponseStatusException extends RuntimeException {
    }
}
