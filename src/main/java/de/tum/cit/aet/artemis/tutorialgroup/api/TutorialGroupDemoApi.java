package de.tum.cit.aet.artemis.tutorialgroup.api;

import java.util.List;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.tutorialgroup.config.TutorialGroupEnabled;
import de.tum.cit.aet.artemis.tutorialgroup.service.TutorialGroupDemoService;

/**
 * Lets the demo seeding create the demo content of the tutorial group module. Only delegates to {@link TutorialGroupDemoService}, which holds the seeding logic.
 */
@Conditional(TutorialGroupEnabled.class)
@Controller
@Lazy
public class TutorialGroupDemoApi extends AbstractTutorialGroupApi {

    private final TutorialGroupDemoService tutorialGroupDemoService;

    public TutorialGroupDemoApi(TutorialGroupDemoService tutorialGroupDemoService) {
        this.tutorialGroupDemoService = tutorialGroupDemoService;
    }

    /**
     * Creates the tutorial groups of the demo course that do not exist yet: the configuration of its tutorial groups, two weekly tutorial groups led by the demo tutor and a free
     * period.
     *
     * @param course   the demo course the tutorial groups belong to.
     * @param tutor    the demo tutor, who leads both tutorial groups.
     * @param students the demo student followed by their ten classmates, who are registered for the tutorial groups.
     */
    public void createDemo(Course course, User tutor, List<User> students) {
        tutorialGroupDemoService.createDemo(course, tutor, students);
    }
}
