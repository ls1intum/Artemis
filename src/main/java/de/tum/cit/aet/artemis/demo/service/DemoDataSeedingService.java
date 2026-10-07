package de.tum.cit.aet.artemis.demo.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.api.AccountDemoApi;
import de.tum.cit.aet.artemis.atlas.api.AtlasDemoApi;
import de.tum.cit.aet.artemis.core.DeferredEagerBeanInitializationCompletedEvent;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.course.api.CourseDemoApi;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.fileupload.api.FileUploadDemoApi;
import de.tum.cit.aet.artemis.lecture.api.LectureDemoApi;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.modeling.api.ModelingDemoApi;
import de.tum.cit.aet.artemis.programming.api.ProgrammingDemoApi;
import de.tum.cit.aet.artemis.quiz.api.QuizDemoApi;
import de.tum.cit.aet.artemis.text.api.TextDemoApi;

/**
 * Seeds the demo course when the {@code demo} profile is active.
 * <p>
 * The service only decides <b>what</b> is seeded and in which order. Building and persisting the data is owned by the modules themselves, which is why every step goes through the
 * respective module's demo API instead of touching repositories or internal services directly. Those demo APIs only exist on the seeding node, so a regular instance carries none
 * of the demo code.
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

    private final AccountDemoApi accountDemoApi;

    private final CourseDemoApi courseDemoApi;

    private final Optional<LectureDemoApi> lectureDemoApi;

    private final Optional<AtlasDemoApi> atlasDemoApi;

    private final Optional<TextDemoApi> textDemoApi;

    private final Optional<ModelingDemoApi> modelingDemoApi;

    private final Optional<FileUploadDemoApi> fileUploadDemoApi;

    private final QuizDemoApi quizDemoApi;

    private final ProgrammingDemoApi programmingDemoApi;

    public DemoDataSeedingService(AccountDemoApi accountDemoApi, CourseDemoApi courseDemoApi, Optional<LectureDemoApi> lectureDemoApi, Optional<AtlasDemoApi> atlasDemoApi,
            Optional<TextDemoApi> textDemoApi, Optional<ModelingDemoApi> modelingDemoApi, Optional<FileUploadDemoApi> fileUploadDemoApi, QuizDemoApi quizDemoApi,
            ProgrammingDemoApi programmingDemoApi) {
        this.accountDemoApi = accountDemoApi;
        this.courseDemoApi = courseDemoApi;
        this.lectureDemoApi = lectureDemoApi;
        this.atlasDemoApi = atlasDemoApi;
        this.textDemoApi = textDemoApi;
        this.modelingDemoApi = modelingDemoApi;
        this.fileUploadDemoApi = fileUploadDemoApi;
        this.quizDemoApi = quizDemoApi;
        this.programmingDemoApi = programmingDemoApi;
    }

    /**
     * Seeds the demo course once the application is fully initialized.
     * <p>
     * This listens for {@link DeferredEagerBeanInitializationCompletedEvent} rather than {@code ApplicationReadyEvent}, because Artemis beans are lazy and are force-instantiated
     * after the ready event has already been published, so an {@code ApplicationReadyEvent} listener on a lazy bean would never fire.
     * <p>
     * Every area of the demo course is seeded on its own: a failing area is logged and skipped, so that it neither keeps the remaining areas from being seeded nor escapes into
     * the startup of the instance.
     *
     * @param event the event signalling that all lazy singletons have been initialized.
     */
    @EventListener(DeferredEagerBeanInitializationCompletedEvent.class)
    public void seedDemoData(DeferredEagerBeanInitializationCompletedEvent event) {
        log.info("Demo profile is active, seeding demo data");

        Course course = seedArea("course and users", () -> courseDemoApi.createDemo(accountDemoApi.createDemoStudent(), accountDemoApi.createDemoInstructor()), null);
        if (course == null) {
            log.error("Skipping all further demo data, because it belongs to the demo course");
            return;
        }

        // The production creation paths resolve the acting user from the security context (the lecture channel takes its creator from there, for example), but seeding runs at
        // startup outside of any request. Act as the demo instructor, which the step above guarantees to exist, so that the demo content is owned by a plausible user.
        SecurityUtils.runAs(AccountDemoApi.DEMO_INSTRUCTOR_LOGIN, () -> {
            List<LectureUnit> lectureUnits = seedArea("lectures", () -> lectureDemoApi.map(api -> api.createDemo(course)).orElse(List.of()), List.of());
            seedArea("competencies", () -> atlasDemoApi.ifPresent(api -> api.createDemo(course, lectureUnits)));

            // One exercise of every type, each of them currently ongoing so that demo students can participate right away.
            seedArea("text exercises", () -> textDemoApi.ifPresent(api -> api.createDemo(course)));
            seedArea("modeling exercises", () -> modelingDemoApi.ifPresent(api -> api.createDemo(course)));
            seedArea("file upload exercises", () -> fileUploadDemoApi.ifPresent(api -> api.createDemo(course)));
            seedArea("quiz exercises", () -> quizDemoApi.createDemo(course));
            seedArea("programming exercises", () -> programmingDemoApi.createDemo(course));
        });

        log.info("Finished seeding demo data for course '{}' with id {}", course.getShortName(), course.getId());
    }

    private void seedArea(String area, Runnable step) {
        seedArea(area, () -> {
            step.run();
            return null;
        }, null);
    }

    private <T> T seedArea(String area, Supplier<T> step, T fallback) {
        try {
            return step.get();
        }
        catch (RuntimeException exception) {
            // Whatever the area created before it failed stays in place and counts as seeded on the next startup, like after any interrupted run.
            log.error("Could not seed the demo {}, continuing with the remaining demo data", area, exception);
            return fallback;
        }
    }
}
