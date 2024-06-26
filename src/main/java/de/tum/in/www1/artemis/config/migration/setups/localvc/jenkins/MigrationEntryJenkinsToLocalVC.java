package de.tum.in.www1.artemis.config.migration.setups.localvc.jenkins;

import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import de.tum.in.www1.artemis.config.migration.setups.localvc.LocalVCMigrationEntry;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.participation.ProgrammingExerciseStudentParticipation;
import de.tum.in.www1.artemis.domain.participation.SolutionProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.domain.participation.TemplateProgrammingExerciseParticipation;
import de.tum.in.www1.artemis.exception.JenkinsException;
import de.tum.in.www1.artemis.repository.AuxiliaryRepositoryRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseRepository;
import de.tum.in.www1.artemis.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.in.www1.artemis.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.repository.TemplateProgrammingExerciseParticipationRepository;
import de.tum.in.www1.artemis.service.UriService;
import de.tum.in.www1.artemis.service.connectors.jenkins.build_plan.JenkinsBuildPlanService;
import de.tum.in.www1.artemis.service.connectors.localvc.LocalVCRepositoryUri;

/**
 * Migration of Jenkins Build plans when the repositories will move to LocalVC.
 */
@Component
@Profile("jenkins")
public class MigrationEntryJenkinsToLocalVC extends LocalVCMigrationEntry {

    private final UriService uriService;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    public MigrationEntryJenkinsToLocalVC(ProgrammingExerciseRepository programmingExerciseRepository,
            SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository,
            TemplateProgrammingExerciseParticipationRepository templateProgrammingExerciseParticipationRepository,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, UriService uriService,
            JenkinsBuildPlanService jenkinsBuildPlanService, AuxiliaryRepositoryRepository auxiliaryRepositoryRepository) {
        super(programmingExerciseRepository, solutionProgrammingExerciseParticipationRepository, templateProgrammingExerciseParticipationRepository,
                programmingExerciseStudentParticipationRepository, auxiliaryRepositoryRepository);
        this.uriService = uriService;
        this.jenkinsBuildPlanService = jenkinsBuildPlanService;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Class<?> getSubclass() {
        return MigrationEntryJenkinsToLocalVC.class;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void migrateSolutions(List<SolutionProgrammingExerciseParticipation> solutionParticipations) {
        for (var participation : solutionParticipations) {
            try {
                if (isRepositoryUriNotNull(participation, "Repository URI is null for solution participation with id {}, cant migrate")) {
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBuildPlanId());
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getProgrammingExercise().getTestRepositoryUri(),
                            participation.getBuildPlanId());
                    for (var repo : getAuxiliaryRepositories(participation.getProgrammingExercise().getId())) {
                        if (repo.getRepositoryUri() == null) {
                            log.error("Repository URI is null for auxiliary repository with id {}, cant migrate", repo.getId());
                        }
                        else {
                            changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), repo.getRepositoryUri(), participation.getBuildPlanId());
                        }
                    }
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate solution participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void migrateTemplates(List<TemplateProgrammingExerciseParticipation> templateParticipations) {
        for (var participation : templateParticipations) {
            try {
                if (isRepositoryUriNotNull(participation, "Repository URI is null for template participation with id {}, cant migrate")) {
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBuildPlanId());
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate template participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void migrateStudents(List<ProgrammingExerciseStudentParticipation> participations) {
        for (var participation : participations) {
            try {
                if (isRepositoryUriNotNull(participation, "Repository URI is null for student participation with id {}, cant migrate")) {
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getRepositoryUri(), participation.getBuildPlanId());
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    private void changeRepositoryUriFromSourceVCSToLocalVC(ProgrammingExercise exercise, String sourceVCSRepositoryUri, String buildPlanKey) {
        // repo is already migrated -> return
        if (sourceVCSRepositoryUri.startsWith(localVCBaseUrl.toString())) {
            log.info("Repository {} is already in local VC", sourceVCSRepositoryUri);
            return;
        }
        try {
            var repositoryName = uriService.getRepositorySlugFromRepositoryUriString(sourceVCSRepositoryUri);
            var projectKey = exercise.getProjectKey();
            var localVCRepositoryUri = new LocalVCRepositoryUri(projectKey, repositoryName, localVCBaseUrl);

            jenkinsBuildPlanService.updateBuildPlanRepositories(projectKey, buildPlanKey, localVCRepositoryUri.toString(), sourceVCSRepositoryUri);
        }
        catch (JenkinsException e) {
            log.error("Failed to adjust repository uri for the source VCS uri: {}.", sourceVCSRepositoryUri, e);
        }
    }

}
