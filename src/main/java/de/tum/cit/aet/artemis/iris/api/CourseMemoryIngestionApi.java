package de.tum.cit.aet.artemis.iris.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.service.CourseMemoryIngestionService;

/**
 * Public facade for Course Memory ingestion, consumed by the communication module via an
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
     * Trigger A: a tutor approved (optionally edited) an Iris-generated answer in the verification dashboard.
     *
     * @param verifiedAnswer the now-verified Iris answer post
     * @param edited         whether the tutor edited the draft before approving
     * @param verifier       the tutor who verified the answer
     * @param course         the course the answer belongs to
     */
    public void onAnswerVerified(AnswerPost verifiedAnswer, boolean edited, User verifier, Course course) {
        courseMemoryIngestionService.ingestVerifiedAnswer(verifiedAnswer, edited, verifier, course);
    }

    /**
     * Trigger B: a thread's resolution state changed — an answer was marked resolving, un-marked, deleted
     * or edited, or the question itself was edited. Ingests the thread while it still holds an answer
     * someone stands behind, and deletes its entry once none remains.
     * <p>
     * The trust tier of the resulting entry is derived from who <em>endorsed</em> the anchoring answer, as
     * recorded on the answer when it was marked resolving — not from {@code actor}, who may merely have
     * edited a typo or un-marked some other answer.
     *
     * @param post             the thread's root post
     * @param triggeringAnswer the answer whose flag or content changed, or {@code null} when it was deleted or
     *                             the change was to the question
     * @param actor            the user whose action triggered this refresh, if known; notified about the run
     * @param course           the course the thread belongs to
     */
    public void onThreadResolutionChanged(Post post, @Nullable AnswerPost triggeringAnswer, @Nullable User actor, Course course) {
        courseMemoryIngestionService.handleResolutionChange(post, triggeringAnswer, actor, course);
    }

    /**
     * The whole thread was deleted, so its Course Memory entry is removed with it.
     *
     * @param post   the thread's root post, before deletion
     * @param actor  the user who deleted the thread, notified about the removal
     * @param course the course the thread belongs to
     */
    public void onThreadDeleted(Post post, @Nullable User actor, Course course) {
        courseMemoryIngestionService.handleThreadDeleted(post, actor, course);
    }

    /**
     * A channel was deleted or stopped being public, so every Course Memory entry mined from it is
     * removed. Eligibility is only checked when an entry is written, so an entry would otherwise keep
     * being served after its source channel was restricted or removed.
     *
     * @param channel the channel whose entries should be removed
     * @param actor   the user who deleted or restricted the channel, notified about the removal
     * @param course  the course the channel belongs to
     */
    public void onChannelNoLongerEligible(Channel channel, @Nullable User actor, Course course) {
        courseMemoryIngestionService.handleChannelNoLongerEligible(channel, actor, course);
    }

    /**
     * The course itself was deleted, so every Course Memory entry of that course is removed. Its
     * conversations are dropped in one bulk statement, so no channel id survives to purge individually.
     *
     * @param course the course being deleted
     * @param actor  the user who deleted the course, notified about the removal
     */
    public void onCourseDeleted(Course course, @Nullable User actor) {
        courseMemoryIngestionService.handleCourseDeleted(course, actor);
    }
}
