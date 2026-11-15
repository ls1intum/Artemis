package de.tum.cit.aet.artemis.localvc.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALVC;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.FormatStyle;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.core.service.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailSendingService;
import de.tum.cit.aet.artemis.programming.dto.GitRepositoryAccessDTO;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;

/**
 * Tells users who authenticate to Git with their account password to switch to a token or an SSH key: by email, and by a message that git prints on a push.
 * <p>
 * Owns the per-user limit of one email per day, which every node of a cluster has to share, and the optional date on which the installation plans to stop accepting
 * passwords.
 */
@Lazy
@Service
@Profile(PROFILE_LOCALVC)
public class PasswordAuthenticationNoticeService {

    private static final Logger log = LoggerFactory.getLogger(PasswordAuthenticationNoticeService.class);

    static final String HTTPS_CLONE_EMAIL_CACHE = "httpsCloneWarningEmailCache";

    private final MailSendingService mailSendingService;

    private final DistributedDataProvider distributedDataProvider;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    /**
     * The day on which this installation plans to stop accepting account passwords for Git, as an ISO date such as {@code 2027-01-15}. Empty announces no date.
     * <p>
     * This only changes what the password notices (the email and the message printed on a push) tell users. Artemis does not reject passwords on that day.
     */
    @Value("${artemis.version-control.password-authentication-end-date:}")
    private String passwordAuthenticationEndDate;

    /**
     * Creates the service.
     *
     * @param mailSendingService            sends the email
     * @param distributedDataProvider       holds the limit of one email per user and day, shared by all nodes
     * @param programmingExerciseRepository reads the titles of the exercise and the course the email names
     */
    public PasswordAuthenticationNoticeService(MailSendingService mailSendingService, DistributedDataProvider distributedDataProvider,
            ProgrammingExerciseRepository programmingExerciseRepository) {
        this.mailSendingService = mailSendingService;
        this.distributedDataProvider = distributedDataProvider;
        this.programmingExerciseRepository = programmingExerciseRepository;
    }

    /**
     * Tells a user who just authenticated with an account password to switch to a token or an SSH key, by email.
     * <p>
     * Any password authentication counts, not only a clone: a user who cloned once and keeps a saved password in the Git client only ever pulls and pushes.
     * At most one email is sent per user and 24 hours, whichever repository or action comes first.
     *
     * @param user     the user who authenticated with their password
     * @param exercise the exercise whose repository the user accessed
     */
    void sendPasswordAuthenticationEmail(User user, GitRepositoryAccessDTO exercise) {
        DistributedMap<Long, Boolean> cache = null;
        boolean slotClaimed = false;
        // A notice must never cost the user their git operation, whatever goes wrong while it is prepared or sent.
        try {
            if (user.getEmail() == null || user.getEmail().isBlank()) {
                return;
            }

            cache = distributedDataProvider.getExpiringMap(HTTPS_CLONE_EMAIL_CACHE, Duration.ofHours(24));
            // putIfAbsent returns null if there was no entry, so only the first password authentication within 24 hours continues
            if (cache.putIfAbsent(user.getId(), Boolean.TRUE, Duration.ofHours(24)) != null) {
                return;
            }
            slotClaimed = true;

            var titles = programmingExerciseRepository.findExerciseAndCourseTitlesById(exercise.exerciseId()).orElse(null);
            Map<String, Object> variables = new HashMap<>();
            variables.put("exerciseTitle", titles != null ? titles.exerciseTitle() : "");
            variables.put("courseTitle", titles != null && titles.courseTitle() != null ? titles.courseTitle() : "");
            variables.put("courseId", exercise.courseId());
            variables.put("exerciseId", exercise.exerciseId());
            // An exam exercise has no page of its own outside the exam, so the email links to its course instead.
            variables.put("examExercise", exercise.examId() != null);
            getPasswordAuthenticationEndDate().ifPresent(endDate -> variables.put("endDate", formatForUser(endDate, user)));

            mailSendingService.buildAndSendAsync(MailRecipientDTO.from(user), "email.httpsCloneTip.title", "mail/httpsCloneTipEmail", variables);
        }
        catch (Exception e) {
            log.warn("Could not send the password authentication email for user {}: {}", user.getId(), e.getMessage());
            // Give back only a slot this attempt claimed, so that the next password authentication can try again instead of waiting a day.
            // A failed claim must not remove the slot of a concurrent request.
            if (slotClaimed) {
                releaseEmailSlot(cache, user);
            }
        }
    }

    /**
     * Gives back the slot of a user after the email could not be prepared or handed over, so that the next password authentication can try again instead of waiting a day.
     *
     * @param cache the map the slot was claimed in, or null if it could not even be reached
     * @param user  the user whose slot is released
     */
    private void releaseEmailSlot(DistributedMap<Long, Boolean> cache, User user) {
        if (cache == null) {
            return;
        }
        try {
            cache.remove(user.getId());
        }
        catch (Exception e) {
            log.warn("Could not release the password authentication email slot of user {}: {}", user.getId(), e.getMessage());
        }
    }

    /**
     * The message git prints to a user who pushed with an account password, as {@code remote: ...} lines.
     *
     * @return the message, including the end date when the installation announces one
     */
    public String getPasswordAuthenticationPushNotice() {
        String notice = "You pushed with your Artemis account password. A password can reach everything your account can reach and stays saved in your Git client. "
                + "Please switch to an access token or an SSH key: open the exercise in Artemis, click the Code button and choose Token or SSH.";
        return getPasswordAuthenticationEndDate().map(endDate -> notice + " Your Artemis administrators plan to stop accepting passwords for Git on " + endDate + ".")
                .orElse(notice);
    }

    /**
     * Rejects a malformed {@code password-authentication-end-date} when the server starts, rather than failing for the first user who authenticates with a password.
     */
    @PostConstruct
    void validatePasswordAuthenticationEndDate() {
        getPasswordAuthenticationEndDate();
    }

    /**
     * Reads the announced end date of password authentication.
     *
     * @return the date, or empty if the installation announces none
     * @throws IllegalStateException if the configured value is no ISO date
     */
    private Optional<LocalDate> getPasswordAuthenticationEndDate() {
        if (passwordAuthenticationEndDate == null || passwordAuthenticationEndDate.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(passwordAuthenticationEndDate.strip()));
        }
        catch (DateTimeParseException e) {
            throw new IllegalStateException(
                    "artemis.version-control.password-authentication-end-date must be an ISO date such as 2027-01-15, but was '" + passwordAuthenticationEndDate + "'", e);
        }
    }

    /**
     * Formats a date in the language of a user.
     *
     * @param date the date
     * @param user the user, whose language key decides the language, with English as the fallback
     * @return the date in its long form, for example "15 December 2026"
     */
    private static String formatForUser(LocalDate date, User user) {
        Locale locale = user.getLangKey() != null ? Locale.forLanguageTag(user.getLangKey()) : Locale.ENGLISH;
        return date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale));
    }
}
