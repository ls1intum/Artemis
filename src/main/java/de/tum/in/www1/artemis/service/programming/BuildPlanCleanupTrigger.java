package de.tum.in.www1.artemis.service.programming;

import javax.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.repository.BuildPlanRepository;

@Component
public class BuildPlanCleanupTrigger {

    private static final Logger log = LoggerFactory.getLogger(BuildPlanCleanupTrigger.class);

    private final BuildPlanRepository buildPlanRepository;

    BuildPlanCleanupTrigger(BuildPlanRepository buildPlanRepository) {
        this.buildPlanRepository = buildPlanRepository;
    }

    @PostConstruct
    public void init() {
        log.debug("Cleaning up unused build plans...");
        buildPlanRepository.deleteAllWithoutProgrammingExercise();
    }
}
