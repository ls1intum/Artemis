package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import org.eclipse.jgit.api.errors.GitAPIException;
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

    public ExerciseService(ExerciseRepository exerciseRepository,
                           UserService userService,
                           ParticipationService participationService,
                           AuthorizationCheckService authCheckService,
                           Optional<ContinuousIntegrationService> continuousIntegrationService,
                           Optional<VersionControlService> versionControlService,
                           Optional<GitService> gitService) {
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.authCheckService = authCheckService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
        this.gitService = gitService;
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
            return null;
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
    }

    /**
     * Delete the exercise by id and all its participations.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Request to delete Exercise : {}", id);
        // delete all participations belonging to this quiz
        participationService.deleteAllByExerciseId(id, false, false);
        exerciseRepository.deleteById(id);
    }

    /**
     * Delete build plans (except BASE) and optionally git repositories of all exercise participations.
     *
     * @param id id of the exercise for which build plans in respective participations are deleted
     */
    @Transactional(noRollbackFor={Throwable.class})
    public void cleanup(Long id, boolean deleteRepositories) throws IOException {
        Exercise exercise = findOneLoadParticipations(id);
        log.info("Request to cleanup all participations for Exercise : {}", exercise.getTitle());

        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participation -> {
                if (participation.getBuildPlanId() != null) {     //ignore participations without build plan id
                    try {
                        continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
                    }
                    catch(Exception ex) {
                        log.error(ex.getMessage());
                        if (ex.getCause() != null) {
                            log.error(ex.getCause().getMessage());
                        }
                    }

                    participation.setInitializationState(InitializationState.INACTIVE);
                    participation.setBuildPlanId(null);
                    participationService.save(participation);
                }
            });

            if (deleteRepositories == false) {
                return ;    //in this case, we are done
            }

            exercise.getParticipations().forEach(participation -> {
                if (participation.getRepositoryUrl() != null) {      //ignore participations without repository URL
                    //delete the repository on the VC Server
                    versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
                    participation.setRepositoryUrl(null);
                    participation.setInitializationState(InitializationState.FINISHED);
                    participationService.save(participation);
                }
            });


        } else {
            log.info("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", id);
            return;
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
                } catch (IOException | GitAPIException ex) {
                    log.error("export repository Participation for " + participation.getRepositoryUrlAsUrl() + "and Students" + studentIds + " did not work as expected");
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
                } catch (IOException | GitAPIException ex) {
                    log.error("Archiving and deleting the repository " + participation.getRepositoryUrlAsUrl() + " did not work as expected");
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
