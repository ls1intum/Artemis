package de.tum.cit.aet.artemis.core.security.passkey;

import java.io.IOException;
import java.util.HashMap;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

import de.tum.cit.aet.artemis.communication.domain.GlobalNotificationType;
import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

/**
 * Custom WebAuthn registration filter for Artemis that extends Spring Security's {@link WebAuthnRegistrationFilter}.
 * This filter sends email notifications to users when they successfully register a new passkey.
 */
public class ArtemisWebAuthnRegistrationFilter extends WebAuthnRegistrationFilter {

    static final String DEFAULT_REGISTER_CREDENTIAL_URL = "/webauthn/register";

    private final PathPatternRequestMatcher registerCredentialMatcher = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, DEFAULT_REGISTER_CREDENTIAL_URL);

    private final MailSendingService mailSendingService;

    private final UserRepository userRepository;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    public ArtemisWebAuthnRegistrationFilter(@NotNull UserCredentialRepository userCredentials, @NotNull WebAuthnRelyingPartyOperations rpOptions,
            MailSendingService mailSendingService, UserRepository userRepository, GlobalNotificationSettingRepository globalNotificationSettingRepository) {
        super(userCredentials, rpOptions);
        this.mailSendingService = mailSendingService;
        this.userRepository = userRepository;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
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
            User recipient = userRepository.getUser();

            if (globalNotificationSettingRepository.isNotificationEnabled(recipient.getId(), GlobalNotificationType.NEW_PASSKEY_ADDED)) {
                mailSendingService.buildAndSendAsync(recipient, "email.notification.newPasskey.title", "mail/notification/newPasskeyEmail", new HashMap<>());
            }
        }
    }

    /**
     * Determines if the current request is a WebAuthn registration request as defined by {@link WebAuthnRegistrationFilter}.
     * A request is considered a WebAuthn registration request if it is a POST request
     * to a URI ending with "webauthn/register".
     *
     * @param request The HTTP servlet request to check
     * @return true if the request is a WebAuthn registration request, false otherwise
     */
    private boolean isWebAuthnRegistrationRequest(HttpServletRequest request) {
        return registerCredentialMatcher.matches(request) && request.getMethod().equals(HttpMethod.POST.name());
    }
}
