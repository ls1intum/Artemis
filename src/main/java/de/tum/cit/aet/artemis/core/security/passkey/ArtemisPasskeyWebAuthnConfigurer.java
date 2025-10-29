package de.tum.cit.aet.artemis.core.security.passkey;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.WebAuthnConfigurer;
import org.springframework.security.web.webauthn.authentication.PublicKeyCredentialRequestOptionsRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.registration.PublicKeyCredentialCreationOptionsRepository;
import org.springframework.stereotype.Component;

import de.tum.cit.aet.artemis.communication.repository.GlobalNotificationSettingRepository;
import de.tum.cit.aet.artemis.communication.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.config.PasskeyEnabled;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.jwt.JWTCookieService;
import de.tum.cit.aet.artemis.core.service.AndroidFingerprintService;
import de.tum.cit.aet.artemis.core.service.ArtemisSuccessfulLoginService;
import de.tum.cit.aet.artemis.core.util.AndroidApkKeyHashUtil;

/**
 * Configurer for WebAuthn passkey authentication in Artemis.
 */
@Component
@Conditional(PasskeyEnabled.class)
@Lazy
public class ArtemisPasskeyWebAuthnConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ArtemisPasskeyWebAuthnConfigurer.class);

    private final AuditEventRepository auditEventRepository;

    private final HttpMessageConverter<Object> converter;

    private final JWTCookieService jwtCookieService;

    private final UserRepository userRepository;

    private final PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository;

    private final UserCredentialRepository userCredentialRepository;

    private final PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository;

    private final PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository;

    private final AndroidFingerprintService androidFingerprintService;

    private final MailSendingService mailSendingService;

    private final ArtemisSuccessfulLoginService artemisSuccessfulLoginService;

    private final GlobalNotificationSettingRepository globalNotificationSettingRepository;

    private final de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository passkeyCredentialsRepository;

    @Value("${" + Constants.PASSKEY_ENABLED_PROPERTY_NAME + ":false}")
    private boolean passkeyEnabled;

    /**
     * We expect the server URL to equal the client URL
     */
    @Value("${server.url}")
    private String serverUrl;

    @Value("${client.port:${server.port}}")
    private String port;

    private final Set<String> allowedOrigins = new HashSet<>();

    private String relyingPartyId;

    private final String relyingPartyName = "Artemis";

    public ArtemisPasskeyWebAuthnConfigurer(AuditEventRepository auditEventRepository, MappingJackson2HttpMessageConverter converter, JWTCookieService jwtCookieService,
            UserRepository userRepository, PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository, UserCredentialRepository userCredentialRepository,
            PublicKeyCredentialCreationOptionsRepository publicKeyCredentialCreationOptionsRepository,
            PublicKeyCredentialRequestOptionsRepository publicKeyCredentialRequestOptionsRepository, AndroidFingerprintService androidFingerprintService,
            MailSendingService mailSendingService, ArtemisSuccessfulLoginService artemisSuccessfulLoginService,
            GlobalNotificationSettingRepository globalNotificationSettingRepository,
            de.tum.cit.aet.artemis.core.repository.PasskeyCredentialsRepository passkeyCredentialsRepository) {
        this.auditEventRepository = auditEventRepository;
        this.converter = converter;
        this.jwtCookieService = jwtCookieService;
        this.userRepository = userRepository;
        this.publicKeyCredentialUserEntityRepository = publicKeyCredentialUserEntityRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.publicKeyCredentialCreationOptionsRepository = publicKeyCredentialCreationOptionsRepository;
        this.publicKeyCredentialRequestOptionsRepository = publicKeyCredentialRequestOptionsRepository;
        this.androidFingerprintService = androidFingerprintService;
        this.mailSendingService = mailSendingService;
        this.artemisSuccessfulLoginService = artemisSuccessfulLoginService;
        this.globalNotificationSettingRepository = globalNotificationSettingRepository;
        this.passkeyCredentialsRepository = passkeyCredentialsRepository;
    }

    /**
     * Validates the configuration for allowed origins when passkey authentication is enabled.
     * <p>
     * This method ensures that the server URL and port are correctly configured for WebAuthn
     * when passkey authentication is enabled. If the configuration is invalid, an exception is thrown.
     * </p>
     *
     * @throws IllegalStateException if the server URL configuration is invalid
     */
    @PostConstruct
    public void validatePasskeyAllowedOriginConfiguration() {
        if (!passkeyEnabled) {
            return;
        }

        try {
            URL clientUrlToRegisterPasskey = new URI(serverUrl).toURL();
            URL clientUrlToAuthenticateWithPasskey = new URI(serverUrl + ":" + port).toURL();

            relyingPartyId = clientUrlToRegisterPasskey.getHost();

            allowedOrigins.add(clientUrlToRegisterPasskey.toString());
            allowedOrigins.add(clientUrlToAuthenticateWithPasskey.toString());

            addAndroidApkKeyHashesToAllowedOrigins();

            log.debug("WebAuthn passkey authentication enabled with RP ID: {}", relyingPartyId);
            log.debug("Allowed origins: {}", allowedOrigins);
        }
        catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Invalid server URL configuration for WebAuthn: " + e.getMessage(), e);
        }
    }

    /**
     * Adds Android APK key hashes to allowed origins if configured.
     */
    private void addAndroidApkKeyHashesToAllowedOrigins() {
        List<String> fingerprints = androidFingerprintService.getFingerprints();
        if (fingerprints.isEmpty()) {
            log.warn("No Android APK key hashes configured. Passkey authentication will not work for Android.");
            return;
        }

        List<String> keyHashes = fingerprints.stream().map(AndroidApkKeyHashUtil::getHashFromFingerprint).toList();

        allowedOrigins.addAll(keyHashes);
    }

    /**
     * Configures HttpSecurity with WebAuthn if passkey authentication is enabled.
     *
     * @param http The HttpSecurity to configure
     * @return true if passkey authentication was configured, false otherwise
     */
    public boolean configure(HttpSecurity http) throws Exception {
        if (!passkeyEnabled) {
            return false;
        }

        WebAuthnConfigurer<HttpSecurity> webAuthnConfigurer = new ArtemisWebAuthnConfigurer<>(auditEventRepository, converter, jwtCookieService, userRepository,
                publicKeyCredentialUserEntityRepository, userCredentialRepository, publicKeyCredentialCreationOptionsRepository, publicKeyCredentialRequestOptionsRepository,
                mailSendingService, artemisSuccessfulLoginService, globalNotificationSettingRepository, passkeyCredentialsRepository);

        http.with(webAuthnConfigurer, configurer -> {
            configurer.allowedOrigins(allowedOrigins).rpId(relyingPartyId).rpName(relyingPartyName);
        });

        return true;
    }
}
