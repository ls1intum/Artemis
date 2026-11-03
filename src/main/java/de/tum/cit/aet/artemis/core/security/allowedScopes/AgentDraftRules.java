package de.tum.cit.aet.artemis.core.security.allowedScopes;

import java.time.ZonedDateTime;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.context.SecurityContextHolder;

import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;

/**
 * The rules that keep content created or changed with a personal access token a draft until a person publishes it.
 * <p>
 * Students see an exercise or lecture unit once its release date is null or has passed, so a draft is one whose release date is
 * in the future. All rules apply only to requests authenticated with a token; a request from the web interface is never
 * affected, which is where publishing stays.
 */
public final class AgentDraftRules {

    private static final String ENTITY_NAME = "agentAccess";

    private AgentDraftRules() {
    }

    /**
     * Whether the current request is authenticated with a personal access token.
     *
     * @return true for a token request
     */
    public static boolean isTokenRequest() {
        return SecurityContextHolder.getContext().getAuthentication() instanceof PersonalAccessTokenAuthentication;
    }

    /**
     * Keeps exam content out of reach of tokens. Exams are not part of agent access, and an exercise of an exam is confidential
     * until the exam starts.
     *
     * @param isExamExercise whether the exercise belongs to an exam
     * @throws AccessForbiddenException if the request is from a token and the exercise belongs to an exam
     */
    public static void rejectExamExercise(boolean isExamExercise) {
        if (isTokenRequest() && isExamExercise) {
            throw new AccessForbiddenException("Exam exercises cannot be accessed with a personal access token.");
        }
    }

    /**
     * Requires a token request that creates an exercise or lecture unit to say when students may see it, and that moment to lie in
     * the future. Without it the new object would be visible to students at once.
     *
     * @param releaseDate the requested release date
     * @param startDate   the requested start date, which also makes an exercise available to students once passed
     * @throws BadRequestAlertException if the request is from a token and the dates would publish the object
     */
    public static void requireDraftDatesForCreate(@Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate) {
        if (isTokenRequest() && !isDraft(releaseDate, startDate)) {
            throw new BadRequestAlertException(
                    "Content created with a personal access token must be a draft: set releaseDate to a time in the future (and startDate, if you set one, after it). "
                            + "Publish it in the Artemis web interface when it is ready.",
                    ENTITY_NAME, "releaseDateMustBeInTheFuture");
        }
    }

    /**
     * Requires a token request that changes an exercise or lecture unit to leave it a draft: it must not be published yet, and the
     * change must not publish it.
     *
     * @param currentReleaseDate the release date of the stored object
     * @param currentStartDate   the start date of the stored object
     * @param newReleaseDate     the release date the request would set
     * @param newStartDate       the start date the request would set
     * @throws AccessForbiddenException if the request is from a token and the stored object is already published
     * @throws BadRequestAlertException if the request is from a token and the change would publish the object
     */
    public static void requireStillDraft(@Nullable ZonedDateTime currentReleaseDate, @Nullable ZonedDateTime currentStartDate, @Nullable ZonedDateTime newReleaseDate,
            @Nullable ZonedDateTime newStartDate) {
        if (!isTokenRequest()) {
            return;
        }
        if (!isDraft(currentReleaseDate, currentStartDate)) {
            throw new AccessForbiddenException("This content is already visible to students. Content that is published can be changed only in the Artemis web interface.");
        }
        requireDraftDatesForCreate(newReleaseDate, newStartDate);
    }

    private static boolean isDraft(@Nullable ZonedDateTime releaseDate, @Nullable ZonedDateTime startDate) {
        ZonedDateTime now = ZonedDateTime.now();
        return releaseDate != null && releaseDate.isAfter(now) && (startDate == null || startDate.isAfter(now));
    }
}
