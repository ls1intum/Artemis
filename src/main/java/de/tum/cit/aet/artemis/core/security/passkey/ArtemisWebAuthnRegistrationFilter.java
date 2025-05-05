package de.tum.cit.aet.artemis.core.security.passkey;

import java.io.IOException;
import java.util.HashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Custom WebAuthn registration filter for Artemis that extends Spring Security's WebAuthnRegistrationFilter.
 * This filter sends email notifications to non-internal users when they successfully register a new passkey.
 */
public class ArtemisWebAuthnRegistrationFilter extends WebAuthnRegistrationFilter {

    private final MailSendingService mailSendingService;

    private final UserRepository userRepository;

    public ArtemisWebAuthnRegistrationFilter(@NotNull UserCredentialRepository userCredentials, @NotNull WebAuthnRelyingPartyOperations rpOptions,
            MailSendingService mailSendingService, UserRepository userRepository) {
        super(userCredentials, rpOptions);
        this.mailSendingService = mailSendingService;
        this.userRepository = userRepository;
    }

    /**
     * Processes HTTP requests that pass through this filter.
     * Extends the parent implementation to send notification emails when a passkey is registered successfully.
     *
     * @param request     The HTTP servlet request
     * @param response    The HTTP servlet response
     * @param filterChain The filter chain for invoking the next filter
     * @throws ServletException If a servlet exception occurs
     * @throws IOException      If an I/O error occurs
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        super.doFilterInternal(request, response, filterChain);

        if (isWebAuthnRegistrationRequest(request) && response.getStatus() == HttpStatus.OK.value()) {
            var recipient = userRepository.getUser();

            if (!recipient.isInternal()) {
                mailSendingService.buildAndSendAsync(recipient, "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());
            }
        }
    }

    /**
     * Determines if the current request is a WebAuthn registration request.
     * A request is considered a WebAuthn registration request if it is a POST request
     * to a URI ending with "/register".
     *
     * @param request The HTTP servlet request to check
     * @return true if the request is a WebAuthn registration request, false otherwise
     */
    private boolean isWebAuthnRegistrationRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/register") && request.getMethod().equals("POST");
    }
}
