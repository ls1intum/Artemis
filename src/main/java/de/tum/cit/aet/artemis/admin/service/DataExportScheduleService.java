package de.tum.cit.aet.artemis.admin.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.service.user.UserService;
import de.tum.cit.aet.artemis.admin.domain.DataExport;
import de.tum.cit.aet.artemis.admin.repository.DataExportRepository;
import de.tum.cit.aet.artemis.admin.service.export.DataExportCreationService;
import de.tum.cit.aet.artemis.admin.service.export.DataExportService;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.notification.dto.DataExportEmailDTO;
import de.tum.cit.aet.artemis.notification.dto.MailRecipientDTO;
import de.tum.cit.aet.artemis.notification.service.notifications.MailService;

/**
 * Service responsible for scheduling data exports.
 */
@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class DataExportScheduleService {

    private final DataExportRepository dataExportRepository;

    private final DataExportCreationService dataExportCreationService;

    private final DataExportService dataExportService;

    private final ProfileService profileService;

    private final MailService mailService;

    private final UserService userService;

    private static final Logger log = LoggerFactory.getLogger(DataExportScheduleService.class);

    /**
     * How long the nightly run keeps starting exports.
     * <p>
     * The job runs at 4 am by default and the next one at 5 am, so it stops in time for that. A request it did not
     * reach stays pending and is picked up by the following run.
     */
    private static final Duration CREATION_BUDGET = Duration.ofMinutes(60);

    public DataExportScheduleService(DataExportRepository dataExportRepository, DataExportCreationService dataExportCreationService, DataExportService dataExportService,
            ProfileService profileService, MailService mailService, UserService userService) {
        this.dataExportRepository = dataExportRepository;
        this.dataExportCreationService = dataExportCreationService;
        this.dataExportService = dataExportService;
        this.profileService = profileService;
        this.mailService = mailService;
        this.userService = userService;
    }

    /**
     * Schedule data export creation and deletion.
     * Created will be all data exports that are in the state REQUESTED OR IN_CREATION
     * Deleted will be all data exports that have a creation date older than seven days
     */
    @Scheduled(cron = "${artemis.scheduling.data-export-creation-time: 0 0 4 * * *}")
    public void createDataExportsAndDeleteOldOnes() {
        if (profileService.isDevActive()) {
            // do not execute this in a development environment
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        SecurityUtils.setSystemAuthorizationObject();
        log.info("Creating data exports and deleting old ones");
        Set<DataExport> successfulDataExports = new HashSet<>();
        var dataExportsToBeCreated = dataExportRepository.findAllToBeCreated();
        // One export at a time. What an export spends its time on is git operations on one student's repositories, so
        // ten at once multiplied the load on the version control server without making any single one of them finish
        // sooner. The place for that concurrency is inside an export, across the exercises it has to read.
        Instant startedAt = Instant.now();
        Instant budgetExhaustedAt = startedAt.plus(CREATION_BUDGET);
        int attempted = 0;
        for (var dataExport : dataExportsToBeCreated) {
            if (Instant.now().isAfter(budgetExhaustedAt)) {
                // The elapsed time, not the budget: the check runs after an export has finished, so the run is past
                // the budget by however long that last export took.
                log.info("Stopping after {} minutes, past the {} minute budget, having attempted {} of {} pending data exports. The rest are picked up by the next run.",
                        Duration.between(startedAt, Instant.now()).toMinutes(), CREATION_BUDGET.toMinutes(), attempted, dataExportsToBeCreated.size());
                break;
            }
            createDataExport(dataExport, successfulDataExports);
            attempted++;
        }
        ZonedDateTime thresholdDate = ZonedDateTime.now().minusDays(7);
        var dataExportsToBeDeleted = dataExportRepository.findAllToBeDeleted(thresholdDate);
        dataExportsToBeDeleted.forEach(this::deleteDataExport);
        Optional<User> admin = userService.findInternalAdminUser();
        if (admin.isEmpty()) {
            log.warn("No internal admin user found. Cannot send email to admin about successful creation of data exports.");
            return;
        }
        if (!successfulDataExports.isEmpty()) {
            Set<DataExportEmailDTO> dataExportDtos = successfulDataExports.stream().map(DataExportEmailDTO::from).collect(Collectors.toSet());
            mailService.sendSuccessfulDataExportsEmailToAdmin(MailRecipientDTO.from(admin.get()), dataExportDtos);
        }
    }

    /**
     * Create a single data export
     *
     * @param dataExport the data export to be created
     */
    private void createDataExport(DataExport dataExport, Set<DataExport> successfulDataExports) {
        SecurityUtils.setSystemAuthorizationObject();
        log.info("Creating data export for {}", dataExport.getUser().getLogin());
        var successful = dataExportCreationService.createDataExport(dataExport);
        if (successful) {
            successfulDataExports.add(dataExport);
        }
    }

    /**
     * Delete a single data export
     *
     * @param dataExport the data export to be deleted
     */
    private void deleteDataExport(DataExport dataExport) {
        log.info("Deleting data export for {}", dataExport.getUser().getLogin());
        dataExportService.deleteDataExportAndSetDataExportState(dataExport);
    }

}
