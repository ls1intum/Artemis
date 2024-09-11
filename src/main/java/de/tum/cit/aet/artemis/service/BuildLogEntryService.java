package de.tum.cit.aet.artemis.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.programming.domain.BuildLogEntry;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.BuildLogEntryRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.service.connectors.ci.ContinuousIntegrationService;

@Profile(PROFILE_CORE)
@Service
public class BuildLogEntryService {

    private static final Logger log = LoggerFactory.getLogger(BuildLogEntryService.class);

    private final BuildLogEntryRepository buildLogEntryRepository;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final ProfileService profileService;

    @Value("${artemis.continuous-integration.build-log.file-expiry-days:30}")
    private int expiryDays;

    @Value("${artemis.build-logs-path:./build-logs}")
    private Path buildLogsPath;

    public BuildLogEntryService(BuildLogEntryRepository buildLogEntryRepository, ProgrammingSubmissionRepository programmingSubmissionRepository, ProfileService profileService) {
        this.buildLogEntryRepository = buildLogEntryRepository;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.profileService = profileService;
    }

    /**
     * Saves the build log entries in the database. The association to the programming submission is first removed and
     * after the saving restored as the relation submission->result uses an order column.
     *
     * @param buildLogs             build logs to save
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
        return programmingSubmissionRepository.findWithEagerBuildLogEntriesById(programmingSubmission.getId()).map(ProgrammingSubmission::getBuildLogEntries)
                .orElseGet(Collections::emptyList);
    }

    private static final Set<String> ILLEGAL_REFLECTION_LOGS = Set.of("An illegal reflective access operation has occurred", "Illegal reflective access by",
            "Please consider reporting this to the maintainers of", "to enable warnings of further illegal reflective access operations",
            "All illegal access operations will be denied in a future release");

    /**
     * Determines if a given build log string can be categorized as an illegal reflection
     *
     * @param logString current build log string
     * @return boolean indicating an illegal reflection log or not
     */
    public boolean isIllegalReflectionLog(String logString) {
        return logString.startsWith("WARNING") && ILLEGAL_REFLECTION_LOGS.stream().anyMatch(logString::contains);
    }

    /**
     * Determines if a given build log string can be categorized as an unnecessary log which does not need to be shown to the student
     *
     * @param logString           current build log string
     * @param programmingLanguage programming language of exercise
     * @return boolean indicating an unnecessary build log or not
     */
    public boolean isUnnecessaryBuildLogForProgrammingLanguage(String logString, ProgrammingLanguage programmingLanguage) {
        boolean isUnnecessaryLog = isInfoLog(logString) || isWarningLog(logString) || isDockerImageLog(logString) || isGitLog(logString) || isTaskLog(logString);
        if (isUnnecessaryLog) {
            return true;
        }
        if (programmingLanguage == ProgrammingLanguage.JAVA) {
            return isUnnecessaryJavaLog(logString);
        }
        else if (programmingLanguage == ProgrammingLanguage.SWIFT || programmingLanguage == ProgrammingLanguage.C) {
            return isUnnecessaryCOrSwiftLog(logString);
        }
        return false;
    }

    private static final Set<String> UNNECESSARY_JAVA_LOGS = Set.of("NOTE: Picked up JDK_JAVA_OPTIONS", "Picked up JAVA_TOOL_OPTIONS", "[withMaven]", "$ docker");

    private boolean isUnnecessaryJavaLog(String log) {
        return isMavenErrorLog(log) || isGradleErrorLog(log) || isGradleInfoLog(log) || UNNECESSARY_JAVA_LOGS.stream().anyMatch(log::startsWith);
    }

    private static final Set<String> UNNECESSARY_C_SWIFT_LOGS = Set.of("Unable to find image", ": Already exists", ": Pull", ": Waiting", ": Verifying", ": Download");

    private boolean isUnnecessaryCOrSwiftLog(String log) {
        return UNNECESSARY_C_SWIFT_LOGS.stream().anyMatch(log::contains) || log.startsWith("Digest:") || log.startsWith("Status:") || log.contains("github.com");
    }

    private boolean isInfoLog(String log) {
        return (log.startsWith("[INFO]") && !log.contains("error")) || log.contains("Downloading") || log.contains("Downloaded") || log.contains("Progress (");
    }

    private static final Set<String> GRADLE_LOGS = Set.of("Downloading https://services.gradle.org", "...........10%", "Here are the highlights of this release:", "- ",
            "For more details see", "Starting a Gradle Daemon", "Deprecated Gradle features were used", "You can use '--warning-mode all' to show");

    private boolean isGradleInfoLog(String log) {
        return log.contains("userguide/command_line_interface.html#sec:command_line_warnings") || GRADLE_LOGS.stream().anyMatch(log::startsWith);
    }

    private boolean isWarningLog(String log) {
        return log.startsWith("[WARNING]");
    }

    private static final Set<String> MAVEN_ERROR_LOGS = Set.of("[ERROR] [Help 1]", "[ERROR] For more information about the errors and possible solutions",
            "[ERROR] Re-run Maven using", "[ERROR] To see the full stack trace of the errors", "[ERROR] -> [Help 1]", "[ERROR] Failed to execute goal org.apache.maven.plugins");

    private boolean isMavenErrorLog(String log) {
        return "[ERROR]".equals(log.strip()) || MAVEN_ERROR_LOGS.stream().anyMatch(log::startsWith);
    }

    private static final Set<String> GRADLE_ERROR_LOGS = Set.of("> Run with", "FAILURE", "* What went wrong:", "Execution failed", "* Get more help", "* Try:");

    private boolean isGradleErrorLog(String log) {
        return log.contains("actionable tasks:") || GRADLE_ERROR_LOGS.stream().anyMatch(log::startsWith);
    }

    private boolean isDockerImageLog(String log) {
        return (log.startsWith("Unable to find image '") && log.endsWith("' locally")) || (log.startsWith("Digest: sha256:") && log.length() == 79)
                || log.startsWith("Status: Downloaded newer image for ") || "Jenkins does not seem to be running inside a container".equals(log)
                || log.startsWith("Jenkins seems to be running inside container ") || ".".equals(log)
                // Each of the following is prefixed by a 12 character hash, so the length is the length of the suffix + 12
                || (log.endsWith(": Pulling fs layer") && log.length() == 30) || (log.endsWith(": Waiting") && log.length() == 21)
                || (log.endsWith(": Verifying Checksum") && log.length() == 32) || (log.endsWith(": Download complete") && log.length() == 31)
                || (log.endsWith(": Pull complete") && log.length() == 27) || log.startsWith("~~~~~~~~~~~~~~~~~~~~ Pull image progress:");
    }

    private static final Set<String> GIT_LOGS = Set.of("Checking out", "Switched to branch", ".git", "Fetching 'refs/heads", "Updating source code to revision",
            "Updated source code to revision", "Creating local git repository", "hint: ", "Initialized empty Git", "Warning: Permanently added", "From ssh://");

    private boolean isGitLog(String log) {
        return log.contains("* [new branch]") || GIT_LOGS.stream().anyMatch(log::startsWith);
    }

    private static final Set<String> BUILD_TASK_LOGS = Set.of("Executing build", "Starting task", "Finished task", "Running pre-build action", "Failing task", "Running post build",
            "Running on server", "Finalising the build...", "Stopping timer.", "Finished building", "All post build plugins have finished", "Publishing an artifact",
            "Unable to publish artifact", "The artifact hasn't been successfully published", "Beginning to execute", "Substituting variable", "Pipeline Maven Plugin",
            "Running in ", "Build ", "Remote agent on ", "Parsing test results", "Successfully removed working directory", "Generating build results summary",
            "Saving build results to disk", "Store variable context");

    private boolean isTaskLog(String log) {
        return BUILD_TASK_LOGS.stream().anyMatch(log::startsWith);
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
            String lastLog = buildLogs.getLast().getLog();
            // If the log does not exist already or if the log is a single blank log add it to the build logs (avoid more than one empty log in a row)
            boolean isSingleBlankLog = shortenedLogString.isBlank() && !lastLog.isBlank();
            return existingLog.isEmpty() || isSingleBlankLog;
        }
        return true;
    }

    /**
     * Filters out unnecessary build logs that a student should not see.
     *
     * @param buildLogEntries     the build logs
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
        return log.contains("COMPILATION ERROR") || log.startsWith("> Compilation failed");
    }

    private boolean isBuildFailure(String log) {
        return log.contains("BUILD FAILURE") || log.startsWith("BUILD FAILED");
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

    /**
     * Delete the build log entries for the given programming submission
     *
     * @param programmingSubmission the programming submission for which the build logs should be deleted
     */
    public void deleteBuildLogEntriesForProgrammingSubmission(ProgrammingSubmission programmingSubmission) {
        programmingSubmission.setBuildLogEntries(Collections.emptyList());
        programmingSubmissionRepository.save(programmingSubmission);
        buildLogEntryRepository.deleteByProgrammingSubmissionId(programmingSubmission.getId());
    }

    /**
     * Save the build logs for a given submission to a file
     *
     * @param buildLogEntries the build logs to save
     * @param buildJobId      the id of the build job for which to save the build logs
     */
    public void saveBuildLogsToFile(List<BuildLogEntry> buildLogEntries, String buildJobId) {

        if (!Files.exists(buildLogsPath)) {
            try {
                Files.createDirectories(buildLogsPath);
            }
            catch (Exception e) {
                throw new IllegalStateException("Could not create directory for build logs", e);
            }
        }

        Path logPath = buildLogsPath.resolve(buildJobId + ".log");

        StringBuilder logsStringBuilder = new StringBuilder();
        for (BuildLogEntry buildLogEntry : buildLogEntries) {
            logsStringBuilder.append(buildLogEntry.getTime()).append("\t").append(buildLogEntry.getLog());
        }

        try {
            FileUtils.writeStringToFile(logPath.toFile(), logsStringBuilder.toString(), StandardCharsets.UTF_8);
            log.debug("Saved build logs for build job {} to file {}", buildJobId, logPath);
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the build logs for a given submission from a file.
     *
     * @param buildJobId the id of the build job for which to retrieve the build logs
     * @return the build logs as a string or null if the file could not be found (e.g. if the build logs have been deleted)
     */
    public FileSystemResource retrieveBuildLogsFromFileForBuildJob(String buildJobId) {
        Path logPath = buildLogsPath.resolve(buildJobId + ".log");

        FileSystemResource fileSystemResource = new FileSystemResource(logPath);
        if (fileSystemResource.exists()) {
            log.debug("Retrieved build logs for build job {} from file {}", buildJobId, logPath);
            return fileSystemResource;
        }
        else {
            log.warn("Could not find build logs for build job {} in file {}", buildJobId, logPath);
            return null;
        }
    }

    /**
     * Scheduled task that deletes old build log files from the continuous integration system.
     * <p>
     * This method runs based on the cron schedule defined in the application properties, with
     * a default value of 3:00 AM every day if no custom schedule is provided.
     * The task will only execute if scheduling is active, which is checked via the {@code profileService}.
     * </p>
     * <p>
     * The method iterates through the files in the configured build logs directory and deletes
     * files that were last modified before the configured expiry period (in days). The expiration
     * period is specified by the {@code expiryDays} variable, and files older than this period are deleted.
     * </p>
     * <p>
     * In case of an error during file deletion, it logs the error and continues processing.
     * </p>
     *
     * @throws IOException if an I/O error occurs while accessing the build log files directory or
     *                         deleting files.
     */
    @Scheduled(cron = "${artemis.continuous-integration.build-log.cleanup-schedule:0 0 3 * * ?}")
    public void deleteOldBuildLogsFiles() {
        // only execute this if scheduling is active
        if (!profileService.isSchedulingActive()) {
            return;
        }
        log.info("Deleting old build log files");
        ZonedDateTime now = ZonedDateTime.now();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(buildLogsPath)) {
            for (Path file : stream) {
                ZonedDateTime lastModified = ZonedDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), now.getZone());
                if (lastModified.isBefore(now.minusDays(expiryDays))) {
                    Files.deleteIfExists(file);
                    log.info("Deleted old build log file {}", file);
                }
            }
        }
        catch (IOException e) {
            log.error("Error occurred while trying to delete old build log files", e);
        }
    }

    public boolean buildJobHasLogFile(String buildJobId) {
        Path logPath = buildLogsPath.resolve(buildJobId + ".log");
        return Files.exists(logPath);
    }

}
