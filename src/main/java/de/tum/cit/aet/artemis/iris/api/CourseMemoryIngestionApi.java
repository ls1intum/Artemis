package de.tum.cit.aet.artemis.iris.api;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
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
     * Trigger B: a thread was marked resolved by a tutor/student answer.
     *
     * @param resolvingAnswer the answer post that resolves the thread
     * @param course          the course the thread belongs to
     */
    public void onThreadResolved(AnswerPost resolvingAnswer, Course course) {
        courseMemoryIngestionService.ingestResolvedThread(resolvingAnswer, course);
    }
}
