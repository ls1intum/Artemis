package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service Implementation for managing Participation.
 */
@Service
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    private final ParticipationRepository participationRepository;
    private final UserRepository userRepository;
    private Optional<GitService> gitService;
    private Optional<ContinuousIntegrationService> continuousIntegrationService;
    private Optional<VersionControlService> versionControlService;

    public ParticipationService(ParticipationRepository participationRepository, UserRepository userRepository, Optional<GitService> gitService, Optional<ContinuousIntegrationService> continuousIntegrationService, Optional<VersionControlService> versionControlService) {
        this.participationRepository = participationRepository;
        this.userRepository = userRepository;
        this.gitService = gitService;
        this.continuousIntegrationService = continuousIntegrationService;
        this.versionControlService = versionControlService;
    }

    /**
     * Save a participation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    @Transactional
    public Participation save(Participation participation) {
        log.debug("Request to save Participation : {}", participation);
        return participationRepository.save(participation);
    }

    public Participation init(Exercise exercise, String username) {

        // Check if participation already exists
        Participation participation = participationRepository.findOneByExerciseIdAndStudentLogin(exercise.getId(), username);
        if (!Optional.ofNullable(participation).isPresent()) {
            participation = new Participation();
            participation.setInitializationState(ParticipationState.UNINITIALIZED);
            participation.setExercise(exercise);

            Optional<User> user = userRepository.findOneByLogin(username);
            if (user.isPresent()) {
                participation.setStudent(user.get());
            }
            participation = save(participation);
        }


        participation = copyRepository(participation);
        participation = configureRepository(participation);
        participation = copyBuildPlan(participation);
        participation = configureBuildPlan(participation);
        participation.setInitializationState(ParticipationState.INITIALIZED);
        participation.setInitializationDate(ZonedDateTime.now());
        save(participation);

        return participation;
    }

    private Participation copyRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_COPIED)) {
            URL repositoryUrl = versionControlService.get().copyRepository(
                participation.getExercise().getBaseRepositoryUrlAsUrl(),
                participation.getStudent().getLogin());
            if (Optional.ofNullable(repositoryUrl).isPresent()) {
                participation.setRepositoryUrl(repositoryUrl.toString());
                participation.setInitializationState(ParticipationState.REPO_COPIED);
            }
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation configureRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_CONFIGURED)) {
            versionControlService.get().configureRepository(participation.getRepositoryUrlAsUrl(), participation.getStudent().getLogin());
            participation.setInitializationState(ParticipationState.REPO_CONFIGURED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation copyBuildPlan(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.BUILD_PLAN_COPIED)) {
            String buildPlanId = continuousIntegrationService.get().copyBuildPlan(
                participation.getExercise().getBaseBuildPlanId(),
                participation.getStudent().getLogin());
            participation.setBuildPlanId(buildPlanId);
            participation.setInitializationState(ParticipationState.BUILD_PLAN_COPIED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation configureBuildPlan(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.BUILD_PLAN_CONFIGURED)) {
            continuousIntegrationService.get().configureBuildPlan(
                participation.getBuildPlanId(),
                participation.getRepositoryUrlAsUrl(),
                participation.getStudent().getLogin());
            participation.setInitializationState(ParticipationState.BUILD_PLAN_CONFIGURED);
            return save(participation);
        } else {
            return participation;
        }
    }

    /**
     * Get all the participations.
     *
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<Participation> findAll() {
        log.debug("Request to get all Participations");
        return participationRepository.findAll();
    }

    /**
     * Get one participation by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOne(Long id) {
        log.debug("Request to get Participation : {}", id);
        return participationRepository.findOne(id);
    }

    /**
     * Get one participation by its student and exercise.
     *
     * @param exerciseId the project key of the exercise
     * @param username   the username of the student
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOneByExerciseIdAndStudentLogin(Long exerciseId, String username) {
        log.debug("Request to get Participation for User {} for Exercise with id: {}", username, exerciseId);
        Participation participation = participationRepository.findOneByExerciseIdAndStudentLoginAndInitializationState(exerciseId, username, ParticipationState.INITIALIZED);
        return participation;
    }

    @Transactional(readOnly = true)
    public Participation findOneByBuildPlanId(String buildPlanId) {
        log.debug("Request to get Participation for build plan id: {}", buildPlanId);
        Participation participation = participationRepository.findOneByBuildPlanId(buildPlanId);
        return participation;
    }

    /**
     * Delete the participation by id.
     *
     * @param id the id of the entity
     */
    @Transactional
    public void delete(Long id, boolean deleteBuildPlan, boolean deleteRepository) {
        log.debug("Request to delete Participation : {}", id);
        Participation participation = participationRepository.findOne(id);
        if (Optional.ofNullable(participation).isPresent()) {
            if (deleteBuildPlan && participation.getBuildPlanId() != null) {
                continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
            }
            if (deleteRepository && participation.getRepositoryUrl() != null) {
                versionControlService.get().deleteRepository(participation.getRepositoryUrlAsUrl());
            }

            // delete local repository cache
            try {
                gitService.get().deleteLocalRepository(participation);
            } catch (IOException e) {
                log.error("Error while deleting local repository", e);
            }

        }
        participationRepository.delete(id);
    }
}
