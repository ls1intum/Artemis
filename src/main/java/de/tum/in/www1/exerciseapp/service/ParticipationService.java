package de.tum.in.www1.exerciseapp.service;

import de.tum.in.www1.exerciseapp.domain.Participation;
import de.tum.in.www1.exerciseapp.domain.User;
import de.tum.in.www1.exerciseapp.repository.ParticipationRepository;
import de.tum.in.www1.exerciseapp.repository.search.ParticipationSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import javax.inject.Inject;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.elasticsearch.index.query.QueryBuilders.*;

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
    private ParticipationSearchRepository participationSearchRepository;

    /**
     * Save a participation.
     *
     * @param participation the entity to save
     * @return the persisted entity
     */
    public Participation save(Participation participation) {
        log.debug("Request to save Participation : {}", participation);
        Participation result = participationRepository.save(participation);
        participationSearchRepository.save(result);
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
     *  Get one participation by its student and exercise.
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
     *  Delete the  participation by id.
     *
     *  @param id the id of the entity
     */
    public void delete(Long id) {
        log.debug("Request to delete Participation : {}", id);
        participationRepository.delete(id);
        participationSearchRepository.delete(id);
    }

    /**
     * Search for the participation corresponding to the query.
     *
     *  @param query the query of the search
     *  @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<Participation> search(String query) {
        log.debug("Request to search Participations for query {}", query);
        return StreamSupport
            .stream(participationSearchRepository.search(queryStringQuery(query)).spliterator(), false)
            .collect(Collectors.toList());
    }
}
