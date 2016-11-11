package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.*;
import de.tum.in.www1.exerciseapp.repository.ExerciseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.inject.Inject;
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

    @Inject
    private ExerciseRepository exerciseRepository;

    @Inject
    private UserService userService;

    @Inject
    private ParticipationService participationService;

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
     *  Get all the exercises.
     *
     *  @param pageable the pagination information
     *  @return the list of entities
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
     *  Get one exercise by id.
     *
     *  @param id the id of the entity
     *  @return the entity
     */
    @Transactional(readOnly = true)
    public Exercise findOne(Long id) {
        log.debug("Request to get Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);
        return exercise;
    }

    /**
     *  Delete the  exercise by id.
     *
     *  @param id the id of the entity
     */
    public void delete(Long id, boolean deleteParticipations) {
        log.debug("Request to delete Exercise : {}", id);
        Exercise exercise = exerciseRepository.findOne(id);

        if(deleteParticipations && Optional.ofNullable(exercise).isPresent()) {
            // delete all participations for this exercise
            for(Participation participation : exercise.getParticipations()) {
                participationService.delete(participation.getId(), true , true);
            }

        }
        exerciseRepository.delete(id);
    }

}
