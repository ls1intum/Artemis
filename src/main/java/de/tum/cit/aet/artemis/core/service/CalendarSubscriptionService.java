package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.security.SecureRandom;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;

@Lazy
@Service
@Profile(PROFILE_CORE)
public class CalendarSubscriptionService {

    public enum CalendarEventFilterOption {
        LECTURES, TUTORIALS, EXAMS, EXERCISES
    }

    private final UserRepository userRepository;

    CalendarSubscriptionService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String getICSFileAsString(Set<CalendarEventDTO> calendarEventDTOs) {
        return "";
    }

    public String getOrCreateSubscriptionTokenFor(User user) {
        String token = user.getCalendarSubscriptionToken();
        if (token == null) {
            token = generateUniqueSubscriptionTokenFor(user);
        }
        return token;
    }

    private String generateUniqueSubscriptionTokenFor(User user) {
        String token;
        boolean generationSuccessful;
        do {
            token = convertBytesToSubscriptionToken(generateSubscriptionTokenBytes());
            user.setCalendarSubscriptionToken(token);
            try {
                userRepository.saveAndFlush(user);
                generationSuccessful = true;
            }
            catch (DataIntegrityViolationException ex) {
                generationSuccessful = false;
            }
        }
        while (!generationSuccessful);
        return token;
    }

    private byte[] generateSubscriptionTokenBytes() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return bytes;
    }

    private String convertBytesToSubscriptionToken(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }
}
