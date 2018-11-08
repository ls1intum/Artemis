package de.tum.in.www1.artemis.service.scheduled;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class AutomaticBuildPlanCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticBuildPlanCleanupService.class);


    @Scheduled(cron="30 2 * * *")
    private void cleanupBuildPlans() {

    }
}
