package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.lecture.config.LectureEnabled;

@Conditional(LectureEnabled.class)
@Profile(PROFILE_CORE_AND_SCHEDULING)
@Lazy(false) // Scheduled methods require this lightweight publisher bean to be instantiated.
@Service
public class StudentVersionRegenerationScheduleService {

    private static final long RETRY_INTERVAL_MILLISECONDS = 300_000;

    private final ApplicationEventPublisher eventPublisher;

    public StudentVersionRegenerationScheduleService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(fixedRate = RETRY_INTERVAL_MILLISECONDS, initialDelay = RETRY_INTERVAL_MILLISECONDS)
    public void retryPendingStudentVersions() {
        eventPublisher.publishEvent(new RetryPendingStudentVersionsEvent());
    }

    public record RetryPendingStudentVersionsEvent() {
    }
}
