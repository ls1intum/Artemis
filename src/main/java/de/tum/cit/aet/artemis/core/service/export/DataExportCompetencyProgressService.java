package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.core.dto.export.UserCompetencyProgressExportDTO;

/**
 * Service for creating competency progress data exports for GDPR compliance.
 * <p>
 * This service exports all competency progress records for a user, including
 * their progress and confidence values for each competency across all courses.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportCompetencyProgressService {

    private static final Logger log = LoggerFactory.getLogger(DataExportCompetencyProgressService.class);

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    public DataExportCompetencyProgressService(Optional<CompetencyProgressApi> competencyProgressApi) {
        this.competencyProgressApi = competencyProgressApi;
    }

    /**
     * Creates the competency progress data export containing all competency progress for the user.
     * If the Atlas module is not enabled, this method does nothing.
     *
     * @param userId           the ID of the user for which the data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createCompetencyProgressExport(long userId, Path workingDirectory) throws IOException {
        if (competencyProgressApi.isEmpty()) {
            log.debug("Atlas module is not enabled, skipping competency progress data export for user {}", userId);
            return;
        }

        var progressRecords = competencyProgressApi.get().findAllForExportByUserId(userId);
        createCompetencyProgressExportFile(workingDirectory, progressRecords);
    }

    /**
     * Creates a CSV file containing all competency progress records for the user.
     *
     * @param workingDirectory the directory where the export file should be created
     * @param progressRecords  the list of progress records to be exported
     * @throws IOException if the file cannot be created
     */
    private void createCompetencyProgressExportFile(Path workingDirectory, List<UserCompetencyProgressExportDTO> progressRecords) throws IOException {
        if (progressRecords == null || progressRecords.isEmpty()) {
            return;
        }

        String[] header = { "course_id", "course_title", "competency_id", "competency_title", "progress", "confidence", "last_modified" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("competency_progress" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var progress : progressRecords) {
                String lastModified = progress.lastModifiedDate() != null ? DATE_FORMATTER.format(progress.lastModifiedDate().atZone(ZoneId.systemDefault())) : "";
                printer.printRecord(progress.courseId(), progress.courseTitle(), progress.competencyId(), progress.competencyTitle(), progress.progress(), progress.confidence(),
                        lastModified);
            }
        }
    }
}
