package de.tum.cit.aet.artemis.iris.api;

import java.util.Collection;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.dto.CourseMemoryThreadDTO;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;

/**
 * Public facade for Course Memory, consumed by the communication and account modules via an
 * {@code Optional<CourseMemoryIngestionApi>} so it stays a no-op when Iris is disabled.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class CourseMemoryIngestionApi extends AbstractIrisApi {

    private final CourseMemoryIngestionService courseMemoryIngestionService;

    public CourseMemoryIngestionApi(CourseMemoryIngestionService courseMemoryIngestionService) {
        this.courseMemoryIngestionService = courseMemoryIngestionService;
    }

    /**
     * A tutor approved (optionally edited) an Iris-generated answer in the verification dashboard.
     *
     * @param verifiedAnswer the now-verified Iris answer post
     * @param verifier       the tutor who verified the answer, notified about the run
     * @param course         the course the answer belongs to
     */
    public void onAnswerVerified(AnswerPost verifiedAnswer, User verifier, Course course) {
        courseMemoryIngestionService.refreshThread(verifiedAnswer.getPost().getId(), verifier, course);
    }

    /**
     * Something that can change a thread's entry happened: an answer was marked or un-marked as resolving, or a message of
     * the thread was edited or deleted. The entry is rebuilt from the current state of the thread, or retracted when
     * nothing memory-worthy remains.
     *
     * @param postId the thread's root post id
     * @param actor  the user whose action triggered the refresh, notified about the run
     * @param course the course the thread belongs to
     */
    public void onThreadChanged(long postId, @Nullable User actor, Course course) {
        courseMemoryIngestionService.refreshThread(postId, actor, course);
    }

    /**
     * The whole thread was deleted, so its entry is retracted for good.
     *
     * @param postId   the deleted thread's root post id
     * @param courseId the course it belonged to
     * @param actor    the user who deleted it, notified about the removal
     */
    public void onThreadDeleted(long postId, long courseId, @Nullable User actor) {
        courseMemoryIngestionService.retractDeletedThread(postId, courseId, actor);
    }

    /**
     * Outdates the entries of every thread with content by the user. Call it before and again after an opt-out from AI
     * or a deactivation is recorded, then pass both results to {@link #refreshThreadsInBackground}.
     *
     * @param userId the user
     * @return the affected threads' root post ids
     */
    public List<Long> invalidateThreadsWithContentBy(long userId) {
        return courseMemoryIngestionService.invalidateThreadsWithContentBy(userId);
    }

    /**
     * Rebuilds the given threads' entries in the background.
     *
     * @param postIds the threads' root post ids
     */
    public void refreshThreadsInBackground(Collection<Long> postIds) {
        if (!postIds.isEmpty()) {
            courseMemoryIngestionService.refreshThreadsAsync(postIds);
        }
    }

    /**
     * An account's messages were deleted: deleted threads are retracted, the others rebuilt without those messages.
     *
     * @param threads the threads that held content by the account, as captured before the deletion
     */
    public void onAccountContentDeleted(List<CourseMemoryThreadDTO> threads) {
        refreshThreadsInBackground(courseMemoryIngestionService.retractDeletedThreadsOf(threads));
    }
}
