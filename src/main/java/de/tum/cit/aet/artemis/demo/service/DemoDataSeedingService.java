package de.tum.cit.aet.artemis.demo.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.api.UserApi;
import de.tum.cit.aet.artemis.account.api.dtos.DemoUsers;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.api.CourseApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.demo.service.DemoExerciseSeedingService.DemoExercises;

/**
 * Seeds the demo course when the {@code demo} profile is active.
 * <p>
 * The service only decides <b>what</b> is seeded and in which order. Building and persisting the data is owned by the modules themselves, which is why every step goes through the
 * respective module's API instead of touching repositories or internal services directly. The APIs only delegate to the demo service of their module, which holds the
 * seeding logic.
 * <p>
 * Seeding is idempotent: each {@code createDemo} implementation checks for its own data and creates only what is missing. Running it on every startup is therefore safe, and demo
 * content that is added to the seeding routine later appears on the next restart without wiping the existing course.
 * <p>
 * Seeding runs on the primary node only: several nodes are active with the {@code core} profile in multi node setups, so this service additionally requires
 * {@code scheduling} to make sure exactly one node seeds per database. Without that restriction, every core node would seed concurrently on startup.
 * <p>
 * Note that the {@code core} profile has to be active as well, because the event this service listens for is only published by {@code DeferredEagerBeanInitializer}, which itself
 * runs only on core nodes. Activating {@code demo} without {@code core} and {@code scheduling} silently seeds nothing.
 */
@Service
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class DemoDataSeedingService {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeedingService.class);

    private final UserApi userApi;

    private final CourseApi courseApi;

    private final DemoExerciseSeedingService demoExerciseSeedingService;

    private final DemoCourseContentSeedingService demoCourseContentSeedingService;

    public DemoDataSeedingService(UserApi userApi, CourseApi courseApi, DemoExerciseSeedingService demoExerciseSeedingService,
            DemoCourseContentSeedingService demoCourseContentSeedingService) {
        this.userApi = userApi;
        this.courseApi = courseApi;
        this.demoExerciseSeedingService = demoExerciseSeedingService;
        this.demoCourseContentSeedingService = demoCourseContentSeedingService;
    }

    /**
     * Seeds the demo course once the application is fully initialized.
     * <p>
     * This listens for {@link DeferredEagerBeanInitializationCompletedEvent} rather than {@code ApplicationReadyEvent}, because Artemis beans are lazy and are force-instantiated
     * after the ready event has already been published, so an {@code ApplicationReadyEvent} listener on a lazy bean would never fire.
     * <p>
     * Every area of the demo course is seeded on its own, see {@link DemoAreas}. Only the users and the course itself are a prerequisite for everything else.
     *
     * @param event the event signalling that all lazy singletons have been initialized.
     */
    @EventListener(DeferredEagerBeanInitializationCompletedEvent.class)
    public void seedDemoData(DeferredEagerBeanInitializationCompletedEvent event) {
        log.info("Demo profile is active, seeding demo data");

        DemoUsers createdUsers = DemoAreas.seed("users", userApi::createDemoUsers, null);
        Course course = createdUsers == null ? null
                : DemoAreas.seed("course", () -> courseApi.createDemo(createdUsers.students(), createdUsers.tutor(), createdUsers.editor(), createdUsers.instructor()), null);
        // Loaded again, because enrolling the users into the course gave the staff the authorities of their course roles.
        DemoUsers users = course == null ? null : DemoAreas.seed("users", userApi::createDemoUsers, null);
        if (users == null) {
            log.error("Skipping the remaining demo data, because it belongs to the demo course and its users");
            return;
        }

        // The production creation paths resolve the acting user from the security context (the lecture channel takes its creator from there, for example), but seeding runs at
        // startup outside of any request. Act as the demo instructor, which the steps above guarantee to exist, so that the demo content is owned by a plausible user.
        SecurityUtils.runAs(users.instructor(), () -> {
            DemoExercises exercises = demoExerciseSeedingService.seed(course, users);
            demoCourseContentSeedingService.seed(course, users, exercises);
        });

        log.info("Finished seeding demo data for course '{}' with id {}", course.getShortName(), course.getId());
    }
}
