package de.tum.cit.aet.artemis.communication.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.regex.Pattern;

import jakarta.validation.Valid;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationApiType;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.cit.aet.artemis.communication.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationRegisterBody;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationRegisterDTO;
import de.tum.cit.aet.artemis.communication.dto.PushNotificationUnregisterRequest;
import de.tum.cit.aet.artemis.communication.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.jwt.TokenProvider;
import io.jsonwebtoken.ExpiredJwtException;

/**
 * Rest Controller for managing push notification device tokens for native clients.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/communication/push_notification/")
public class PushNotificationResource {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationResource.class);

    private static final KeyGenerator aesKeyGenerator;

    private static final Pattern VERSION_CODE_PATTERN = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");

    private final TokenProvider tokenProvider;

    static {
        try {
            aesKeyGenerator = KeyGenerator.getInstance("AES");
            aesKeyGenerator.init(256, new SecureRandom());
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository;

    private final UserRepository userRepository;

    public PushNotificationResource(TokenProvider tokenProvider, PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository,
            UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.pushNotificationDeviceConfigurationRepository = pushNotificationDeviceConfigurationRepository;
        this.userRepository = userRepository;
    }

    /**
     * API Endpoint which native clients use to register with their device token to enable push notification support
     *
     * @param pushNotificationRegisterBody contains all information required to store the device token for a specific user
     * @return an DTO containing information about the encryption
     */
    @PostMapping("register")
    @EnforceAtLeastStudent
    public ResponseEntity<PushNotificationRegisterDTO> register(@Valid @RequestBody PushNotificationRegisterBody pushNotificationRegisterBody) {
        var newKey = aesKeyGenerator.generateKey();

        String token = getToken();

        Date expirationDate;

        // This cannot throw an error as it must have been valid to even call this method
        try {
            expirationDate = tokenProvider.getExpirationDate(token);
        }
        catch (ExpiredJwtException e) {
            log.error("Expired token {}", token, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        catch (Exception ex) {
            log.error("Cannot parse token {}", token, ex);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (pushNotificationRegisterBody.versionCode() != null && !VERSION_CODE_PATTERN.matcher(pushNotificationRegisterBody.versionCode()).matches()) {
            throw new IllegalArgumentException("Version code is not valid");
        }

        final var deviceConfiguration = getPushNotificationDeviceConfiguration(pushNotificationRegisterBody, expirationDate, newKey);
        pushNotificationDeviceConfigurationRepository.save(deviceConfiguration);

        var encodedKey = Base64.getEncoder().encodeToString(newKey.getEncoded());

        return ResponseEntity.ok(new PushNotificationRegisterDTO(encodedKey, Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM));
    }

    private PushNotificationDeviceConfiguration getPushNotificationDeviceConfiguration(PushNotificationRegisterBody pushNotificationRegisterBody, Date expirationDate,
            SecretKey newKey) {
        PushNotificationApiType apiType = pushNotificationRegisterBody.apiType() != null ? pushNotificationRegisterBody.apiType() : PushNotificationApiType.DEFAULT;

        User user = userRepository.getUser();

        return new PushNotificationDeviceConfiguration(pushNotificationRegisterBody.token(), pushNotificationRegisterBody.deviceType(), expirationDate, newKey.getEncoded(), user,
                apiType, pushNotificationRegisterBody.versionCode());
    }

    /**
     * API Endpoint used by native clients to unregister for push notifications.
     *
     * @param body contains information on which device token should be removed for what user
     * @return HttpStatus as ResponseEntity
     */
    @DeleteMapping("unregister")
    @EnforceAtLeastStudent
    public ResponseEntity<Void> unregister(@Valid @RequestBody PushNotificationUnregisterRequest body) {
        final var deviceId = new PushNotificationDeviceConfigurationId(userRepository.getUser(), body.token(), body.deviceType());

        if (!pushNotificationDeviceConfigurationRepository.existsById(deviceId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        pushNotificationDeviceConfigurationRepository.deleteById(deviceId);

        return ResponseEntity.ok().build();
    }

    private String getToken() {
        // TODO: we should rather get the token from the cookie, e.g. something like Cookie jwtCookie = WebUtils.getCookie(httpServletRequest, JWT_COOKIE_NAME);
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getCredentials();
    }
}
