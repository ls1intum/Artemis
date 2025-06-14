package de.tum.cit.aet.artemis.core.service.export;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.ScienceEventApi;
import de.tum.cit.aet.artemis.atlas.config.AtlasNotPresentException;
import de.tum.cit.aet.artemis.atlas.domain.science.ScienceEvent;

/**
 * A Service to create the science event export data for users.
 * This includes the timestamps for every lecture, lecture unit and exercise opened.
 * All science event data is exported into a single CSV file.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class DataExportScienceEventService {

    private final Optional<ScienceEventApi> scienceEventApi;

    public DataExportScienceEventService(Optional<ScienceEventApi> scienceEventApi) {
        this.scienceEventApi = scienceEventApi;
    }

    /**
     * Creates the science event data export containing timestamps for every lecture, lecture unit and exercise opened.
     *
     * @param login            the login of the user for which the science event data should be exported
     * @param workingDirectory the directory where the export file should be created
     * @throws IOException if the file cannot be created
     */
    public void createScienceEventExport(String login, Path workingDirectory) throws IOException {
        var api = scienceEventApi.orElseThrow(() -> new AtlasNotPresentException(ScienceEventApi.class));
        var scienceEvents = api.findAllByIdentity(login);
        createScienceEventExportFile(workingDirectory, scienceEvents);
    }

    /**
     * Creates a CSV file containing the science event data from a given set of science events.
     *
     * @param workingDirectory the directory where the export file should be created
     * @param scienceEvents    the set of science events to be exported
     * @throws IOException if the file cannot be created
     */
    private void createScienceEventExportFile(Path workingDirectory, Set<ScienceEvent> scienceEvents) throws IOException {

        if (scienceEvents == null || scienceEvents.isEmpty()) {
            return;
        }

        String[] header = { "timestamp", "event_type", "resource_id" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).get();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("science_events" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var scienceEvent : scienceEvents) {
                printer.printRecord(scienceEvent.getTimestamp(), scienceEvent.getType(), scienceEvent.getResourceId());
            }
        }
    }
}
