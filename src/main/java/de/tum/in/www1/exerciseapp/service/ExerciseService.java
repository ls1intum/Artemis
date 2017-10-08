package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.domain.enumeration.ParticipationState;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public ExerciseService(ExerciseRepository exerciseRepository, UserService userService, ParticipationService participationService, Optional<ContinuousIntegrationService> continuousIntegrationService) {
        this.exerciseRepository = exerciseRepository;
        this.userService = userService;
        this.participationService = participationService;
        this.continuousIntegrationService = continuousIntegrationService;
    }

    /**
     * Save a exercise.
     *
     * @param exercise the entity to save
     * @return the persisted entity
     */
    public Exercise save(Exercise exercise) {
        log.debug("Request to save Exercise : {}", exercise);
        Exercise result = exerciseRepository.save(exercise);
        return result;
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
            e -> user.getGroups().contains(e.getCourse().getStudentGroupName())
                || user.getGroups().contains(e.getCourse().getTeachingAssistantGroupName())
                || user.getAuthorities().contains(adminAuthority)
                || e.getCourse().getTitle().equals("Archive") // TODO: Maybe we want to externalize the configuration of the "Archive" course name
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
        Exercise exercise = exerciseRepository.findOne(id);
        return exercise;
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
    public void delete(Long id, boolean deleteParticipations) {
        log.debug("Request to delete Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);

        if (deleteParticipations && Optional.ofNullable(exercise).isPresent()) {
            reset(exercise);
        }
        exerciseRepository.delete(id);
    }

    /**
     * Delete build plans (except BASE) of all exercise participations.
     *
     * @param id id of the exercise for which build plans in respective participations are deleted
     */
    @Transactional
    public void deleteBuildPlans(Long id) {
        log.debug("Request to delete build plans for Exercise : {}", id);
        Exercise exercise = findOneLoadParticipations(id);
        if (Optional.ofNullable(exercise).isPresent() && exercise instanceof ProgrammingExercise) {
            exercise.getParticipations().forEach(participation -> {
                if (participation.getBuildPlanId() != null) {
                    continuousIntegrationService.get().deleteBuildPlan(participation.getBuildPlanId());
                    participation.setInitializationState(ParticipationState.INACTIVE);
                    participation.setBuildPlanId(null);
                    participationService.save(participation);
                }
            });
        } else {
            log.debug("Exercise with id {} is not an instance of ProgrammingExercise. Ignoring the request to delete build plans", id);
        }
    }
}
