package de.tum.in.www1.artemis.service.connectors;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.in.www1.artemis.domain.BuildLogEntry;
import de.tum.in.www1.artemis.repository.BuildLogStatisticsEntryRepository;
import de.tum.in.www1.artemis.repository.FeedbackRepository;
import de.tum.in.www1.artemis.repository.ProgrammingSubmissionRepository;
import de.tum.in.www1.artemis.service.BuildLogEntryService;
import de.tum.in.www1.artemis.service.hestia.TestwiseCoverageService;

public abstract class AbstractContinuousIntegrationService implements ContinuousIntegrationService {

    private final Logger log = LoggerFactory.getLogger(AbstractContinuousIntegrationService.class);

    protected final ProgrammingSubmissionRepository programmingSubmissionRepository;

    protected final FeedbackRepository feedbackRepository;

    protected final BuildLogEntryService buildLogService;

    protected final BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository;

    protected final TestwiseCoverageService testwiseCoverageService;

    public AbstractContinuousIntegrationService(ProgrammingSubmissionRepository programmingSubmissionRepository, FeedbackRepository feedbackRepository,
            BuildLogEntryService buildLogService, BuildLogStatisticsEntryRepository buildLogStatisticsEntryRepository, TestwiseCoverageService testwiseCoverageService) {
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.feedbackRepository = feedbackRepository;
        this.buildLogService = buildLogService;
        this.buildLogStatisticsEntryRepository = buildLogStatisticsEntryRepository;
        this.testwiseCoverageService = testwiseCoverageService;
    }

    /**
     * Find the ZonedDateTime of the first BuildLogEntry that contains the searchString in the log message.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, String searchString) {
        return getTimestampForLogEntry(buildLogEntries, searchString, 0);
    }

    /**
     * Find the ZonedDateTime of the nth BuildLogEntry that contains the searchString in the log message.
     * This method does not return the first entry that matches the searchString but skips skipEntries matching BuildLogEntries.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @param skipEntries     the number of matching BuildLogEntries that should be skipped
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, String searchString, int skipEntries) {
        return getTimestampForLogEntry(buildLogEntries, buildLogEntry -> buildLogEntry.getLog().contains(searchString), skipEntries);
    }

    /**
     * Find the ZonedDateTime of the first BuildLogEntry that matches the given predicate.
     *
     * @param buildLogEntries   the BuildLogEntries that should be searched
     * @param matchingPredicate the predicate that must be true for the BuildLogEntry
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, Predicate<BuildLogEntry> matchingPredicate) {
        return getTimestampForLogEntry(buildLogEntries, matchingPredicate, 0);
    }

    /**
     * Find the ZonedDateTime of the nth BuildLogEntry that matches the given predicate.
     * This method does not return the first entry that matches the given predicate but skips skipEntries matching BuildLogEntries.
     *
     * @param buildLogEntries   the BuildLogEntries that should be searched
     * @param matchingPredicate the predicate that must be true for the BuildLogEntry
     * @param skipEntries       the number of matching BuildLogEntries that should be skipped
     * @return the ZonedDateTime of the found BuildLogEntry, or null if none was found
     */
    protected ZonedDateTime getTimestampForLogEntry(List<BuildLogEntry> buildLogEntries, Predicate<BuildLogEntry> matchingPredicate, int skipEntries) {
        return buildLogEntries.stream().filter(matchingPredicate).skip(skipEntries).findFirst().map(BuildLogEntry::getTime).orElse(null);
    }

    /**
     * Count the number of log entries that contain the searchString in the log message.
     *
     * @param buildLogEntries the BuildLogEntries that should be searched
     * @param searchString    the text that must be contained in the log message
     * @return the number of matching log entries
     */
    protected Integer countMatchingLogs(List<BuildLogEntry> buildLogEntries, String searchString) {
        return Math.toIntExact(buildLogEntries.stream().filter(buildLogEntry -> buildLogEntry.getLog().contains(searchString)).count());
    }
}
