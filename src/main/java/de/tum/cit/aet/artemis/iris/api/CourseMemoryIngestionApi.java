package de.tum.cit.aet.artemis.iris.api;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.Post;
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
     * Trigger B: a thread's resolution state changed — an answer was marked resolving, un-marked, or
     * deleted. Ingests the thread while it still holds an answer someone stands behind, and deletes
     * its entry once none remains.
     *
     * @param post             the thread's root post
     * @param triggeringAnswer the answer whose flag changed, or {@code null} when it was deleted
     * @param marker           the user who changed the resolution state, if known
     * @param course           the course the thread belongs to
     */
    public void onThreadResolutionChanged(Post post, @Nullable AnswerPost triggeringAnswer, @Nullable User marker, Course course) {
        courseMemoryIngestionService.handleResolutionChange(post, triggeringAnswer, marker, course);
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
}
