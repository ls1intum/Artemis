package de.tum.in.www1.artemis.service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.TutorParticipationStatus;
import de.tum.in.www1.artemis.repository.ExampleSubmissionRepository;
import de.tum.in.www1.artemis.repository.TutorParticipationRepository;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;
import javassist.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.security.InvalidParameterException;
import java.util.ArrayList;
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
    private final UserService userService;
    private final ExampleSubmissionService exampleSubmissionService;

    private static final float scoreRangePercentage = 10;

    public TutorParticipationService(TutorParticipationRepository tutorParticipationRepository,
                                     ExampleSubmissionRepository exampleSubmissionRepository,
                                     UserService userService,
                                     ExampleSubmissionService exampleSubmissionService) {
        this.tutorParticipationRepository = tutorParticipationRepository;
        this.exampleSubmissionRepository = exampleSubmissionRepository;
        this.userService = userService;
        this.exampleSubmissionService = exampleSubmissionService;
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

    /**
     * Given an exercise, it adds to the tutor participation of that exercise the example submission passed as
     * argument, if it is valid (e.g: if it is an example submission used for review, we check the result is close
     * enough to the one of the instructor)
     *
     * TODO @rpadovani: after https://github.com/ls1intum/ArTEMiS/pull/160 has been merged, move to that kind of
     *      exceptions
     *
     * @param exercise - the exercise we are referring to
     * @param exampleSubmission - the example submission to add
     * @return the updated tutor participation
     */
    public TutorParticipation addExampleSubmission(Exercise exercise, ExampleSubmission exampleSubmission) throws NotFoundException, IllegalStateException, InvalidParameterException {
        User user = userService.getUserWithGroupsAndAuthorities();

        TutorParticipation existingTutorParticipation = this.findByExerciseAndTutor(exercise, user);
        // Do not trust the user input
        Optional<ExampleSubmission> exampleSubmissionFromDatabase = exampleSubmissionService.get(exampleSubmission.getId());

        if (existingTutorParticipation == null || !exampleSubmissionFromDatabase.isPresent()) {
            throw new NotFoundException("There isn't such example submission, or there isn't any tutor participation for this exercise");
        }

        ExampleSubmission originalExampleSubmission = exampleSubmissionFromDatabase.get();

        if (existingTutorParticipation.getStatus() != TutorParticipationStatus.REVIEWED_INSTRUCTIONS) {
            throw new IllegalStateException();
        }

        // Check if it is a tutorial or not
        boolean isTutorial = originalExampleSubmission.isUsedForTutorial() == Boolean.TRUE;

        // If it is not a tutorial we check the assessment
        if (!isTutorial) {
            // Retrieve the example feedback created by the instructor
            List<Feedback> existingFeedback = this.exampleSubmissionService.getFeedbackForExampleSubmission(exampleSubmission.getId());

            // Check if the result is the same
            // TODO: at the moment we check only the score +/10%, maybe we want to do something smarter?
            float instructorScore = calculateTotalScore(existingFeedback);
            float lowerInstructorScore = instructorScore - instructorScore / scoreRangePercentage;
            float higherInstructorScore = instructorScore + instructorScore / scoreRangePercentage;

            float tutorScore = calculateTotalScore(exampleSubmission.getSubmission().getResult().getFeedbacks());

            if (lowerInstructorScore > tutorScore) {
                throw new InvalidParameterException("tooLow");
            }

            if (tutorScore > higherInstructorScore) {
                throw new InvalidParameterException("tooHigh");
            }
        }

        List<ExampleSubmission> alreadyAssessedSubmissions = new ArrayList<>(existingTutorParticipation.getTrainedExampleSubmissions());

        // If the example submission was already assessed, we do not assess it again
        if (alreadyAssessedSubmissions.contains(exampleSubmission)) {
            throw new InvalidParameterException("alreadyAssessed");
        }

        int numberOfExampleSubmissions = this.exampleSubmissionRepository.findAllByExerciseId(exercise.getId()).size();
        int numberOfAlreadyAssessedSubmissions = alreadyAssessedSubmissions.size() + 1;  // +1 because we haven't added yet the one we just did

        /*
          When the tutor has read and assessed all the exercises, the tutor status goes to the next step.
         */
        if (numberOfAlreadyAssessedSubmissions >= numberOfExampleSubmissions) {
            existingTutorParticipation.setStatus(TutorParticipationStatus.TRAINED);
        }

        existingTutorParticipation = existingTutorParticipation.addTrainedExampleSubmissions(originalExampleSubmission);
        exampleSubmissionService.save(originalExampleSubmission);
        this.save(existingTutorParticipation);

        return existingTutorParticipation;
    }

    private float calculateTotalScore(List<Feedback> feedbacks) {
        return (float) feedbacks.stream().mapToDouble(Feedback::getCredits).sum();
    }
}
