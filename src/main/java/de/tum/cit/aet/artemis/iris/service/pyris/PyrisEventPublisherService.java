package de.tum.cit.aet.artemis.iris.service.pyris;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.iris.domain.settings.event.IrisEventType;
import de.tum.cit.aet.artemis.iris.service.pyris.event.CompetencyJolSetEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.NewResultEvent;
import de.tum.cit.aet.artemis.iris.service.pyris.event.PyrisEvent;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;

/**
 * Service for publishing Pyris events.
 */
@Service
@Profile(PROFILE_IRIS)
public class PyrisEventPublisherService {

    private static final Logger log = LoggerFactory.getLogger(PyrisEventPublisherService.class);

    private final ApplicationEventPublisher eventPublisher;

    private final IrisSettingsService irisSettingsService;

    public PyrisEventPublisherService(ApplicationEventPublisher eventPublisher, IrisSettingsService irisSettingsService) {
        this.eventPublisher = eventPublisher;
        this.irisSettingsService = irisSettingsService;
    }

    /**
     * Publishes the given event.
     *
     * @param event the event to publish
     */
    public void publishEvent(PyrisEvent event) {
        if (!isEventEnabled(event)) {
            log.debug("Skipping event publication as conditions are not met: {}", event.getClass().getSimpleName());
            return;
        }
        try {
            eventPublisher.publishEvent(event);
        }
        catch (Exception e) {
            log.error("Failed to publish event: {}", event, e);
            throw e;
        }
    }

    /**
     * Checks if the given event is enabled.
     *
     * @param event the event to check
     * @return true if the event is enabled and conditions are met, false otherwise
     */
    private boolean isEventEnabled(PyrisEvent event) {
        return switch (event) {
            case NewResultEvent newResultEvent -> {
                var result = newResultEvent.getResult();
                if (result == null) {
                    yield false;
                }
                var submission = result.getSubmission();
                if (submission == null) {
                    yield false;
                }
                if (submission instanceof ProgrammingSubmission programmingSubmission) {
                    var participation = programmingSubmission.getParticipation();
                    if (participation == null) {
                        yield false;
                    }
                    var programmingExercise = participation.getExercise();
                    if (programmingExercise == null) {
                        yield false;
                    }
                    if (programmingSubmission.isBuildFailed()) {
                        yield irisSettingsService.isActivatedFor(IrisEventType.BUILD_FAILED, programmingExercise);
                    }
                    else {
                        yield irisSettingsService.isActivatedFor(IrisEventType.PROGRESS_STALLED, programmingExercise);
                    }
                }
                else {
                    yield false;
                }
            }
            case CompetencyJolSetEvent competencyJolSetEvent -> {
                var competencyJol = competencyJolSetEvent.getCompetencyJol();
                if (competencyJol == null) {
                    yield false;
                }
                var competency = competencyJol.getCompetency();
                if (competency == null) {
                    yield false;
                }
                var course = competency.getCourse();
                if (course == null) {
                    yield false;
                }
                yield irisSettingsService.isActivatedFor(IrisEventType.JOL, course);
            }
            default -> throw new UnsupportedPyrisEventException("Unsupported Pyris event: " + event.getClass().getSimpleName());
        };
    }
}
