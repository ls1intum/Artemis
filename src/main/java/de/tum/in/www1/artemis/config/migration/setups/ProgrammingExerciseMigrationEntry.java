package de.tum.in.www1.artemis.config.migration.setups;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import de.tum.in.www1.artemis.domain.AuxiliaryRepository;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ParticipationInterface;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
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
    private int estimatedTimePerRepository;

    protected final ProgrammingExerciseRepository programmingExerciseRepository;

    protected final SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    protected final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    protected final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    protected final AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    protected static final String ERROR_MESSAGE = "Failed to migrate programming exercises within %d hours. Aborting migration.";

    protected final Logger log = LoggerFactory.getLogger(getSubclass());

    protected final CopyOnWriteArrayList<ProgrammingExerciseParticipation> errorList = new CopyOnWriteArrayList<>();

    protected ProgrammingExerciseMigrationEntry(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.solutionProgrammingExerciseParticipationRepository = solutionProgrammingExerciseParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.auxiliaryRepositoryRepository = auxiliaryRepositoryRepository;
    }

    /**
     * Executes this migration entry.
     *
     * @return False if there is a general configuration error, which blocks the whole execution
     *         and true otherwise.
     */
    public boolean execute() {
        if (areValuesIncomplete()) {
            return false;
        }
        var programmingExerciseCount = programmingExerciseRepository.count();
        var studentCount = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(Pageable.unpaged()).getTotalElements();

        if (programmingExerciseCount == 0) {
            log.info("No programming exercises to change");
            return true;
        }

        log.info("Will migrate {} programming exercises and {} student repositories now. This might take a while", programmingExerciseCount, studentCount);

        final long totalFullBatchCount = programmingExerciseCount / batchSize;
        final long threadCount = Math.max(1, Math.min(totalFullBatchCount, maxThreadCount));
        final long estimatedTimeExercise = getRestDurationInSeconds(0, programmingExerciseCount, 3, threadCount);
        final long estimatedTimeStudents = getRestDurationInSeconds(0, studentCount, 1, threadCount);

        final long estimatedTime = (estimatedTimeExercise + estimatedTimeStudents);
        log.info("Using {} threads for migration, and assuming {}s per repository, the migration should take around {}", threadCount, estimatedTimePerRepository,
                TimeLogUtil.formatDuration(estimatedTime));

        // Use fixed thread pool to prevent loading too many exercises into memory at once
        ExecutorService executorService = Executors.newFixedThreadPool((int) threadCount);
        /*
         * migrate the solution participations first, then the template participations, then the student participations
         */
        AtomicInteger solutionCounter = new AtomicInteger(0);
        final var totalNumberOfSolutions = solutionProgrammingExerciseParticipationRepository.count();
        log.info("Found {} solution participations to migrate.", totalNumberOfSolutions);
        for (int currentPageStart = 0; currentPageStart < totalNumberOfSolutions; currentPageStart += batchSize) {
            Pageable pageable = PageRequest.of(currentPageStart / batchSize, batchSize);
            var solutionParticipationPage = solutionProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} solution participations in batch.", solutionParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateSolutions(solutionParticipationPage.toList());
                solutionCounter.addAndGet(solutionParticipationPage.getNumberOfElements());
                logProgress(solutionCounter.get(), totalNumberOfSolutions, threadCount, 2, "solution");
            });
        }

        log.info("Submitted all solution participations to thread pool for migration.");
        /*
         * migrate the template participations
         */
        AtomicInteger templateCounter = new AtomicInteger(0);
        var templateCount = templateProgrammingExerciseParticipationRepository.count();
        log.info("Found {} template participations to migrate", templateCount);
        for (int currentPageStart = 0; currentPageStart < templateCount; currentPageStart += batchSize) {
            Pageable pageable = PageRequest.of(currentPageStart / batchSize, batchSize);
            var templateParticipationPage = templateProgrammingExerciseParticipationRepository.findAll(pageable);
            log.info("Will migrate {} template programming exercises in batch.", templateParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateTemplates(templateParticipationPage.toList());
                templateCounter.addAndGet(templateParticipationPage.getNumberOfElements());
                logProgress(templateCounter.get(), templateCount, threadCount, 1, "template");
            });
        }

        log.info("Submitted all template participations to thread pool for migration.");

        /*
         * migrate the student participations
         */
        AtomicInteger studentCounter = new AtomicInteger(0);
        log.info("Found {} student programming exercise participations with build plans to migrate.", studentCount);
        for (int currentPageStart = 0; currentPageStart < studentCount; currentPageStart += batchSize) {
            Pageable pageable = PageRequest.of(currentPageStart / batchSize, batchSize);
            Page<ProgrammingExerciseStudentParticipation> studentParticipationPage = programmingExerciseStudentParticipationRepository.findAllWithRepositoryUri(pageable);
            log.info("Will migrate {} student programming exercise participations in batch.", studentParticipationPage.getNumberOfElements());
            executorService.submit(() -> {
                migrateStudents(studentParticipationPage.toList());
                studentCounter.addAndGet(studentParticipationPage.getNumberOfElements());
                logProgress(studentCounter.get(), studentCount, threadCount, 1, "student");
            });
        }

        log.info("Submitted all student participations to thread pool for migration.");

        shutdown(executorService, timeoutInHours, ERROR_MESSAGE.formatted(timeoutInHours));
        log.info("Finished migrating programming exercises and student participations");
        evaluateErrorList(programmingExerciseRepository);
        return true;
    }

    protected abstract boolean areValuesIncomplete();

    /**
     * Returns a list of auxiliary repositories for the given exercise.
     *
     * @param exerciseId The id of the exercise
     * @return A list of auxiliary repositories, or an empty list if the migration service does not support auxiliary repositories
     */
    protected List<AuxiliaryRepository> getAuxiliaryRepositories(Long exerciseId) {
        return auxiliaryRepositoryRepository.findByExerciseId(exerciseId);
    }

    /**
     * Migrate the solution participations. Also Migrates the test and aux repository of the programming exercise since we have it
     * in the solution participation already loaded from the database.
     *
     * @param solutionParticipations the solution participations to migrate
     */
    protected abstract void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations);

    /**
     * Migrate the template participations.
     *
     * @param templateParticipations list of template participations to migrate
     */
    protected abstract void migrateTemplates(List<TemplateProgrammingExerciseParticipation> templateParticipations);

    /**
     * Migrate the student participations.
     *
     * @param participations list of student participations to migrate
     */
    protected abstract void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations);

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

    /**
     * Checks if the repository uri of the given participation is not null
     * and logs for the case of null.
     *
     * @param programmingExerciseParticipation participation which repository uri should be checked.
     * @param errorMessage                     Message for the error logging, if the uri is null.
     *                                             Including one parameter for the participation id.
     * @return False if the repository uri is null and true otherwise
     */
    protected boolean isRepositoryUriNotNull(ProgrammingExerciseParticipation programmingExerciseParticipation, String errorMessage) {
        boolean result = programmingExerciseParticipation.getRepositoryUri() != null;
        if (!result) {
            log.error(errorMessage, programmingExerciseParticipation.getId());
            errorList.add(programmingExerciseParticipation);
        }
        return result;
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
