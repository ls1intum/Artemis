package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
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

    /**
     * Given an exercise and a tutor, it retrieves the participation of the tutor for that exercise. If there isn't
     * any participation in the database, it returns a participation with status NOT_PARTICIPATED
     *
     * @param exercise the exercise we want to retrieve the tutor participation
     * @param tutor    the tutor of who we want to retrieve the participation
     * @return a tutor participation object for the pair (exercise, tutor) passed as argument
     */
    @Transactional(readOnly = true)
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

    /**
     * Given an exercise and a tutor it creates the participation of the tutor to that exercise
     *
     * The tutor starts a participation when she reads the grading instruction. If no grading instructions are
     * available, then she starts her participation clicking on "Start participation".
     * Usually, the first step is `REVIEWED_INSTRUCTIONS`: after that, she has to train reviewing some example
     * submissions, and assessing others.
     * If no example submissions are available, because the instructor hasn't created any, then she goes directly
     * to the next step, that allows her to assess students' participations
     *
     * @param exercise the exercise the tutor is going to participate to
     * @param tutor    the tutor who is going to participate to the exercise
     * @return a TutorParticipation for the exercise
     */
    public TutorParticipation createNewParticipation(Exercise exercise, User tutor) {
        TutorParticipation tutorParticipation = new TutorParticipation();

        List<ExampleSubmission> exampleSubmissions = exampleSubmissionRepository.findAllByExerciseId(exercise.getId());

        if (exampleSubmissions.size() == 0) {
            tutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        } else {
            tutorParticipation.setStatus(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        }

        tutorParticipation.setTutor(tutor);
        tutorParticipation.setAssessedExercise(exercise);

        return save(tutorParticipation);
    }

    /**
     * Given a course and a tutor, it finds all the participation of that tutor in the course, with related assessed
     * exercise and trained example submissions
     * @param course - the course we are interested in
     * @param user - the tutor who is querying the service
     * @return a list of tutor participation for the course
     */
    @Transactional(readOnly = true)
    public List<TutorParticipation> findAllByCourseAndTutor(Course course, User user) {
        List<TutorParticipation> tutorParticipations = tutorParticipationRepository.findAllByAssessedExercise_Course_IdAndTutor_Id(course.getId(), user.getId());

        for (TutorParticipation tutorParticipation : tutorParticipations) {
            tutorParticipation.setAssessedExercise(tutorParticipation.getAssessedExercise());
            tutorParticipation.getTrainedExampleSubmissions().size();
        }

        return tutorParticipations;
    }
}
