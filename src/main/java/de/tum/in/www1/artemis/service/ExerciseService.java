package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.exception.GitException;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.service.connectors.ContinuousIntegrationService;
import de.tum.in.www1.artemis.service.connectors.GitService;
import de.tum.in.www1.artemis.service.connectors.VersionControlService;
import de.tum.in.www1.artemis.service.scheduled.QuizScheduleService;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service Implementation for managing Exercise.
 */
@Service
@Transactional
public class ExerciseService {

    private final Logger log = LoggerFactory.getLogger(ExerciseService.class);

    private final ExerciseRepository exerciseRepository;
    private final UserService userService;
    private final ParticipationService participationService;
    private final AuthorizationCheckService authCheckService;
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<GitService> gitService;
    private final StatisticService statisticService;
    private final QuizScheduleService quizScheduleService;

    public ExerciseService(ExerciseRepository exerciseRepository,
                           UserService userService,
                           ParticipationService participationService,
                           AuthorizationCheckService authCheckService,
                           Optional<ContinuousIntegrationService> continuousIntegrationService,
                           Optional<VersionControlService> versionControlService,
                           Optional<GitService> gitService,
                           StatisticService statisticService,
                           QuizScheduleService quizScheduleService) {
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
        this.statisticService = statisticService;
        this.quizScheduleService = quizScheduleService;
    }

    /**
     * Save a exercise.
     *
     * @param exercise the entity to save
     * @return the persisted entity
     */
    public Exercise save(Exercise exercise) {
        log.debug("Request to save Exercise : {}", exercise);
        return exerciseRepository.save(exercise);
    }

    /**
     * Get all the exercises.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<Exercise> findAll(Pageable pageable) {
        log.debug("Request to get all Exercises");
        List<Exercise> result = exerciseRepository.findAll();
        User user = userService.getUserWithGroupsAndAuthorities();
        Stream<Exercise> userExercises = result.stream().filter(
            exercise -> authCheckService.isAllowedToSeeExercise(exercise, user));
        List<Exercise> filteredExercises = userExercises.collect(Collectors.toList());
        return new PageImpl<>(userExercises.collect(Collectors.toList()), pageable, filteredExercises.size());
    }

    /**
     * Get all exercises by courseID
     *
     * @param course  for return of exercises in course
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<Exercise> findAllExercisesByCourseId(Course course, User user) {
        List<Exercise> exercises = null;
        if (authCheckService.isAdmin() ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isTeachingAssistantInCourse(course, user)) {
            // user can see this exercise
            exercises = exerciseRepository.findAllByCourseId(course.getId());
        }   return exercises;
    }

    @Transactional(readOnly = true)
    public List<Exercise> findAllForCourse(Course course, boolean withLtiOutcomeUrlExisting, Principal principal, User user) {
        List<Exercise> exercises = null;
        if (authCheckService.isAdmin() ||
            authCheckService.isInstructorInCourse(course, user) ||
            authCheckService.isTeachingAssistantInCourse(course, user)) {
            // user can see this exercise
            exercises = exerciseRepository.findByCourseId(course.getId());
        }
        else if (authCheckService.isStudentInCourse(course, user)) {
            // user is student for this course and might not have the right to see it so we have to filter
            exercises = withLtiOutcomeUrlExisting ? exerciseRepository.findByCourseIdWhereLtiOutcomeUrlExists(course.getId(), principal) : exerciseRepository.findByCourseId(course.getId());
            // filter out exercises that are not released (or explicitly made visible to students) yet
            exercises = exercises.stream().filter(Exercise::isVisibleToStudents).collect(Collectors.toList());
        }

        // filter out questions and all statistical information about the quizPointStatistic from quizExercises (so users can't see which answer options are correct)
        for (Exercise exercise : exercises) {
            if (exercise instanceof QuizExercise) {
                QuizExercise quizExercise = (QuizExercise) exercise;
                quizExercise.filterSensitiveInformation();
            }
        }

        return exercises;
    }

    /**
     * Get one exercise by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Exercise findOne(Long id) {
        Optional<Exercise> exercise = exerciseRepository.findById(id);
        if (!exercise.isPresent()) {
            throw new EntityNotFoundException("Exercise with id " + id + " does not exist!");
        }
        if (exercise.get() instanceof QuizExercise) {
            QuizExercise quizExercise = (QuizExercise) exercise.get();
            //eagerly load questions and statistic
            quizExercise.getQuestions().size();
            quizExercise.getQuizPointStatistic().getId();
        }
        return exercise.get();
    }

    /**
     * Find exercise by id and load participations in this exercise.
     *
     * @param id the id of the exercise entity
     * @return the exercise entity
     */
    @Transactional(readOnly = true)
    public Exercise findOneLoadParticipations(Long id) {
        log.debug("Request to find Exercise with participations loaded: {}", id);
        Exercise exercise = findOne(id);
        if(Optional.ofNullable(exercise).isPresent()) {
            exercise.getParticipations().size();
        }
        return exercise;
    }

    /**
     * Resets an Exercise by deleting all its Participations
     *
     * @param exercise
     */
    @Transactional(noRollbackFor={Throwable.class})
    public void reset(Exercise exercise) {
        log.debug("Request reset Exercise : {}", exercise.getId());

        // delete all participations for this exercise
        for (Participation participation : exercise.getParticipations()) {
            participationService.delete(participation.getId(), true, true);
        }

        if (exercise instanceof QuizExercise) {

            // refetch exercise to make sure we have an updated version
            exercise = findOneLoadParticipations(exercise.getId());

            //for quizzes we need to delete the statistics and we need to reset the quiz to its original state
            QuizExercise quizExercise = (QuizExercise) exercise;
            quizExercise.setIsVisibleBeforeStart(Boolean.FALSE);
            quizExercise.setIsPlannedToStart(Boolean.FALSE);
            quizExercise.setIsOpenForPractice(Boolean.FALSE);
            quizExercise.setReleaseDate(null);


            //TODO: the dependencies to concrete exercise types here are not really nice. We should find a better way to structure this, e.g. having this call managed in the quiz exercise resource
            // which delegates some functionality to the exercise service

            //in case the quiz has not yet started or the quiz is currently running, we have to cleanup
            quizScheduleService.cancelScheduledQuizStart(quizExercise.getId());
            quizScheduleService.clearQuizData(quizExercise.getId());

            // clean up the statistics
            statisticService.recalculateStatistics(quizExercise);
        }
    }

    /**
     * Delete the exercise by id and all its participations.
     *
     * @param exercise the exercise to be deleted
     * @param deleteStudentReposBuildPlans whether the student repos and build plans should be deleted
     * @param deleteBaseReposBuildPlans whether the template and solution repos and build plans should be deleted
     */
    @Transactional
    public void delete(Exercise exercise, boolean deleteStudentReposBuildPlans, boolean deleteBaseReposBuildPlans) {
        log.debug("Request to delete Exercise : {}", exercise.getTitle());
        // delete all participations belonging to this quiz
        participationService.deleteAllByExerciseId(exercise.getId(), deleteStudentReposBuildPlans, deleteStudentReposBuildPlans);
        if (exercise instanceof ProgrammingExercise && deleteBaseReposBuildPlans) {
            ProgrammingExercise programmingExercise = (ProgrammingExercise) exercise;
            if (programmingExercise.getBaseBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getBaseBuildPlanId());
            }
            if (programmingExercise.getSolutionBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(programmingExercise.getSolutionBuildPlanId());
            }
            continuousIntegrationService.get().deleteProject(programmingExercise.getProjectKey());

            if (programmingExercise.getBaseRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getBaseRepositoryUrlAsUrl());
                gitService.get().deleteLocalRepository(programmingExercise.getBaseRepositoryUrlAsUrl());
            }
            if (programmingExercise.getSolutionRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getSolutionRepositoryUrlAsUrl());
                gitService.get().deleteLocalRepository(programmingExercise.getSolutionRepositoryUrlAsUrl());
            }
            if (programmingExercise.getTestRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(programmingExercise.getTestRepositoryUrlAsUrl());
                gitService.get().deleteLocalRepository(programmingExercise.getTestRepositoryUrlAsUrl());
            }
            versionControlService.get().deleteProject(programmingExercise.getProjectKey());
        }
        exerciseRepository.deleteById(exercise.getId());
    }

    /**
     * Delete build plans (except BASE) and optionally git repositories of all exercise participations.
     *
     * @param id id of the exercise for which build plans in respective participations are deleted
     */
    @Transactional(noRollbackFor={Throwable.class})
    public void cleanup(Long id, boolean deleteRepositories) {
        Exercise exercise = findOneLoadParticipations(id);
        log.info("Request to cleanup all participations for Exercise : {}", exercise.getTitle());

        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participationService::cleanupBuildPlan);

            if (!deleteRepositories) {
                return;    //in this case, we are done
            }

            exercise.getParticipations().forEach(participationService::cleanupRepository);

        } else {
            log.warn("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", id);
        }
    }

    /**
     * Get participations of coding exercises of a requested list of students packed together in one zip file.
     *
     * @param exerciseId the id of the exercise entity
     * @param studentIds TUM Student-Login ID of requested students
     * @return a zip file containing all requested participations
     */
    @Transactional(readOnly = true)
    public java.io.File exportParticipations(Long exerciseId, List<String> studentIds) {
        Exercise exercise = findOneLoadParticipations(exerciseId);
        List<Path> zippedRepoFiles = new ArrayList<>();
        Path zipFilePath = null;
        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participation -> {
                try {
                    if (participation.getRepositoryUrl() != null && studentIds.contains(participation.getStudent().getLogin())) {
                        boolean repoAlreadyExists = gitService.get().repositoryAlreadyExists(participation.getRepositoryUrlAsUrl());

                        Repository repo = gitService.get().getOrCheckoutRepository(participation);
                        log.debug("Create temporary zip file for repository " + repo.getLocalPath().toString());
                        Path zippedRepoFile = gitService.get().zipRepository(repo);
                        zippedRepoFiles.add(zippedRepoFile);
                        boolean allowInlineEditor = ((ProgrammingExercise) exercise).isAllowOnlineEditor() != null && ((ProgrammingExercise) exercise).isAllowOnlineEditor();
                        if(!allowInlineEditor){ //if onlineeditor is not allowed we are free to delete
                            log.debug("Delete temporary repoistory "+ repo.getLocalPath().toString());
                            gitService.get().deleteLocalRepository(participation);
                        }
                        if (allowInlineEditor && !repoAlreadyExists){ //if onlineEditor is allowed only delete if the repo didn't exist beforehand
                            log.debug("Delete temporary repoistory "+ repo.getLocalPath().toString());
                            gitService.get().deleteLocalRepository(participation);
                        }

                    }
                } catch (IOException | GitException | InterruptedException ex) {
                    log.error("export repository Participation for " + participation.getRepositoryUrlAsUrl() + "and Students" + studentIds + " did not work as expected: " + ex);
                }
            });
            if (!exercise.getParticipations().isEmpty() && !zippedRepoFiles.isEmpty()) {
                try {
                    // create a large zip file with all zipped repos and provide it for download
                    log.debug("Create zip file for all repositories");
                    zipFilePath = Paths.get(zippedRepoFiles.get(0).getParent().toString(), exercise.getCourse().getTitle() + " " + exercise.getTitle() +studentIds.hashCode()+ ".zip");
                    createZipFile(zipFilePath, zippedRepoFiles);
                    scheduleForDeletion(zipFilePath, 10);

                    log.debug("Delete all temporary zip repo files");
                    //delete the temporary zipped repo files
                    for (Path zippedRepoFile : zippedRepoFiles) {
                        Files.delete(zippedRepoFile);
                    }
                } catch (IOException ex) {
                    log.error("Archiving and deleting the local repositories did not work as expected");
                }
            }
            else {
                log.debug("The zip file could not be created. Ignoring the request to export repositories", exerciseId);
                return null;
            }
        }
        else {
            log.debug("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to export repositories", exerciseId);
            return null;
        }
        return new java.io.File(zipFilePath.toString());
    }

    //does not delete anything
    @Transactional(readOnly = true)
    public java.io.File archive(Long id) {
        Exercise exercise = findOneLoadParticipations(id);
        log.info("Request to archive all participations repositories for Exercise : {}", exercise.getTitle());
        List<Path> zippedRepoFiles = new ArrayList<>();
        Path finalZipFilePath = null;
        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participation -> {
                try {
                    if (participation.getRepositoryUrl() != null) {     //ignore participations without repository URL
                        //1. clone the repository
                        Repository repo = gitService.get().getOrCheckoutRepository(participation);
                        //2. zip repository and collect the zip file
                        log.info("Create temporary zip file for repository " + repo.getLocalPath().toString());
                        Path zippedRepoFile = gitService.get().zipRepository(repo);
                        zippedRepoFiles.add(zippedRepoFile);
                        //3. delete the locally cloned repo again
                        gitService.get().deleteLocalRepository(participation);
                    }
                } catch (IOException | GitException | InterruptedException ex) {
                    log.error("Archiving and deleting the repository " + participation.getRepositoryUrlAsUrl() + " did not work as expected: " + ex);
                }
            });


            if (!exercise.getParticipations().isEmpty() && !zippedRepoFiles.isEmpty()) {
                try {
                    // create a large zip file with all zipped repos and provide it for download
                    log.info("Create zip file for all repositories");
                    finalZipFilePath = Paths.get(zippedRepoFiles.get(0).getParent().toString(), exercise.getCourse().getTitle() + " " + exercise.getTitle() + " Student Repositories.zip");
                    createZipFile(finalZipFilePath, zippedRepoFiles);
                    scheduleForDeletion(finalZipFilePath, 300);

                    log.info("Delete all temporary zip repo files");
                    //delete the temporary zipped repo files
                    for (Path zippedRepoFile : zippedRepoFiles) {
                        Files.delete(zippedRepoFile);
                    }
                } catch (IOException ex) {
                    log.error("Archiving and deleting the local repositories did not work as expected");
                }
            }
            else {
                log.info("The zip file could not be created. Ignoring the request to archive repositories", id);
                return null;
            }
        }
        else {
            log.info("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to archive repositories", id);
            return null;
        }
        return new java.io.File(finalZipFilePath.toString());
    }


    private void createZipFile(Path zipFilePath, List<Path> paths) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {
            paths.stream()
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(path.toString());
                    try {
                        zipOutputStream.putNextEntry(zipEntry);
                        Files.copy(path, zipOutputStream);
                        zipOutputStream.closeEntry();
                    } catch (Exception e) {
                        log.error("Create zip file error", e);
                    }
                });
        }
    }

    private Map<Path, ScheduledFuture> futures = new HashMap<>();

    private ScheduledExecutorService executor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static final TimeUnit UNITS = TimeUnit.SECONDS; // your time unit

    private void scheduleForDeletion(Path path, long delay) {
        ScheduledFuture future = executor.schedule(() -> {
            try {
                log.info("Delete file " + path);
                Files.delete(path);
                futures.remove(path);
            } catch (IOException e) {
                log.error("Deleting the file " + path + " did not work", e);
            }
        }, delay, UNITS);

        futures.put(path, future);
    }
}
