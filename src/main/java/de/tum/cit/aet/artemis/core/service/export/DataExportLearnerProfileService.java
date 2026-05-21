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

import de.tum.cit.aet.artemis.atlas.api.LearnerProfileApi;
import de.tum.cit.aet.artemis.core.dto.export.UserLearnerProfileExportDTO;

/**
 * Service for creating learner profile data exports for GDPR compliance.
 * <p>
 * This service exports all course learner profiles for a user, including
 * their learning preferences such as goal orientation, time investment,
 * and repetition intensity settings.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportLearnerProfileService {

    private static final Logger log = LoggerFactory.getLogger(DataExportLearnerProfileService.class);

    private final Optional<LearnerProfileApi> learnerProfileApi;

    public DataExportLearnerProfileService(Optional<LearnerProfileApi> learnerProfileApi) {
        this.learnerProfileApi = learnerProfileApi;
    }

    /**
     * Creates the learner profile data export containing all course learner profiles for the user.
     * If the Atlas module is not enabled, this method does nothing.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createLearnerProfileExport(long userId, Path workingDirectory) throws IOException {
        if (learnerProfileApi.isEmpty()) {
            log.debug("Atlas module is not enabled, skipping learner profile data export for user {}", userId);
            return;
        }

        var learnerProfiles = learnerProfileApi.get().findAllForExportByUserId(userId);
        createLearnerProfileExportFile(workingDirectory, learnerProfiles);
    }

    /**
     * Creates a CSV file containing all learner profiles for the user.
     *
     * @param workingDirectory the directory where the export file should be created
     * @param learnerProfiles  the list of learner profiles to be exported
     * @throws IOException if the file cannot be created
     */
    private void createLearnerProfileExportFile(Path workingDirectory, List<UserLearnerProfileExportDTO> learnerProfiles) throws IOException {
        if (learnerProfiles == null || learnerProfiles.isEmpty()) {
            return;
        }

        String[] header = { "course_id", "course_title", "aim_for_grade_or_bonus", "time_investment", "repetition_intensity" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("learner_profiles" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var profile : learnerProfiles) {
                printer.printRecord(profile.courseId(), profile.courseTitle(), profile.aimForGradeOrBonus(), profile.timeInvestment(), profile.repetitionIntensity());
            }
        }
    }
}
