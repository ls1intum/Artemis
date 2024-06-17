package de.tum.in.www1.artemis.service.export;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;
import static de.tum.in.www1.artemis.service.export.DataExportExerciseCreationService.CSV_FILE_EXTENSION;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.science.ScienceEvent;
import de.tum.in.www1.artemis.repository.science.ScienceEventRepository;

@Profile(PROFILE_CORE)
@Service
public class DataExportScienceEventService {

    private final ScienceEventRepository scienceEventRepository;

    public DataExportScienceEventService(ScienceEventRepository scienceEventRepository) {
        this.scienceEventRepository = scienceEventRepository;
    }

    public void createScienceEventExport(String login, Path workingDirectory) throws IOException {
        var scienceEvents = scienceEventRepository.findAllByIdentity(login);
        createScienceEventExportFile(workingDirectory, scienceEvents);
    }

    private void createScienceEventExportFile(Path workingDirectory, Set<ScienceEvent> scienceEvents) throws IOException {

        if (scienceEvents == null || scienceEvents.isEmpty()) {
            return;
        }

        String[] header = { "timestamp", "event_type", "resource_id" };
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder().setHeader(header).build();

        try (final CSVPrinter printer = new CSVPrinter(Files.newBufferedWriter(workingDirectory.resolve("science_events" + CSV_FILE_EXTENSION)), csvFormat)) {
            for (var scienceEvent : scienceEvents) {
                printer.printRecord(scienceEvent.getTimestamp(), scienceEvent.getType(), scienceEvent.getResourceId());
            }
        }
    }
}
