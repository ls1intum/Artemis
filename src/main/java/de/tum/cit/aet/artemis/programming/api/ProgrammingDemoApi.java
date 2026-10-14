package de.tum.cit.aet.artemis.programming.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingDemoService;

/**
 * Lets the demo seeding create the demo content of the programming module. Only delegates to {@link ProgrammingDemoService}, which holds the seeding logic.
 */
@Profile(PROFILE_CORE)
@Controller
@Lazy
public class ProgrammingDemoApi implements AbstractApi {

    private final ProgrammingDemoService programmingDemoService;

    public ProgrammingDemoApi(ProgrammingDemoService programmingDemoService) {
        this.programmingDemoService = programmingDemoService;
    }

    /**
     * Creates the demo programming exercise in the given course if it does not exist yet, see {@link ProgrammingDemoService#createDemo(Course)}.
     *
     * @param course the demo course the exercise belongs to.
     * @return the demo exercise, whether it already existed or was created by this call, or empty if seeding it is skipped.
     */
    public Optional<ProgrammingExercise> createDemo(Course course) {
        return programmingDemoService.createDemo(course);
    }
}
