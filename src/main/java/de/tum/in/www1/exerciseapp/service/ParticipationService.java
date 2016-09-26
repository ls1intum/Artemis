package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Exercise;
import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.exception.BambooException;
import de.tum.in.www1.exerciseapp.exception.BitbucketException;
import de.tum.in.www1.exerciseapp.exception.GitException;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.UserRepository;
import de.tum.in.www1.exerciseapp.web.rest.errors.CustomParameterizedException;
import de.tum.in.www1.exerciseapp.web.rest.errors.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service Implementation for managing Participation.
 */
@Service
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    @Inject
    private ParticipationRepository participationRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private BambooService bambooService;

//    @Inject
//    private BitbucketService bitbucketService;

    @Inject
    private VersionControlService versionControlService;

    @Inject
    private GitService gitService;

    /**
     * Save a participation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    @Transactional
    public Participation save(Participation participation) {
        log.debug("Request to save Participation : {}", participation);
        Participation result = participationRepository.save(participation);
        return result;
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

        try {
            participation = copyRepository(participation);
            participation = configureRepository(participation);
            participation = cloneBuildPlan(participation);
            participation = updateBuildPlanRepository(participation);
            participation = enableBuildPlan(participation);
            participation = doEmptyCommit(participation);
        } catch (BitbucketException | BambooException | GitException e) {
            log.error("Error while initializing participation");
            throw new CustomParameterizedException(ErrorConstants.ERR_INTERNAL_SERVER_ERROR);
        }
        return participation;
    }

    private Participation copyRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_FORKED)) {
            Map forkResult = versionControlService.copyRepository(
                participation.getExercise().getBaseProjectKey(),
                participation.getExercise().getBaseRepositorySlug(),
                participation.getStudent().getLogin());
            if (Optional.ofNullable(forkResult).isPresent()) {
                participation.setCloneUrl((String) forkResult.get("cloneUrl"));
                participation.setRepositorySlug((String) forkResult.get("slug"));
                participation.setInitializationState(ParticipationState.REPO_FORKED);
            }
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation configureRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_PERMISSIONS_SET)) {
            versionControlService.configureRepository(
                participation.getExercise().getBaseProjectKey(),
                participation.getRepositorySlug(),
                participation.getStudent().getLogin()
            );
            participation.setInitializationState(ParticipationState.REPO_PERMISSIONS_SET);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation cloneBuildPlan(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.PLAN_CLONED)) {
            bambooService.clonePlan(
                participation.getExercise().getBaseProjectKey(),
                participation.getExercise().getBaseBuildPlanSlug(),
                participation.getStudent().getLogin()
            );
            // TODO: Add property for build plan identifier on participation
            participation.setInitializationState(ParticipationState.PLAN_CLONED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation updateBuildPlanRepository(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.PLAN_REPO_UPDATED)) {
            bambooService.updatePlanRepository(
                participation.getExercise().getBaseProjectKey(),
                participation.getStudent().getLogin(),
                participation.getExercise().getBaseRepositorySlug(),
                participation.getExercise().getBaseProjectKey(),
                participation.getRepositorySlug()
            );
            participation.setInitializationState(ParticipationState.PLAN_REPO_UPDATED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation enableBuildPlan(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.PLAN_ENABLED)) {
            bambooService.enablePlan(participation.getExercise().getBaseProjectKey(), participation.getStudent().getLogin());
            participation.setInitializationState(ParticipationState.PLAN_ENABLED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation doEmptyCommit(Participation participation) {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.INITIALIZED)) {
            gitService.doEmptyCommit(participation.getExercise().getBaseProjectKey(), participation.getCloneUrl());
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setInitializationState(ParticipationState.INITIALIZED);
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
        List<Participation> result = participationRepository.findAll();
        return result;
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
        Participation participation = participationRepository.findOne(id);
        return participation;
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

    /**
     * Get one participation by its student and exercise.
     *
     * @param projectKey the project key of the exercise
     * @param username   the username of the student
     * @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOneByExerciseProjectKeyAndStudentLogin(String projectKey, String username) {
        log.debug("Request to get Participation for User {} for Exercise with project key: {}", username, projectKey);
        Participation participation = participationRepository.findOneByExerciseBaseProjectKeyAndStudentLogin(projectKey, username);
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
            if (deleteBuildPlan) {
                bambooService.deletePlan(participation.getExercise().getBaseProjectKey(), participation.getStudent().getLogin());
            }
            if (deleteRepository) {
                versionControlService.deleteRepository(participation.getExercise().getBaseProjectKey(), participation.getRepositorySlug());
            }
        }
        participationRepository.delete(id);
    }

}
