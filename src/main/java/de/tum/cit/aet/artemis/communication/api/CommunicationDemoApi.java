package de.tum.cit.aet.artemis.communication.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.communication.service.CommunicationDemoService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;

/**
 * Lets the demo seeding create the demo content of the communication module. Only delegates to {@link CommunicationDemoService}, which holds the seeding logic.
 */
@Controller
@Lazy
@Profile(PROFILE_CORE)
public class CommunicationDemoApi implements AbstractApi {

    private final CommunicationDemoService communicationDemoService;

    public CommunicationDemoApi(CommunicationDemoService communicationDemoService) {
        this.communicationDemoService = communicationDemoService;
    }

    /**
     * Creates the communication and the FAQs of the demo course that do not exist yet.
     *
     * @param course            the demo course.
     * @param students          the demo student followed by their ten classmates, who start and answer the threads.
     * @param tutor             the demo tutor, who answers the questions.
     * @param instructor        the demo instructor, who posts the announcement and the information about the tutorial groups.
     * @param discussedExercise the exercise whose channel discusses its word limit, or {@code null} to leave that thread out.
     */
    public void createDemo(Course course, List<User> students, User tutor, User instructor, @Nullable Exercise discussedExercise) {
        communicationDemoService.createDemo(course, students, tutor, instructor, discussedExercise);
    }
}
