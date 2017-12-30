package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.exception.BambooException;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
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
    private final Optional<ContinuousIntegrationService> continuousIntegrationService;
    private final Optional<VersionControlService> versionControlService;
    private final Optional<GitService> gitService;

    public ExerciseService(ExerciseRepository exerciseRepository, UserService userService, ParticipationService participationService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService, Optional<GitService> gitService) {
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.participationService = participationService;
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
        Authority adminAuthority = new Authority();
        adminAuthority.setName("ROLE_ADMIN");
        Stream<Exercise> userExercises = result.stream().filter(
            exercise -> user.getGroups().contains(exercise.getCourse().getStudentGroupName())
                || user.getGroups().contains(exercise.getCourse().getTeachingAssistantGroupName())
                || user.getAuthorities().contains(adminAuthority)
                || exercise.getCourse().getTitle().equals("Archive") // TODO: Maybe we want to externalize the configuration of the "Archive" course name
        );
        List<Exercise> filteredExercises = userExercises.collect(Collectors.toList());
        return new PageImpl<>(filteredExercises, pageable, filteredExercises.size());
    }

    /**
     * Get one exercise by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Exercise findOne(Long id) {
        log.debug("Request to get Exercise : {}", id);
        return exerciseRepository.findOne(id);
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
            exercise.getParticipations();
        }
        return exercise;
    }

    /**
     * Resets an Exercise by deleting all its Participations
     *
     * @param exercise
     */
    @Transactional
    public void reset(Exercise exercise) {
        log.debug("Request reset Exercise : {}", exercise.getId());

        // delete all participations for this exercise
        for (Participation participation : exercise.getParticipations()) {
            participationService.delete(participation.getId(), true, true);
        }
    }

    /**
     * Delete the  exercise by id.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id) {
        log.debug("Request to delete Exercise : {}", id);
        exerciseRepository.delete(id);
    }

    /**
     * Delete build plans (except BASE) and optionally repositores of all exercise participations.
     *
     * @param id id of the exercise for which build plans in respective participations are deleted
     */
    @Transactional
    public java.io.File cleanup(Long id, boolean deleteRepositories) throws java.io.IOException {
        Exercise exercise = findOneLoadParticipations(id);
        log.info("Request to cleanup all participations for Exercise : {}", exercise.getTitle());
        List<Repository> studentRepositories = new ArrayList<>();
        Path finalZipFilePath = null;

        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participation -> {
                if (participation.getBuildPlanId() != null) {     //ignore participations without build plan id
                    try {
                        continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
                    }
                    catch(BambooException ex) {
                        log.error(ex.getMessage());
                        if (ex.getCause() != null) {
                            log.error(ex.getCause().getMessage());
                        }
                    }

                    participation.setInitializationState(ParticipationState.INACTIVE);
                    participation.setBuildPlanId(null);
                    participationService.save(participation);
                }
                if (deleteRepositories == true && participation.getRepositoryUrl() != null) {     //ignore participations without repository URL
                    try {
                        //1. clone the repository
                        Repository repo = gitService.get().getOrCheckoutRepository(participation);
                        //2. collect the repo file
                        studentRepositories.add(repo);
                    } catch (GitAPIException | IOException ex) {
                        log.error("Archiving and deleting the repository " + participation.getRepositoryUrlAsUrl() + " did not work as expected", ex);
                    }
                }
            });

            if (deleteRepositories == false) {
                return null;    //in this case, we are done
            }

            if (studentRepositories.isEmpty()) {
                log.info("No student repositories have been found.");
                return null;
            }

            //from here on, deleteRepositories is true and does not need to be evaluated again
            log.info("Create zip file for all repositories");
            Files.createDirectories(Paths.get("zippedRepos"));
            finalZipFilePath = Paths.get("zippedRepos", exercise.getCourse().getTitle() + " " + exercise.getTitle() + " Student Repositories.zip");
            zipAllRepositories(studentRepositories, finalZipFilePath);

            exercise.getParticipations().forEach(participation -> {
                if (participation.getRepositoryUrl() != null) {      //ignore participations without repository URL
                    try {
                        //3. delete the locally cloned repo again
                        gitService.get().deleteLocalRepository(participation);
                    } catch (IOException e) {
                        log.error("Archiving and deleting the repository " + participation.getRepositoryUrlAsUrl() + " did not work as expected", e);
                    }
                    //4. finally delete the repository on the VC Server
                    versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
                    participation.setRepositoryUrl(null);
                    participation.setInitializationState(ParticipationState.FINISHED);
                    participationService.save(participation);
                }
            });

            scheduleForDeletion(finalZipFilePath, 300);

        } else {
            log.info("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to cleanup repositories and build plan", id);
            return null;
        }

        return new java.io.File(finalZipFilePath.toString());
    }


    public Path zipAllRepositories(List<Repository> repositories, Path zipFilePath) throws IOException {

        try (ZipOutputStream zipOutputStream = new ZipOutputStream(Files.newOutputStream(zipFilePath))) {

            repositories.forEach(repository -> {
                Path repoPath = repository.getLocalPath();
                Path parentRepoPath = repoPath.getParent();
                try {
                    Files.walk(repoPath)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            ZipEntry zipEntry = new ZipEntry(parentRepoPath.relativize(path).toString());
                            try {
                                zipOutputStream.putNextEntry(zipEntry);
                                Files.copy(path, zipOutputStream);
                                zipOutputStream.closeEntry();
                            } catch (Exception e) {
                                log.error("Create zip file error", e);
                            }
                        });
                } catch (IOException e) {
                    log.error("Create zip file error", e);
                }
            });

        }
        return zipFilePath;
    }


    //does not delete anything
    @Transactional
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
