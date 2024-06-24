package de.tum.in.www1.artemis.config.migration.setups;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ParticipationInterface;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.service.util.TimeLogUtil;

public abstract class ProgrammingExerciseMigrationEntry {

    @Value("${migration.scaling.batch-size:100}")
    protected int batchSize;

    @Value("${migration.scaling.max-thread-count:32}")
    protected int maxThreadCount;

    @Value("${migration.scaling.timeout-in-hours:48}")
    protected int timeoutInHours;

    /**
     * Value in seconds
     */
    @Value("${migration.scaling.estimated-time-per-repository:2}")
    protected int estimatedTimePerRepository;

    protected static final String ERROR_MESSAGE = "Failed to migrate programming exercises within %d hours. Aborting migration.";

    protected final Logger log = LoggerFactory.getLogger(getSubclass());

    protected final CopyOnWriteArrayList<ProgrammingExerciseParticipation> errorList = new CopyOnWriteArrayList<>();

    /**
     * Returns the type of the concrete subclass.
     * It is used to make the logging related to the subclass, to make the log more readable.
     *
     * @return Class that is the concrete subclass.
     */
    protected abstract Class<?> getSubclass();

    protected void logProgress(long doneCount, long totalCount, long threadCount, long reposPerEntry, String migrationType) {
        final double percentage = ((double) doneCount / totalCount) * 100;
        log.info("Migrated {}/{} {} participations ({}%)", doneCount, totalCount, migrationType, String.format("%.2f", percentage));
        log.info("Estimated time remaining: {} for {} repositories", TimeLogUtil.formatDuration(getRestDurationInSeconds(doneCount, totalCount, reposPerEntry, threadCount)),
                migrationType);
    }

    protected long getRestDurationInSeconds(final long done, final long total, final long reposPerEntry, final long threads) {
        final long stillTodo = total - done;
        final long timePerEntry = estimatedTimePerRepository * reposPerEntry;
        return (stillTodo * timePerEntry) / threads;
    }

    protected void shutdown(ExecutorService executorService, int timeoutInHours, String errorMessage) {
        // Wait for all threads to finish
        executorService.shutdown();

        try {
            boolean finished = executorService.awaitTermination(timeoutInHours, TimeUnit.HOURS);
            if (!finished) {
                log.error(errorMessage);
                if (executorService.awaitTermination(1, TimeUnit.MINUTES)) {
                    log.error("Failed to cancel all migration threads. Some threads are still running.");
                }
                throw new RuntimeException(errorMessage);
            }
        }
        catch (InterruptedException e) {
            log.error(errorMessage);
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * Evaluates the error map and prints the errors to the log.
     */
    protected void evaluateErrorList(ProgrammingExerciseRepository programmingExerciseRepository) {
        if (errorList.isEmpty()) {
            log.info("Successfully migrated all programming exercises");
            return;
        }

        List<Long> failedTemplateExercises = errorList.stream().filter(participation -> participation instanceof TemplateProgrammingExerciseParticipation)
                .map(participation -> getProgrammingExerciseId(programmingExerciseRepository, participation)).filter(Objects::nonNull).toList();
        List<Long> failedSolutionExercises = errorList.stream().filter(participation -> participation instanceof SolutionProgrammingExerciseParticipation)
                .map(participation -> getProgrammingExerciseId(programmingExerciseRepository, participation)).filter(Objects::nonNull).toList();
        List<Long> failedStudentParticipations = errorList.stream().filter(participation -> participation instanceof ProgrammingExerciseStudentParticipation)
                .map(ParticipationInterface::getId).toList();

        log.error("{} failures during migration", errorList.size());
        // print each participation in a single line in the long to simplify reviewing the issues
        log.error("Errors occurred for the following participations: \n{}", errorList.stream().map(Object::toString).collect(Collectors.joining("\n")));
        log.error("Failed to migrate template build plan for exercises: {}", failedTemplateExercises);
        log.error("Failed to migrate solution build plan for exercises: {}", failedSolutionExercises);
        log.error("Failed to migrate students participations: {}", failedStudentParticipations);
        // TODO Adjust this message to our situation here, how to restart the migration
        log.warn("Please check the logs for more information. If the issues are related to the external VCS/CI system, fix the issues and rerun the migration. or "
                + "fix the build plans yourself and mark the migration as run. The migration can be rerun by deleting the migration entry in the database table containing "
                + "the migration with author: {} and date_string: {} and then restarting Artemis.", "author()", "date()");
    }

    /**
     * Sometimes the programming exercise is not set in the participation, so we need to get it from the repository
     *
     * @param repository    the repository to get the programming exercise from
     * @param participation the participation to get the programming exercise from
     * @return the id of the programming exercise
     */
    private Long getProgrammingExerciseId(ProgrammingExerciseRepository repository, ProgrammingExerciseParticipation participation) {
        ProgrammingExercise programmingExercise = participation.getProgrammingExercise();

        if (programmingExercise == null) {
            programmingExercise = repository.getProgrammingExerciseFromParticipation(participation);
            if (programmingExercise == null) {
                return null;
            }
        }
        return programmingExercise.getId();
    }
}
