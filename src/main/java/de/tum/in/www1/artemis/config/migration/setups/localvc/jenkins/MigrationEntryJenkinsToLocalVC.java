package de.tum.in.www1.artemis.config.migration.setups.localvc.jenkins;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
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

/**
 * Migration of Jenkins Build plans when the repositories will move to LocalVC.
 */
@Component
@Profile("jenkins")
public class MigrationEntryJenkinsToLocalVC extends LocalVCMigrationEntry {

    private final UriService uriService;

    private final JenkinsBuildPlanService jenkinsBuildPlanService;

    /**
     * Base url to the local VC repository is the server urls plus /git/.
     */
    private String localVCRepositoriesBaseUrl;

    @Value("${artemis.version-control.url}")
    private String sourceVCSBaseUrl;

    /**
     * URL to the source VCS with a trailing slash.
     * This avoids that a base URL like "http://example.com" also finds "http://example.com:8080".
     */
    private String sourceVCSRepositoriesBaseUrl;;

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

    @PostConstruct
    public void initialize() {
        localVCRepositoriesBaseUrl = localVCBaseUrl + "/git/";
        sourceVCSRepositoriesBaseUrl = sourceVCSBaseUrl.endsWith("/") ? sourceVCSBaseUrl : sourceVCSBaseUrl + "/";
        ;
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
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getBuildPlanId());
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
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getBuildPlanId());
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
                    changeRepositoryUriFromSourceVCSToLocalVC(participation.getProgrammingExercise(), participation.getBuildPlanId());
                }
            }
            catch (Exception e) {
                log.error("Failed to migrate student participation with id {}", participation.getId(), e);
                errorList.add(participation);
            }
        }
    }

    private void changeRepositoryUriFromSourceVCSToLocalVC(ProgrammingExercise exercise, String buildPlanKey) {
        var projectKey = exercise.getProjectKey();
        try {
            jenkinsBuildPlanService.updateBuildPlanSearchAndReplace(projectKey, buildPlanKey, sourceVCSRepositoriesBaseUrl, localVCRepositoriesBaseUrl);
        }
        catch (JenkinsException e) {
            log.error("Failed to adjust repository uris for build plan {} in project {}.", buildPlanKey, projectKey, e);
        }
    }

}
