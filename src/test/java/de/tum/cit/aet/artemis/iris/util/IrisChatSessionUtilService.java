package de.tum.cit.aet.artemis.iris.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.session.IrisCourseChatSession;
import de.tum.cit.aet.artemis.iris.domain.session.IrisProgrammingExerciseChatSession;
import de.tum.cit.aet.artemis.iris.repository.IrisSessionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Utility service for creating Iris chat session test data.
 * Only available when Iris is enabled via the artemis.iris.enabled property.
 */
@Lazy
@Service
@Conditional(IrisEnabled.class)
public class IrisChatSessionUtilService {

    @Autowired
    private IrisSessionRepository irisSessionRepository;

    public IrisProgrammingExerciseChatSession createAndSaveProgrammingExerciseChatSessionForUser(ProgrammingExercise exercise, User user) {
        return irisSessionRepository.save(IrisChatSessionFactory.createProgrammingExerciseChatSessionForUser(exercise, user));
    }

    /**
     * Creates and persists an Iris course chat session for the given course and user.
     *
     * @param course the course for which to create the session
     * @param user   the user for whom to create the session
     * @return the persisted IrisCourseChatSession
     */
    public IrisCourseChatSession createAndSaveCourseChatSessionForUser(Course course, User user) {
        return irisSessionRepository.save(IrisChatSessionFactory.createCourseSessionForUserWithMessages(course, user));
    }
}
