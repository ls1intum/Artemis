package de.tum.in.www1.artemis.web.rest.push_notification;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.EnforceAtLeastStudent;
import io.jsonwebtoken.*;

/**
 * Rest Controller for managing push notification device tokens for native clients.
 */
@RestController
@RequestMapping("/api/push_notification")
public class PushNotificationResource {

    private static KeyGenerator aesKeyGenerator;

    static {
        try {
            aesKeyGenerator = KeyGenerator.getInstance("AES");
            aesKeyGenerator.init(256, new SecureRandom());
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository;

    private UserRepository userRepository;

    public PushNotificationResource(PushNotificationDeviceConfigurationRepository pushNotificationDeviceConfigurationRepository, UserRepository userRepository) {
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

        String jwtWithoutSignature = token.substring(0, token.lastIndexOf('.') + 1);

        Jwt<Header, Claims> headerClaimsJwt;

        // This cannot throw an error as it must have been valid to even call this method
        try {
            headerClaimsJwt = Jwts.parserBuilder().build().parseClaimsJwt(jwtWithoutSignature);
        }
        catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Date expirationDate = headerClaimsJwt.getBody().getExpiration();

        User user = userRepository.getUser();

        PushNotificationDeviceConfiguration deviceConfiguration = new PushNotificationDeviceConfiguration(pushNotificationRegisterBody.token(),
                pushNotificationRegisterBody.deviceType(), expirationDate, newKey.getEncoded(), user);
        pushNotificationDeviceConfigurationRepository.save(deviceConfiguration);

        var encodedKey = Base64.getEncoder().encodeToString(newKey.getEncoded());

        return ResponseEntity.ok(new PushNotificationRegisterDTO(encodedKey, Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM));
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
        final var id = new PushNotificationDeviceConfigurationId(userRepository.getUser(), body.token(), body.deviceType());

        if (!pushNotificationDeviceConfigurationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        pushNotificationDeviceConfigurationRepository.deleteById(id);

        return ResponseEntity.ok().build();
    }

    private String getToken() {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        return (String) auth.getCredentials();
    }
}
