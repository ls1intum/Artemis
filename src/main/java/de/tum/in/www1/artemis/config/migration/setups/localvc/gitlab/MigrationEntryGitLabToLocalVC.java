package de.tum.in.www1.artemis.config.migration.setups.localvc.gitlab;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.eclipse.jgit.api.Git;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.setups.localvc.LocalVCMigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.VcsRepositoryUri;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.localvc.LocalVCInternalException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;
import de.tum.in.www1.artemis.service.connectors.vcs.AbstractVersionControlService;

@Component
@Profile("gitlab")
public class MigrationEntryGitLabToLocalVC extends LocalVCMigrationEntry {

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path:#{null}}")
    private String localVCBasePath;

    private final UriService uriService;

    private final Optional<AbstractVersionControlService> sourceVersionControlService;

    public MigrationEntryGitLabToLocalVC(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository,
            Optional<AbstractVersionControlService> sourceVersionControlService, UriService uriService) {
        super(programmingExerciseRepository, solutionProgrammingExerciseParticipationRepository, templateProgrammingExerciseParticipationRepository,
                programmingExerciseStudentParticipationRepository, auxiliaryRepositoryRepository);
        this.sourceVersionControlService = sourceVersionControlService;
        this.uriService = uriService;
    }

    @Override
    protected boolean areValuesIncomplete() {
        if (super.areValuesIncomplete()) {
            return true;
        }
        if (localVCBasePath == null) {
            log.error("Migration failed because the local VC base path is not configured.");
            return true;
        }
        if (defaultBranch == null) {
            log.error("Migration failed because the default branch is not configured.");
            return true;
        }
        return false;
    }

    @Override
    protected Class<?> getSubclass() {
        return MigrationEntryGitLabToLocalVC.class;
    }

    private String migrateTestRepo(ProgrammingExercise programmingExercise) throws URISyntaxException {
        return cloneRepositoryFromSourceVCSAndMoveToLocalVC(programmingExercise, programmingExercise.getTestRepositoryUri(), programmingExercise.getBranch());
    }

    /**
     * Migrate auxiliary repositories of the given programming exercise.
     *
     * @param solutionParticipation the solution participation to migrate the auxiliary repositories for
     * @param programmingExercise   the programming exercise to migrate the auxiliary repositories for
     * @param oldBranch             the old branch of the programming exercise
     */
    private void migrateAuxiliaryRepositories(SolutionProgrammingExerciseParticipation solutionParticipation, ProgrammingExercise programmingExercise, String oldBranch) {
        for (var repo : getAuxiliaryRepositories(programmingExercise.getId())) {
            try {
                if (repo.getRepositoryUri() == null) {
                    log.error("Repository URI is null for auxiliary repository with id {}, cant migrate", repo.getId());
                    continue;
                }
                var url = cloneRepositoryFromSourceVCSAndMoveToLocalVC(programmingExercise, repo.getRepositoryUri(), oldBranch);
                if (url == null) {
                    errorList.add(solutionParticipation);
                    log.error("Failed to migrate auxiliary repository for programming exercise with id {}, keeping the url in the database", programmingExercise.getId());
                }
                else {
                    log.debug("Migrated auxiliary repository with id {} to {}", repo.getId(), url);
                    repo.setRepositoryUri(url);
                    auxiliaryRepositoryRepository.save(repo);
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate auxiliary repository with id {}", repo.getId(), e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations) {
        for (var solutionParticipation : solutionParticipations) {
            try {
                if (isRepositoryUriNotNull(solutionParticipation, "Repository URI is null for solution participation with id {}, cant migrate")) {
                    var programmingExercise = solutionParticipation.getProgrammingExercise();
                    var url = cloneRepositoryFromSourceVCSAndMoveToLocalVC(solutionParticipation.getProgrammingExercise(), solutionParticipation.getRepositoryUri(),
                            programmingExercise.getBranch());
                    if (url == null) {
                        log.error("Failed to migrate solution repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                        errorList.add(solutionParticipation);
                    }
                    else {
                        log.debug("Migrated solution participation with id {} to {}", solutionParticipation.getId(), url);
                        solutionParticipation.setRepositoryUri(url);
                        solutionProgrammingExerciseParticipationRepository.save(solutionParticipation);
                    }
                    url = migrateTestRepo(solutionParticipation.getProgrammingExercise());
                    var oldBranch = programmingExercise.getBranch();
                    if (url == null) {
                        log.error("Failed to migrate test repository for solution participation with id {}, keeping the url in the database", solutionParticipation.getId());
                        errorList.add(solutionParticipation);
                    }
                    else {
                        log.debug("Migrated test repository for solution participation with id {} to {}", solutionParticipation.getId(), url);
                        if (!defaultBranch.equals(programmingExercise.getBranch())) {
                            programmingExercise.setBranch(defaultBranch);
                            log.debug("Changed branch of programming exercise with id {} to {}", programmingExercise.getId(), programmingExercise.getBranch());
                        }
                        programmingExercise.setTestRepositoryUri(url);
                        programmingExerciseRepository.save(programmingExercise);
                    }
                    migrateAuxiliaryRepositories(solutionParticipation, programmingExercise, oldBranch);
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate solution participation with id {}", solutionParticipation.getId(), e);
                errorList.add(solutionParticipation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void migrateTemplates(List<TemplateProgrammingExerciseParticipation> templateParticipations) {
        for (var templateParticipation : templateParticipations) {
            try {
                if (isRepositoryUriNotNull(templateParticipation, "Repository URI is null for template participation with id {}, cant migrate")) {
                    var url = cloneRepositoryFromSourceVCSAndMoveToLocalVC(templateParticipation.getProgrammingExercise(), templateParticipation.getRepositoryUri(),
                            templateParticipation.getProgrammingExercise().getBranch());
                    if (url == null) {
                        log.error("Failed to migrate template repository for template participation with id {}, keeping the url in the database", templateParticipation.getId());
                        errorList.add(templateParticipation);
                    }
                    else {
                        log.debug("Migrated template participation with id {} to {}", templateParticipation.getId(), url);
                        templateParticipation.setRepositoryUri(url);
                        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
                    }
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate template participation with id {}", templateParticipation.getId(), e);
                errorList.add(templateParticipation);
            }
        }
    }

    /**
     * Migrate the student participations. This is the most time-consuming part of the migration as we have
     * to clone the repository for each student.
     *
     * @param participations list of student participations to migrate
     */
    @Override
    protected void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        for (var participation : participations) {
            try {
                if (isRepositoryUriNotNull(participation, "Repository URI is null for student participation with id {}, cant migrate")) {
                    var url = cloneRepositoryFromSourceVCSAndMoveToLocalVC(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBranch());
                    if (url == null) {
                        log.error("Failed to migrate student repository for student participation with id {}, keeping the url in the database", participation.getId());
                        errorList.add(participation);
                    }
                    else {
                        log.debug("Migrated student participation with id {} to {}", participation.getId(), url);
                        if (participation.getBranch() != null) {
                            participation.setBranch(defaultBranch);
                            log.debug("Changed branch of student participation with id {} to {}", participation.getId(), participation.getBranch());
                        }
                        participation.setRepositoryUri(url);
                        programmingExerciseStudentParticipationRepository.save(participation);
                    }
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * Clones the repository from the source VCS and moves it to the local VC.
     * This is done by cloning the repository from the source VCS and then creating a bare repository in the local VC.
     *
     * @param exercise      the programming exercise
     * @param repositoryUri the repository URI
     * @param oldBranch     the old branch of the programming exercise
     * @return the URI of the repository in the local VC
     * @throws URISyntaxException if the repository URL is invalid
     */
    private String cloneRepositoryFromSourceVCSAndMoveToLocalVC(ProgrammingExercise exercise, String repositoryUri, String oldBranch) throws URISyntaxException {
        if (sourceVersionControlService.isEmpty()) {
            log.error("Failed to clone repository from source VCS: {}", repositoryUri);
            log.error("The source VCS service is not available");
            return null;
        }
        // repo is already migrated -> return
        if (repositoryUri.startsWith(localVCBaseUrl.toString())) {
            log.info("Repository {} is already in local VC", repositoryUri);
            return repositoryUri;
        }
        // check if the repo exists in the source VCS, if not -> return
        if (!sourceVersionControlService.get().repositoryUriIsValid(new VcsRepositoryUri(repositoryUri))) {
            log.info("Repository {} is not available in the source VCS, removing the reference in the database", repositoryUri);
            return null;
        }
        try {
            var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(repositoryUri);
            var projectKey = exercise.getProjectKey();

            log.info("Cloning repository {} from the source VCS and moving it to local VC", repositoryUri);
            copyRepoToLocalVC(projectKey, repositoryName, repositoryUri, oldBranch);
            log.info("Successfully cloned repository {} from the source VCS and moved it to local VC", repositoryUri);
            var uri = new LocalVCRepositoryUri(projectKey, repositoryName, localVCBaseUrl);
            return uri.toString();
        }
        catch (LocalVCInternalException e) {
            /*
             * By returning null here, we indicate that the repository does not exist anymore
             */
            log.error("Failed to clone repository from the source VCS: {}, the repository is unavailable.", repositoryUri, e);
            return null;
        }
    }

    /**
     * Clones the repository from the old origin and creates a bare repository in the local VC, just as a normal local VC repository would be created
     * by Artemis.
     *
     * @param projectKey     the project key
     * @param repositorySlug the repository slug
     * @param oldOrigin      the old origin of the repository
     */
    private void copyRepoToLocalVC(String projectKey, String repositorySlug, String oldOrigin, String branch) {
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositorySlug, localVCBaseUrl);

        Path repositoryPath = localVCRepositoryUri.getLocalRepositoryPath(localVCBasePath);

        try {
            Files.createDirectories(repositoryPath);
            log.debug("Created local git repository folder {}", repositoryPath);

            // Create a bare local repository with JGit.
            try (Git git = Git.cloneRepository().setBranch(branch).setDirectory(repositoryPath.toFile()).setBare(true).setURI(oldOrigin).call()) {
                if (!git.getRepository().getBranch().equals(defaultBranch)) {
                    // Rename the default branch to the configured default branch. Old exercises might have a different default branch.
                    git.branchRename().setNewName(defaultBranch).call();
                    log.debug("Renamed default branch of local git repository {} to {}", repositorySlug, defaultBranch);
                }
            }
            catch (Exception e) {
                log.error("Failed to clone repository from source VCS: {}", repositorySlug, e);
                throw new LocalVCInternalException("Error while cloning repository from source VCS.", e);
            }

            log.debug("Created local git repository {} in directory {}", repositorySlug, repositoryPath);
        }
        catch (IOException e) {
            log.error("Could not create local git repo {} at location {}", repositorySlug, repositoryPath, e);
            throw new LocalVCInternalException("Error while creating local git project.", e);
        }
    }
}
