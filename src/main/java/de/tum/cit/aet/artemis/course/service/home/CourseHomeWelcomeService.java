package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.CourseHomeWelcomeSource;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeGeneratedWelcomeDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeQueryDTO;
import de.tum.cit.aet.artemis.course.dto.home.CourseHomeWelcomeRequestDTO;
import io.micrometer.core.instrument.MeterRegistry;

/** Optional encouragement only. Authoritative facts and navigation stay deterministic. */
@Service
@Lazy
@Profile(PROFILE_CORE)
public class CourseHomeWelcomeService {

    private static final Pattern UNSAFE = Pattern.compile(
            "(?iu)(https?://|www\\.|[<>`\\[\\]{}*#]|\\b(?:[\\p{L}\\d-]+\\.)+[\\p{L}]{2,}\\b|\\b(?:today|tomorrow|tonight|yesterday|heute|morgen|gestern|übermorgen|noon|midnight|morning|afternoon|evening|vormittag|nachmittag|abend|mitternacht|hours?|minutes?|days?|weeks?|months?|years?|stunden?|minuten?|tagen?|wochen?|monate[n]?|jahre[n]?|deadline|abgabefrist|monday|tuesday|wednesday|thursday|friday|saturday|sunday|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag)\\b|\\b(?:next|this)\\s+(?:week|month|year)|\\b(?:nächste|diese|kommende)\\w*\\s+(?:woche|monat|jahr))");

    private static final Pattern CLOCK_TIME = Pattern
            .compile("(?iu)(\\buhr\\b|\\b(?:at|um|gegen)\\s+(?:(?:half(?:\\s+past)?|quarter(?:\\s+(?:past|to))?|halb|viertel(?:\\s+(?:nach|vor))?|dreiviertel)\\s+)?"
                    + "(?:zero|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|twenty|eins|ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|dreizehn|vierzehn|fünfzehn|sechzehn|siebzehn|achtzehn|neunzehn|zwanzig|(?:ein|zwei|drei|vier)undzwanzig)\\b)");

    private final CourseHomeService home;

    private final CourseHomeAiPolicyService policy;

    private final CourseHomeAiPromptBuilderService prompts;

    private final Optional<CourseHomeAiClient> client;

    private final MeterRegistry meters;

    public CourseHomeWelcomeService(CourseHomeService home, CourseHomeAiPolicyService policy, CourseHomeAiPromptBuilderService prompts, Optional<CourseHomeAiClient> client,
            MeterRegistry meters) {
        this.home = home;
        this.policy = policy;
        this.prompts = prompts;
        this.client = client;
        this.meters = meters;
    }

    /**
     * Generates optional encouragement after validating current access, context and consent.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated user
     * @param request  the fingerprint and requested language
     * @param asOf     the single server timestamp for this snapshot
     * @return a future containing validated encouragement or the fallback marker
     */
    public CompletableFuture<CourseHomeGeneratedWelcomeDTO> generate(long courseId, User user, CourseHomeWelcomeRequestDTO request, Instant asOf) {
        if (client.isEmpty() || !policy.canGenerate(user.getId())) {
            return CompletableFuture.completedFuture(fallback(request, "ineligible"));
        }
        // Authorization is intentionally outside the provider-failure boundary.
        var current = home.load(courseId, user, new CourseHomeQueryDTO(null), asOf);
        if (!current.welcome().aiEligible() || !current.welcome().contextFingerprint().equals(request.contextFingerprint())) {
            return CompletableFuture.completedFuture(fallback(request, "changed_context"));
        }
        CourseHomeAiClient.Input input;
        try {
            input = prompts.build(current, request.locale());
        }
        catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(fallback(request, "prompt"));
        }
        if (!policy.canGenerate(user.getId())) {
            return CompletableFuture.completedFuture(fallback(request, "revoked"));
        }
        try {
            return client.get().generate(input).handle((output, error) -> {
                if (error != null || output == null || output.isEmpty()) {
                    return fallback(request, "provider");
                }
                if (!policy.canGenerate(user.getId())) {
                    return fallback(request, "revoked");
                }
                String text = output.get().strip();
                if (!valid(text)) {
                    return fallback(request, "invalid_output");
                }
                meters.counter("artemis.course.home.welcome", "outcome", "ai", "reason", "generated").increment();
                return new CourseHomeGeneratedWelcomeDTO(request.contextFingerprint(), text, CourseHomeWelcomeSource.AI);
            });
        }
        catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(fallback(request, "provider"));
        }
    }

    /**
     * Bounds the optional wording wait independently from the core Home request.
     *
     * @param courseId the authorized course identifier
     * @param user     the authenticated student
     * @param request  the requested fingerprint and language
     * @param asOf     the snapshot timestamp
     * @return generated encouragement or the deterministic marker, with interrupts preserved
     */
    public CourseHomeGeneratedWelcomeDTO generateBounded(long courseId, User user, CourseHomeWelcomeRequestDTO request, Instant asOf) {
        try {
            return generate(courseId, user, request, asOf).get(3500, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return fallback(request, "interrupted");
        }
        catch (ExecutionException | TimeoutException ex) {
            return fallback(request, "deadline");
        }
    }

    private boolean valid(String text) {
        return !text.isBlank() && text.length() <= 500 && text.codePoints().anyMatch(Character::isLetter) && text.codePoints().noneMatch(Character::isDigit)
                && text.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) && !text.startsWith("\"") && !UNSAFE.matcher(text).find()
                && !CLOCK_TIME.matcher(text).find();
    }

    private CourseHomeGeneratedWelcomeDTO fallback(CourseHomeWelcomeRequestDTO request, String reason) {
        meters.counter("artemis.course.home.welcome", "outcome", "fallback", "reason", reason).increment();
        return new CourseHomeGeneratedWelcomeDTO(request.contextFingerprint(), null, CourseHomeWelcomeSource.FALLBACK);
    }
}
