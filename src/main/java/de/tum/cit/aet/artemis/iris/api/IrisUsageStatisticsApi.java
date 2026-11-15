package de.tum.cit.aet.artemis.iris.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.repository.IrisMessageRepository;

/**
 * Exposes aggregated Iris usage numbers to callers outside the Iris module, namely the public usage dashboard.
 * Only counts are exposed, never the content or the authors of messages.
 */
@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisUsageStatisticsApi extends AbstractIrisApi {

    private final IrisMessageRepository irisMessageRepository;

    public IrisUsageStatisticsApi(IrisMessageRepository irisMessageRepository) {
        this.irisMessageRepository = irisMessageRepository;
    }

    /**
     * @param courseIds the courses of the chat sessions
     * @return the number of user messages and final Iris answers in the chat sessions of these courses
     */
    public long countMessagesInCourses(Set<Long> courseIds) {
        return irisMessageRepository.countUserMessagesAndAnswersInCourses(courseIds);
    }
}
