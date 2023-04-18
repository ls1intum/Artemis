package de.tum.in.www1.artemis.web.rest.push_notification;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;

import javax.crypto.KeyGenerator;
import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.in.www1.artemis.repository.PushNotificationDeviceConfigurationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import io.jsonwebtoken.*;

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

    @PostMapping("register")
    @PreAuthorize("hasRole('USER')")
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

        // DB CALL INSERT OR UPDATE
        pushNotificationDeviceConfigurationRepository.save(new PushNotificationDeviceConfiguration(pushNotificationRegisterBody.token(), pushNotificationRegisterBody.deviceType(),
                expirationDate, newKey.getEncoded(), user));

        var encodedKey = Base64.getEncoder().encodeToString(newKey.getEncoded());

        return ResponseEntity.ok(new PushNotificationRegisterDTO(encodedKey, Constants.PUSH_NOTIFICATION_ENCRYPTION_ALGORITHM));
    }

    @DeleteMapping("unregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unregister(@Valid @RequestBody PushNotificationUnregisterRequest body) {
        final var id = new PushNotificationDeviceConfigurationId(userRepository.getUser(), body.getToken(), body.getDeviceType());

        if (!pushNotificationDeviceConfigurationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        pushNotificationDeviceConfigurationRepository.deleteById(id);

        return ResponseEntity.ok().build();
    }

    private String getToken() {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String token = (String) auth.getCredentials();
        return token;
    }
}
