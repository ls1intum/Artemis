package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupRegistrationApi;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorialGroupRegistration;

/**
 * Service for creating tutorial group registration data exports for GDPR compliance.
 * <p>
 * This service exports all tutorial group registrations for a user, showing
 * which tutorial groups the user is enrolled in across all courses.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportTutorialGroupService {

    private static final Logger log = LoggerFactory.getLogger(DataExportTutorialGroupService.class);

    private final Optional<TutorialGroupRegistrationApi> tutorialGroupRegistrationApi;

    public DataExportTutorialGroupService(Optional<TutorialGroupRegistrationApi> tutorialGroupRegistrationApi) {
        this.tutorialGroupRegistrationApi = tutorialGroupRegistrationApi;
    }

    /**
     * Creates the tutorial group registration export containing all tutorial group enrollments.
     * If the tutorial group module is not enabled, this method does nothing.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createTutorialGroupExport(long userId, Path workingDirectory) throws IOException {
        if (tutorialGroupRegistrationApi.isEmpty()) {
            log.debug("Tutorial group module is not enabled, skipping tutorial group data export for user {}", userId);
            return;
        }

        var registrations = tutorialGroupRegistrationApi.get().findAllByStudentIdForExport(userId);
        createTutorialGroupExportFile(workingDirectory, registrations);
    }

    /**
     * Creates a CSV file containing all tutorial group registrations for the user.
     *
     * @param workingDirectory the directory where the export file should be created
     * @param registrations    the list of tutorial group registrations to be exported
     * @throws IOException if the file cannot be created
     */
    private void createTutorialGroupExportFile(Path workingDirectory, List<TutorialGroupRegistration> registrations) throws IOException {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }

        String[] header = { "course_title", "tutorial_group_title", "registration_type" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("tutorial_group_registrations" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var registration : registrations) {
                var tutorialGroup = registration.getTutorialGroup();
                String courseTitle = tutorialGroup != null && tutorialGroup.getCourse() != null ? tutorialGroup.getCourse().getTitle() : "";
                String tutorialGroupTitle = tutorialGroup != null ? tutorialGroup.getTitle() : "";
                printer.printRecord(courseTitle, tutorialGroupTitle, registration.getType());
            }
        }
    }
}
