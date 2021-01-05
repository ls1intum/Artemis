package de.tum.in.www1.artemis.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;

@Service
public class BuildLogEntryService {

    private BuildLogEntryRepository buildLogEntryRepository;

    public BuildLogEntryService(BuildLogEntryRepository buildLogEntryRepository) {
        this.buildLogEntryRepository = buildLogEntryRepository;
    }

    /**
     * Saves the build log entries in the database. The association to the programming submission is first removed and
     * after the saving restored as the relation submission->result uses an order column.
     *
     * @param buildLogs Build logs to save
     * @param programmingSubmission Submission of the build logs
     */
    public List<BuildLogEntry> saveBuildLogs(List<BuildLogEntry> buildLogs, ProgrammingSubmission programmingSubmission) {
        return buildLogs.stream().map(buildLogEntry -> {
            // Truncate the log so that it fits into the database
            buildLogEntry.truncateLogToMaxLength();
            // Cut association to parent object
            buildLogEntry.setProgrammingSubmission(null);
            // persist the BuildLogEntry object without an association to the parent object.
            var updatedBuildLogEntry = buildLogEntryRepository.save(buildLogEntry);
            // restore the association to the parent object
            updatedBuildLogEntry.setProgrammingSubmission(programmingSubmission);
            return updatedBuildLogEntry;
        }).collect(Collectors.toList());
    }
}
