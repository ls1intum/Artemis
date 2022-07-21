package de.tum.in.www1.artemis.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.domain.ProgrammingSubmission;
import de.tum.in.www1.artemis.domain.enumeration.ProgrammingLanguage;
import de.tum.in.www1.artemis.repository.BuildLogEntryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;

@Service
public class BuildLogEntryService {

    private final BuildLogEntryRepository buildLogEntryRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    public BuildLogEntryService(BuildLogEntryRepository buildLogEntryRepository, ProgrammingSubmissionRepository programmingSubmissionRepository) {
        this.buildLogEntryRepository = buildLogEntryRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
    }

    /**
     * Saves the build log entries in the database. The association to the programming submission is first removed and
     * after the saving restored as the relation submission->result uses an order column.
     *
     * @param buildLogs build logs to save
     * @param programmingSubmission submission of the build logs
     * @return the saved build logs
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
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Retrieves the latest build logs for a given programming submission.
     *
     * @param programmingSubmission submission for which to retrieve the build logs
     * @return the build log entries
     */
    public List<BuildLogEntry> getLatestBuildLogs(ProgrammingSubmission programmingSubmission) {
        Optional<ProgrammingSubmission> optionalProgrammingSubmission = programmingSubmissionRepository.findWithEagerBuildLogEntriesById(programmingSubmission.getId());
        if (optionalProgrammingSubmission.isPresent()) {
            return optionalProgrammingSubmission.get().getBuildLogEntries();
        }
        return List.of();
    }

    /**
     * Determines if a given build log string can be categorized as an illegal reflection
     *
     * @param logString current build log string
     * @return boolean indicating an illegal reflection log or not
     */
    public boolean isIllegalReflectionLog(String logString) {
        return logString.startsWith("WARNING") && (logString.contains("An illegal reflective access operation has occurred")
                || logString.contains("WARNING: Illegal reflective access by") || logString.contains("WARNING: Please consider reporting this to the maintainers of")
                || logString.contains("to enable warnings of further illegal reflective access operations")
                || logString.contains("All illegal access operations will be denied in a future release"));
    }

    /**
     * Determines if a given build log string can be categorized as an unnecessary log which does not need to be shown to the student
     *
     * @param logString           current build log string
     * @param programmingLanguage programming language of exercise
     * @return boolean indicating an unnecessary build log or not
     */
    public boolean isUnnecessaryBuildLogForProgrammingLanguage(String logString, ProgrammingLanguage programmingLanguage) {
        boolean isInfoWarningOrErrorLog = isInfoLog(logString) || isWarningLog(logString) || isErrorLog(logString) || isDockerImageLog(logString);
        if (ProgrammingLanguage.JAVA.equals(programmingLanguage)) {
            return isInfoWarningOrErrorLog || logString.startsWith("Unable to publish artifact") || logString.startsWith("NOTE: Picked up JDK_JAVA_OPTIONS")
                    || logString.startsWith("Picked up JAVA_TOOL_OPTIONS") || logString.startsWith("[withMaven]") || logString.startsWith("$ docker");
        }
        else if (ProgrammingLanguage.SWIFT.equals(programmingLanguage) || ProgrammingLanguage.C.equals(programmingLanguage)) {
            return isInfoWarningOrErrorLog || logString.contains("Unable to find image") || logString.contains(": Already exists") || logString.contains(": Pull")
                    || logString.contains(": Waiting") || logString.contains(": Verifying") || logString.contains(": Download") || logString.startsWith("Digest:")
                    || logString.startsWith("Status:") || logString.contains("github.com");
        }
        return isInfoWarningOrErrorLog;
    }

    private boolean isInfoLog(String log) {
        return (log.startsWith("[INFO]") && !log.contains("error")) || log.startsWith("[INFO] Downloading") || log.startsWith("[INFO] Downloaded");
    }

    private boolean isWarningLog(String log) {
        return log.startsWith("[WARNING]");
    }

    private boolean isErrorLog(String log) {
        return log.startsWith("[ERROR] [Help 1]") || log.startsWith("[ERROR] For more information about the errors and possible solutions")
                || log.startsWith("[ERROR] Re-run Maven using") || log.startsWith("[ERROR] To see the full stack trace of the errors") || log.startsWith("[ERROR] -> [Help 1]")
                || log.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins") || "[ERROR] ".equals(log);
    }

    private boolean isDockerImageLog(String log) {
        return (log.startsWith("Unable to find image '") && log.endsWith("' locally")) || (log.startsWith("Digest: sha256:") && log.length() == 79)
                || log.startsWith("Status: Downloaded newer image for ") || "Jenkins does not seem to be running inside a container".equals(log)
                || log.startsWith("Jenkins seems to be running inside container ") || ".".equals(log)
                // Each of the following is prefixed by a 12 character hash, so the length is the length of the suffix + 12
                || (log.endsWith(": Pulling fs layer") && log.length() == 30) || (log.endsWith(": Waiting") && log.length() == 21)
                || (log.endsWith(": Verifying Checksum") && log.length() == 32) || (log.endsWith(": Download complete") && log.length() == 31)
                || (log.endsWith(": Pull complete") && log.length() == 27);
    }

    /**
     * Determines if a given build log string shall be added to the build logs which are shown to the user.
     * It avoids duplicate entries and only allows not more than one empty log.
     *
     * @param programmingLanguage programming language of build log
     * @param buildLogs           accumulated build logs
     * @param shortenedLogString  current build log string
     * @return boolean indicating a build log should be added to the overall build logs
     */
    public boolean checkIfBuildLogIsNotADuplicate(ProgrammingLanguage programmingLanguage, List<BuildLogEntry> buildLogs, String shortenedLogString) {
        // C outputs duplicate but necessary output, so we need to skip it
        boolean skipLanguage = ProgrammingLanguage.C.equals(programmingLanguage);
        if (!skipLanguage && !buildLogs.isEmpty()) {
            // E.g. Swift produces a lot of duplicate build logs when a build fails
            var existingLog = buildLogs.stream().filter(log -> log.getLog().equals(shortenedLogString)).findFirst();
            String lastLog = buildLogs.get(buildLogs.size() - 1).getLog();
            // If the log does not exist already or if the log is a single blank log add it to the build logs (avoid more than one empty log in a row)
            boolean isSingleBlankLog = shortenedLogString.isBlank() && !lastLog.isBlank();
            return existingLog.isEmpty() || isSingleBlankLog;
        }
        return true;
    }

    /**
     * Filters out unnecessary build logs that a student should not see.
     *
     * @param buildLogEntries the build logs
     * @param programmingLanguage the programming language
     * @return filtered build logs
     */
    private List<BuildLogEntry> removeUnnecessaryLogs(List<BuildLogEntry> buildLogEntries, ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> filteredLogs = new ArrayList<>();
        for (BuildLogEntry buildLog : buildLogEntries) {

            String logString = buildLog.getLog();
            if (isCompilationError(logString) && isBuildFailure(logString)) {
                // hide duplicated information that is displayed in the section COMPILATION ERROR and in the section BUILD FAILURE and stop here
                break;
            }

            // filter unnecessary logs and illegal reflection logs
            if (isUnnecessaryBuildLogForProgrammingLanguage(logString, programmingLanguage) || isIllegalReflectionLog(logString)) {
                continue;
            }

            // filter blank entries
            if (logString.isBlank()) {
                continue;
            }

            // Avoid duplicate log entries
            if (checkIfBuildLogIsNotADuplicate(programmingLanguage, filteredLogs, logString)) {
                filteredLogs.add(new BuildLogEntry(buildLog.getTime(), logString, buildLog.getProgrammingSubmission()));
            }
        }

        return filteredLogs;
    }

    private boolean isCompilationError(String log) {
        return log.contains("COMPILATION ERROR");
    }

    private boolean isBuildFailure(String log) {
        return log.contains("BUILD FAILURE");
    }

    /**
     * Filter the given list of unfiltered build log entries and return A NEW list only including the filtered build logs.
     *
     * @param buildLogEntries     the original, unfiltered list
     * @param programmingLanguage the programming language for filtering out language-specific logs
     * @return the filtered list
     */
    public List<BuildLogEntry> removeUnnecessaryLogsForProgrammingLanguage(List<BuildLogEntry> buildLogEntries, ProgrammingLanguage programmingLanguage) {
        List<BuildLogEntry> buildLogs = removeUnnecessaryLogs(buildLogEntries, programmingLanguage);
        // Replace some unnecessary information and hide complex details to make it easier to read the important information
        return buildLogs.stream().peek(buildLog -> buildLog.setLog(ContinuousIntegrationService.ASSIGNMENT_PATH.matcher(buildLog.getLog()).replaceAll(""))).toList();
    }
}
