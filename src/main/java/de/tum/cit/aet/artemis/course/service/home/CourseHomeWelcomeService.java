package de.tum.cit.aet.artemis.course.service.home;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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

    public CompletableFuture<CourseHomeGeneratedWelcomeDTO> generate(long courseId, User user, CourseHomeWelcomeRequestDTO request, Instant asOf) {
        if (client.isEmpty() || !policy.canGenerate(user.getId()))
            return CompletableFuture.completedFuture(fallback(request, "ineligible"));
        // Authorization is intentionally outside the provider-failure boundary.
        var current = home.load(courseId, user, new CourseHomeQueryDTO(null), asOf);
        if (!current.welcome().aiEligible() || !current.welcome().contextFingerprint().equals(request.contextFingerprint()))
            return CompletableFuture.completedFuture(fallback(request, "changed_context"));
        CourseHomeAiClient.Input input;
        try {
            input = prompts.build(current, request.locale());
        }
        catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(fallback(request, "prompt"));
        }
        if (!policy.canGenerate(user.getId()))
            return CompletableFuture.completedFuture(fallback(request, "revoked"));
        try {
            return client.get().generate(input).handle((output, error) -> {
                if (error != null || output == null || output.isEmpty())
                    return fallback(request, "provider");
                if (!policy.canGenerate(user.getId()))
                    return fallback(request, "revoked");
                String text = output.get().strip();
                if (!valid(text))
                    return fallback(request, "invalid_output");
                meters.counter("artemis.course.home.welcome", "outcome", "ai", "reason", "generated").increment();
                return new CourseHomeGeneratedWelcomeDTO(request.contextFingerprint(), text, CourseHomeWelcomeSource.AI);
            });
        }
        catch (RuntimeException ex) {
            return CompletableFuture.completedFuture(fallback(request, "provider"));
        }
    }

    private boolean valid(String text) {
        return !text.isBlank() && text.length() <= 500 && text.codePoints().anyMatch(Character::isLetter) && text.codePoints().noneMatch(Character::isDigit)
                && text.codePoints().noneMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT) && !text.startsWith("\"")
                && !UNSAFE.matcher(text).find();
    }

    private CourseHomeGeneratedWelcomeDTO fallback(CourseHomeWelcomeRequestDTO request, String reason) {
        meters.counter("artemis.course.home.welcome", "outcome", "fallback", "reason", reason).increment();
        return new CourseHomeGeneratedWelcomeDTO(request.contextFingerprint(), null, CourseHomeWelcomeSource.FALLBACK);
    }
}
