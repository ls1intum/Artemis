package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.ExampleSubmission;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.TutorParticipation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service Implementation for managing TutorParticipation.
 */
@Service
@Transactional
public class TutorParticipationService {

    private final Logger log = LoggerFactory.getLogger(TutorParticipationService.class);
    private final ExampleSubmissionRepository exampleSubmissionRepository;

    @Value("${server.url}")
    private String ARTEMIS_BASE_URL;

    private final TutorParticipationRepository tutorParticipationRepository;

    public TutorParticipationService(TutorParticipationRepository tutorParticipationRepository,
                                     ExampleSubmissionRepository exampleSubmissionRepository) {
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
    }

    /**
     * Save a tutorParticipations.
     *
     * @param tutorParticipation the entity to save
     * @return the persisted entity
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public TutorParticipation save(TutorParticipation tutorParticipation) {
        log.debug("Request to save TutorParticipation : {}", tutorParticipation);
        return tutorParticipationRepository.saveAndFlush(tutorParticipation);
    }

    /**
     * Get all the tutorParticipations.
     *
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public List<TutorParticipation> findAll() {
        log.debug("Request to get all TutorParticipations");
        return tutorParticipationRepository.findAll();
    }

    /**
     * Get all the tutorParticipations.
     *
     * @param pageable the pagination information
     * @return the list of entities
     */
    @Transactional(readOnly = true)
    public Page<TutorParticipation> findAll(Pageable pageable) {
        log.debug("Request to get all TutorParticipations");
        return tutorParticipationRepository.findAll(pageable);
    }

    /**
     * Get one tutorParticipations by id.
     *
     * @param id the id of the entity
     * @return the entity
     */
    @Transactional(readOnly = true)
    public TutorParticipation findOne(Long id) {
        log.debug("Request to get TutorParticipation : {}", id);

        Optional<TutorParticipation> tutorParticipation = tutorParticipationRepository.findById(id);

        return tutorParticipation.orElse(null);
    }

    @Transactional
    public TutorParticipation findByExerciseAndTutor(Exercise exercise, User tutor) {
        TutorParticipation participation = tutorParticipationRepository.findByAssessedExerciseAndTutor(exercise, tutor);

        if (participation == null) {
            participation = new TutorParticipation();
            participation.setStatus(TutorParticipationStatus.NOT_PARTICIPATED);
        } else {
            participation.getTrainedExampleSubmissions().size();    // Load trained example submissions
        }

        return participation;
    }

    public TutorParticipation createNewParticipation(Exercise exercise, User tutor) {
        TutorParticipation tutorParticipation = new TutorParticipation();

        List<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllByExerciseId(exercise.getId());

        // The tutor starts a participation when she reads the grading instruction. If no grading instructions are
        // available, then she starts her participation clicking on "Start participation".
        // Usually, the first step is `REVIEWED_INSTRUCTIONS`: after that, she has to train reviewing some example
        // submissions, and assessing others.
        // If no example submissions are available, because the instructor hasn't created any, then she goes directly
        // to the next step, that allows her to assess students' participations
        if (exampleSubmissions.size() == 0) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        } else {
            tutorParticipation.setStatus(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        }

        tutorParticipation.setTutor(tutor);
        tutorParticipation.setAssessedExercise(exercise);

        return save(tutorParticipation);
    }
}
