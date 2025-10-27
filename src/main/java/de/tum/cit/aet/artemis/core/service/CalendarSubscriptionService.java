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

import de.tum.cit.aet.artemis.core.domain.CalendarSubscriptionTokenStore;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.repository.CalendarSubscriptionTokenStoreRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.util.CalendarEventType;
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

    private final UserRepository userRepository;

    private final CalendarSubscriptionTokenStoreRepository calendarSubscriptionTokenStoreRepository;

    private final String artemisServerUrl;

    CalendarSubscriptionService(UserRepository userRepository, CalendarSubscriptionTokenStoreRepository calendarSubscriptionTokenStoreRepository,
            @Value("${server.url}") String artemisServerUrl) {
        this.userRepository = userRepository;
        this.calendarSubscriptionTokenStoreRepository = calendarSubscriptionTokenStoreRepository;
        this.artemisServerUrl = artemisServerUrl;
    }

    /**
     * Uses a CSPRNG that is part of SecureRandom to generate a cryptographically secure, random subscription token.
     * Each token is a string of 32 lower-case, hexadecimal characters.
     * Attempts to generate a unique token 10 times, then throws (should never happen in practice because of astronomical unlikeliness).
     *
     * @param userLogin the login of the {@link User} for whom the subscription token is requested
     * @return the generated token
     */
    public String createSubscriptionTokenForUser(String userLogin) {
        User user = userRepository.getUserByLoginElseThrow(userLogin);
        final int MAXIMUM_ATTEMPT_NUMBER = 10;
        for (int attempt = 1; attempt <= MAXIMUM_ATTEMPT_NUMBER; attempt++) {
            String token = convertBytesToSubscriptionToken(generateSubscriptionTokenBytes());
            CalendarSubscriptionTokenStore store = new CalendarSubscriptionTokenStore();
            store.setToken(token);
            store.setUser(user);
            try {
                calendarSubscriptionTokenStoreRepository.saveAndFlush(store);
                return token;
            }
            catch (DataIntegrityViolationException ignored) {
            }
        }
        throw new IllegalStateException("Could not generate a unique calendar subscription token after " + MAXIMUM_ATTEMPT_NUMBER + " attempts");
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

    /**
     * Builds an iCalendar (.ics) file as a string for the given course and language,
     * including all provided calendar events.
     * <p>
     * Each {@link CalendarEventDTO} is converted into a {@link VEvent} with a stable
     * UUID, a summary, a start and end date and optionally location/contact details.
     * </p>
     *
     * @param courseShortName   short name of the course, included in the summary
     * @param language          used to localize the summary
     * @param calendarEventDTOs set of events that is converted into Vevents ans included in the .ics file
     * @return the .ics file content as a string
     */
    public String getICSFileAsString(String courseShortName, Language language, Set<CalendarEventDTO> calendarEventDTOs) {
        Calendar calendar = new Calendar();
        calendar.add(new ProdId("-//TUM//Artemis//" + language.getShortName().toUpperCase()));
        calendar.add(ImmutableVersion.VERSION_2_0);
        calendar.add(ImmutableCalScale.GREGORIAN);
        calendar.add(ImmutableMethod.PUBLISH);

        calendarEventDTOs.forEach(calendarEventDTO -> calendar.add(getVEventFrom(calendarEventDTO, courseShortName, language)));

        return calendar.toString();
    }

    private VEvent getVEventFrom(CalendarEventDTO calendarEventDTO, String courseShortName, Language language) {
        Instant start = calendarEventDTO.startDate().toInstant();
        Instant end = calendarEventDTO.endDate() != null ? calendarEventDTO.endDate().toInstant() : start;
        String eventType = getEventTypeDescription(language, calendarEventDTO.type());
        String title = courseShortName + " " + eventType + " | " + calendarEventDTO.title();
        VEvent event = new VEvent(start, end, title);

        String stableUniqueEventKey = calendarEventDTO.id() + "|" + artemisServerUrl;
        UUID uuid = UUID.nameUUIDFromBytes(stableUniqueEventKey.getBytes(StandardCharsets.UTF_8));
        event.add(new Uid("urn:uuid:" + uuid));

        if (calendarEventDTO.location() != null) {
            event.add(new Location(calendarEventDTO.location()));
        }

        if (calendarEventDTO.facilitator() != null && !calendarEventDTO.facilitator().isBlank()) {
            event.add(new Contact(calendarEventDTO.facilitator()));
        }

        return event;
    }

    private String getEventTypeDescription(Language language, CalendarEventType calendarEventType) {
        return switch (language) {
            case GERMAN -> getGermanEventTypeDescription(calendarEventType);
            case ENGLISH -> getEnglishEventTypeDescription(calendarEventType);
        };
    }

    private String getGermanEventTypeDescription(CalendarEventType calendarEventType) {
        return switch (calendarEventType) {
            case LECTURE -> "Vorlesung";
            case TUTORIAL -> "Tutorium";
            case EXAM -> "Klausur";
            default -> "Aufgabe";
        };
    }

    private String getEnglishEventTypeDescription(CalendarEventType calendarEventType) {
        return switch (calendarEventType) {
            case LECTURE -> "Lecture";
            case TUTORIAL -> "Tutorial";
            case EXAM -> "Exam";
            default -> "Exercise";
        };
    }
}
