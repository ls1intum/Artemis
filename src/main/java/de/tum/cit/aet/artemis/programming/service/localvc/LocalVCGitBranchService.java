package de.tum.cit.aet.artemis.programming.service.localvc;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.io.File;
import java.io.IOException;

import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.localvc.LocalVCInternalException;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.TemplateProgrammingExerciseParticipationRepository;

@Profile(PROFILE_CORE)
@Service
public class LocalVCGitBranchService {

    @Value("${artemis.version-control.local-vcs-repo-path:#{null}}")
    private String localVCBasePath;

    private final ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository;

    public LocalVCGitBranchService(ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository, StudentParticipationRepository studentParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository) {
        this.programmingExerciseBuildConfigRepository = programmingExerciseBuildConfigRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.templateProgrammingExerciseParticipationRepository = templateProgrammingExerciseParticipationRepository;
    }

    /**
     * Get the default branch of the repository
     *
     * @param repositoryUri The repository uri to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     */
    public String getDefaultBranchOfRepository(VcsRepositoryUri repositoryUri) {
        LocalVCRepositoryUri localVCRepositoryUri = new LocalVCRepositoryUri(repositoryUri.toString());
        return getDefaultBranch(localVCRepositoryUri);
    }

    /**
     * Get the default branch of the repository given the Local VC repository URI
     *
     * @param localVCRepositoryUri The Local VC repository URI uri to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     * @throws LocalVCInternalException if the default branch cannot be determined
     */
    public String getDefaultBranch(LocalVCRepositoryUri localVCRepositoryUri) {
        String localRepositoryPath = localVCRepositoryUri.getLocalRepositoryPath(localVCBasePath).toString();
        return getDefaultBranch(localRepositoryPath);
    }

    /**
     * Get the default branch of the repository given the repository path.
     * We assume that the path links to a bare repository. This implementation is faster than using ls-remote (Git.lsRemoteRepository().setRemote(localRepositoryPath).callAsMap())
     *
     * @param repositoryPath The path of the repository to get the default branch for.
     * @return the name of the default branch, e.g. 'main'
     * @throws LocalVCInternalException if the default branch cannot be determined
     */
    @Deprecated(forRemoval = true, since = "8.0")
    public static String getDefaultBranch(String repositoryPath) {
        try (Repository repository = new RepositoryBuilder().setGitDir(new File(repositoryPath)).build()) {

            String defaultBranchName = repository.getBranch();
            if (defaultBranchName == null) {
                throw new LocalVCInternalException("HEAD is not a symbolic reference in repository " + repository.getDirectory().toPath());
            }
            return defaultBranchName;
        }
        catch (IOException e) {
            throw new LocalVCInternalException("Cannot read default branch of repository " + repositoryPath + ". Failed to open repository.", e);
        }
    }

    /**
     * Get the default branch used in the participation or retrieves it from the VCS if not present in the database
     * NOTE: the branch is stored in the participation or the build config of the exercise and should be accessed directly
     * This method was a workaround for the times when the branch was not stored in the database
     *
     * @param participation The participation to get the default branch from
     * @return The default branch used by this participation
     */
    @Deprecated(forRemoval = true, since = "8.0")
    public String getOrRetrieveBranchOfParticipation(ProgrammingExerciseParticipation participation) {
        if (participation instanceof ProgrammingExerciseStudentParticipation studentParticipation) {
            if (studentParticipation.getBranch() == null) {
                String branch = getDefaultBranchOfRepository(participation.getVcsRepositoryUri());
                studentParticipation.setBranch(branch);
                studentParticipationRepository.save(studentParticipation);
            }

            return studentParticipation.getBranch();
        }
        else {
            return getOrRetrieveBranchOfExercise(participation.getProgrammingExercise());
        }
    }

    /**
     * Get the default branch used in the programmingExercise or retrieves it from the VCS if not present in the database
     * NOTE: the branch is stored in the build config of the exercise and should be accessed directly
     * This method was a workaround for the times when the branch was not stored in the database
     *
     * @param programmingExercise The participation to get the default branch from
     * @return The default branch used by this programmingExercise
     */
    @Deprecated(forRemoval = true, since = "8.0")
    public String getOrRetrieveBranchOfExercise(ProgrammingExercise programmingExercise) {
        programmingExerciseBuildConfigRepository.loadAndSetBuildConfig(programmingExercise);

        if (programmingExercise.getBuildConfig().getBranch() == null) {
            if (!Hibernate.isInitialized(programmingExercise.getTemplateParticipation())) {
                programmingExercise.setTemplateParticipation(templateProgrammingExerciseParticipationRepository.findByProgrammingExerciseIdElseThrow(programmingExercise.getId()));
            }
            String branch = getDefaultBranchOfRepository(programmingExercise.getVcsTemplateRepositoryUri());
            programmingExercise.getBuildConfig().setBranch(branch);
            programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        }

        return programmingExercise.getBuildConfig().getBranch();
    }
}
