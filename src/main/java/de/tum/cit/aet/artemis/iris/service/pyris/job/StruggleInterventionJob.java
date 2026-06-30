package de.tum.cit.aet.artemis.iris.service.pyris.job;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Flat one-shot Hazelcast job for a proactive struggle-intervention run. The session is NOT stored (it is
 * created only on an {@code active} outcome, §11); the callback resolves it from {@code exerciseId} +
 * {@code userId}. {@code jobId == settings.authenticationToken == Bearer run_id}.
 * <p>
 * {@code intent} and {@code episodeId} are stamped here so the async callback (A9/A11) can correlate the
 * Pyris response back to the client slot without the websocket event echoing them. {@code confirmReason}
 * allows A11 to route close-mode actions. {@code requestToken} is the scoped-cancel identity (A10).
 *
 * @param jobId         the job id (== authentication token == Bearer run_id)
 * @param courseId      the course the run belongs to; authorizes {@link #canAccess(Course)}
 * @param exerciseId    the exercise the student is struggling on
 * @param userId        the struggling student
 * @param intent        the slot intent ({@code decide} | {@code confirm_close} | {@code stale_check}); null on legacy paths
 * @param episodeId     the client-allocated episode UUID for correlation; null when no episode was sent
 * @param confirmReason the close-mode discriminator ({@code progress} | {@code stale_solved} | {@code parked_progress}); null unless intent is {@code confirm_close}
 * @param requestToken  the client-minted scoped-cancel UUID (A10); null on legacy paths
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record StruggleInterventionJob(String jobId, long courseId, long exerciseId, long userId, @Nullable String intent, @Nullable String episodeId,
        @Nullable String confirmReason, @Nullable String requestToken) implements PyrisJob {

    @Override
    public boolean canAccess(Course course) {
        return course.getId().equals(courseId);
    }
}
