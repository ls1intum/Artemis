package de.tum.in.www1.artemis.web.rest.push_notification;

import java.security.NoSuchAlgorithmException;
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

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfiguration;
import de.tum.in.www1.artemis.domain.push_notification.PushNotificationDeviceConfigurationId;
import de.tum.in.www1.artemis.repository.PushNotificationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import io.jsonwebtoken.*;

@RestController
@RequestMapping("/api/push_notification")
public class PushNotificationResource {

    private static String algorithm = "AES/CBC/PKCS5Padding";

    private static KeyGenerator aesKeyGenerator;

    static {
        try {
            aesKeyGenerator = KeyGenerator.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private PushNotificationRepository pushNotificationRepository;

    private UserRepository userRepository;

    public PushNotificationResource(PushNotificationRepository pushNotificationRepository, UserRepository userRepository) {
        this.pushNotificationRepository = pushNotificationRepository;
        this.userRepository = userRepository;
    }

    @PostMapping("register")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<PushNotificationRegisterDTO> register(@Valid @RequestBody PushNotificationRegisterBody pushNotificationRegisterBody) {
        var newKey = aesKeyGenerator.generateKey();

        String token = getToken();

        Jwt<Header, Claims> headerClaimsJwt;

        // This cannot throw an error as it must have been valid to even call this method
        try {
            headerClaimsJwt = Jwts.parserBuilder().build().parseClaimsJwt(token);
        }
        catch (ExpiredJwtException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Date expirationDate = headerClaimsJwt.getBody().getExpiration();

        User user = userRepository.getUser();

        // DB CALL INSERT OR UPDATE
        pushNotificationRepository.save(new PushNotificationDeviceConfiguration(pushNotificationRegisterBody.getToken(), pushNotificationRegisterBody.getDeviceType(),
                expirationDate, newKey.getEncoded(), user));

        var encodedKey = Base64.getEncoder().encodeToString(newKey.getEncoded());

        return ResponseEntity.ok(new PushNotificationRegisterDTO(encodedKey, algorithm));
    }

    @DeleteMapping("unregister")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Void> unregister(@Valid @RequestBody PushNotificationUnregisterRequest body) {
        final var id = new PushNotificationDeviceConfigurationId(userRepository.getUser(), body.getToken(), body.getDeviceType());

        if (!pushNotificationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        pushNotificationRepository.deleteById(id);

        return ResponseEntity.ok().build();
    }

    private static String getToken() {
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        String token = (String) auth.getCredentials();
        return token;
    }
}
