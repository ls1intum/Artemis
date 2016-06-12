package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
import java.util.List;
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
    private BambooService bambooService;

    @Inject
    private BitbucketService bitbucketService;

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

    /**
     *  Get all the participations.
     *
     *  @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<Participation> findAll() {
        log.debug("Request to get all Participations");
        List<Participation> result = participationRepository.findAll();
        return result;
    }

    /**
     *  Get one participation by id.
     *
     *  @param id the id of the entity
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOne(Long id) {
        log.debug("Request to get Participation : {}", id);
        Participation participation = participationRepository.findOne(id);
        return participation;
    }

    /**
     *  Get one participation by the current user and exercise.
     *
     *  @param exerciseId the id of the exercise
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOneByExerciseIdAndCurrentUser(Long exerciseId) {
        log.debug("Request to get Participation for User for Exercise: {}", exerciseId);
        Participation participation = participationRepository.findOneByExerciseIdAndStudentIsCurrentUser(exerciseId);
        return participation;
    }

    /**
     *  Get one participation by its student and exercise.
     *
     *  @param projectKey the project key of the exercise
     *  @param username the username of the student
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Participation findOneByExerciseProjectKeyAndStudentLogin(String projectKey, String username) {
        log.debug("Request to get Participation for User {} for Exercise with project key: {}", username, projectKey);
        Participation participation = participationRepository.findOneByExerciseBaseProjectKeyAndStudentLogin(projectKey, username);
        return participation;
    }

    /**
     *  Delete the participation by id.
     *
     *  @param id the id of the entity
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
