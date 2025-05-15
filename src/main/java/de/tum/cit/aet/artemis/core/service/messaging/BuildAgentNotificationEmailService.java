package de.tum.cit.aet.artemis.core.service.messaging;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.core.EntryEvent;
import com.hazelcast.map.listener.EntryUpdatedListener;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.communication.service.notifications.MailService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.UserService;
import de.tum.cit.aet.artemis.programming.service.localci.DistributedDataAccessService;

/**
 * Service for handling build agent notifications on core nodes.
 * This service listens for build agent status changes and sends email notifications for relevant events
 */
@Profile(PROFILE_CORE)
@Service
public class BuildAgentNotificationEmailService {

    private final UserService userService;

    private final MailService mailService;

    private final DistributedDataAccessService distributedDataAccessService;

    private static final Logger log = LoggerFactory.getLogger(BuildAgentNotificationEmailService.class);

    public BuildAgentNotificationEmailService(UserService userService, MailService mailService, DistributedDataAccessService distributedDataAccessService) {
        this.userService = userService;
        this.mailService = mailService;
        this.distributedDataAccessService = distributedDataAccessService;

    }

    @PostConstruct
    public void init() {
        distributedDataAccessService.getDistributedBuildAgentInformation().addEntryListener(new BuildAgentStatusListener(), true);
    }

    private class BuildAgentStatusListener implements EntryUpdatedListener<String, BuildAgentInformation> {

        @Override
        public void entryUpdated(EntryEvent<String, BuildAgentInformation> event) {
            BuildAgentInformation oldValue = event.getOldValue();
            BuildAgentInformation newValue = event.getValue();

            if (oldValue != null && newValue != null && oldValue.status() != BuildAgentInformation.BuildAgentStatus.SELF_PAUSED
                    && newValue.status() == BuildAgentInformation.BuildAgentStatus.SELF_PAUSED) {

                Optional<User> admin = userService.findInternalAdminUser();
                if (admin.isEmpty()) {
                    log.warn("No internal admin user found. Cannot send email to admin about successful creation of data exports.");
                    return;
                }
                mailService.sendBuildAgentSelfPausedEmailToAdmin(admin.get(), newValue.buildAgent().name());
            }
        }
    }
}
