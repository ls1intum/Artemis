package de.tum.cit.aet.artemis.iris.service.pyris.job;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Flat one-shot distributed job for a proactive struggle-intervention run. The session is not stored here; the
 * callback resolves it from {@code exerciseId} and {@code userId}, creating it if the student has none yet, because
 * what ambient defers to the reveal is the message rather than the session. The stamped {@code intent},
 * {@code episodeId}, {@code confirmReason} and {@code requestToken} let the async callback correlate and route
 * without the websocket event echoing them.
 *
 * @param jobId           the job id (== authentication token == Bearer run_id)
 * @param courseId        the course the run belongs to; authorizes {@link #canAccess(Course)}
 * @param exerciseId      the exercise the student is struggling on
 * @param userId          the struggling student
 * @param intent          the slot intent ({@code decide} | {@code confirm_close} | {@code help_request}); null on legacy paths
 * @param episodeId       the client-allocated episode UUID for correlation; null when no episode was sent
 * @param confirmReason   the close-mode discriminator ({@code progress} | {@code parked_progress}); null unless intent is {@code confirm_close}
 * @param requestToken    the client-minted scoped-cancel UUID; null on legacy paths
 * @param proactivityMode the presence level ({@code pull} | {@code push}); stamped so the async callback can deterministically cap an
 *                            {@code active} decision to {@code ambient} in {@code pull}; null on legacy paths
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionJob(String jobId, long courseId, long exerciseId, long userId, @Nullable String intent, @Nullable String episodeId,
        @Nullable String confirmReason, @Nullable String requestToken, @Nullable String proactivityMode) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
