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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service Implementation for managing Participation.
 */
@Service
@Transactional
public class ParticipationService {

    private final Logger log = LoggerFactory.getLogger(ParticipationService.class);

    @Inject
    private ParticipationRepository participationRepository;

    @Inject
    private UserRepository userRepository;

    @Inject
    private BambooService bambooService;

    @Inject
    private BitbucketService bitbucketService;

    @Inject
    private GitService gitService;

    /**
     * Save a participation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
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
            participation = forkRepository(participation);
            participation = giveWritePermission(participation);
            participation = cloneBuildPlan(participation);
            participation = updateBuildPlanRepository(participation);
            participation = enableBuildPlan(participation);
            participation = doEmptyCommit(participation);
        } catch (BitbucketException e) {
            log.error("Bitbucket error", e);
        } catch (BambooException e) {
            log.error("Bamboo error", e);
        } catch (GitException e) {
            log.error("Git error", e);
        }

        return participation;
    }

    private Participation forkRepository(Participation participation) throws BitbucketException {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_FORKED)) {
            Map forkResult = bitbucketService.forkRepository(
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

    private Participation giveWritePermission(Participation participation) throws BitbucketException {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.REPO_PERMISSIONS_SET)) {
            bitbucketService.giveWritePermission(
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

    private Participation cloneBuildPlan(Participation participation) throws BambooException {
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

    private Participation updateBuildPlanRepository(Participation participation) throws BambooException {
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

    private Participation enableBuildPlan(Participation participation) throws BambooException {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.PLAN_ENABLED)) {
            bambooService.enablePlan(participation.getExercise().getBaseProjectKey(), participation.getStudent().getLogin());
            participation.setInitializationState(ParticipationState.PLAN_ENABLED);
            return save(participation);
        } else {
            return participation;
        }
    }

    private Participation doEmptyCommit(Participation participation) throws GitException {
        if (!participation.getInitializationState().hasCompletedState(ParticipationState.INITIALIZED)) {
            gitService.doEmptyCommit(participation.getExercise().getBaseProjectKey(), participation.getCloneUrl());
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
        Participation participation = participationRepository.findOneByExerciseIdAndStudentLogin(exerciseId, username);
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
    public void delete(Long id, boolean deleteBuildPlan, boolean deleteRepository) {
        log.debug("Request to delete Participation : {}", id);
        Participation participation = participationRepository.findOne(id);
        if (Optional.ofNullable(participation).isPresent()) {
            if (deleteBuildPlan) {
                bambooService.deletePlan(participation.getExercise().getBaseProjectKey(), participation.getStudent().getLogin());
            }
            if (deleteRepository) {
                bitbucketService.deleteRepository(participation.getExercise().getBaseProjectKey(), participation.getRepositorySlug());
            }
        }
        participationRepository.delete(id);
    }

}
