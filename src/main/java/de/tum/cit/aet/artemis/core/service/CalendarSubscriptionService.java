package de.tum.cit.aet.artemis.core.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Contact;
import net.fortuna.ical4j.model.property.Location;
import net.fortuna.ical4j.model.property.ProdId;
import net.fortuna.ical4j.model.property.Uid;
import net.fortuna.ical4j.model.property.immutable.ImmutableCalScale;
import net.fortuna.ical4j.model.property.immutable.ImmutableMethod;
import net.fortuna.ical4j.model.property.immutable.ImmutableVersion;

@Lazy
@Service
@Profile(PROFILE_CORE)
public class CalendarSubscriptionService {

    public enum CalendarEventFilterOption {
        LECTURES, TUTORIALS, EXAMS, EXERCISES
    }

    private final UserRepository userRepository;

    private final String artemisServerUrl;

    CalendarSubscriptionService(UserRepository userRepository, @Value("${server.url}") String artemisServerUrl) {
        this.userRepository = userRepository;
        this.artemisServerUrl = artemisServerUrl;
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

    public String getICSFileAsString(Language language, Set<CalendarEventDTO> calendarEventDTOs) {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//TUM//Artemis//" + language.getShortName().toUpperCase()));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(ImmutableMethod.PUBLISH);

        calendarEventDTOs.forEach(calendarEventDTO -> calendar.add(getVEventFrom(calendarEventDTO)));

        return calendar.toString();
    }

    private VEvent getVEventFrom(CalendarEventDTO calendarEventDTO) {
        Instant start = calendarEventDTO.startDate().toInstant();
        Instant end = calendarEventDTO.endDate() != null ? calendarEventDTO.endDate().toInstant() : null;

        VEvent event = (end != null) ? new VEvent(start, end, calendarEventDTO.title()) : new VEvent(start, calendarEventDTO.title());

        String stableUniqueEventKey = calendarEventDTO.id() + "|" + artemisServerUrl;
        UUID uuid = UUID.nameUUIDFromBytes(stableUniqueEventKey.getBytes(StandardCharsets.UTF_8));
        event.add(new Uid("urn:uuid:" + uuid.toString()));

        if (calendarEventDTO.location() != null) {
            event.add(new Location(calendarEventDTO.location()));
        }

        if (calendarEventDTO.facilitator() != null && !calendarEventDTO.facilitator().isBlank()) {
            event.add(new Contact(calendarEventDTO.facilitator()));
        }

        return event;
    }
}
