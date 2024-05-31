package de.tum.in.www1.artemis.web.rest;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.domain.quiz.QuizExercise;
import de.tum.in.www1.artemis.repository.QuizExerciseRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.in.www1.artemis.service.ParticipationService;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * REST controller for managing quiz participations.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class QuizParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(QuizParticipationResource.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ParticipationService participationService;

    private final UserRepository userRepository;

    public QuizParticipationResource(QuizExerciseRepository quizExerciseRepository, ParticipationService participationService, UserRepository userRepository) {
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationService = participationService;
        this.userRepository = userRepository;
    }

    /**
     * POST /quiz-exercises/{exerciseId}/start-participation : start the quiz exercise participation
     *
     * @param exerciseId the id of the quiz exercise
     * @return The created participation
     */
    @PostMapping("quiz-exercises/{exerciseId}/start-participation")
    @EnforceAtLeastStudentInExercise
    public ResponseEntity<StudentParticipation> startParticipation(@PathVariable Long exerciseId) {
        log.debug("REST request to start quiz exercise participation : {}", exerciseId);
        QuizExercise exercise = quizExerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise.getReleaseDate() != null && exercise.getReleaseDate().isAfter(ZonedDateTime.now())) {
            throw new AccessForbiddenException("Students cannot start an exercise before the release date");
        }
        if (!Boolean.TRUE.equals(exercise.isIsOpenForPractice()) && exercise.getDueDate() != null && exercise.getDueDate().isBefore(ZonedDateTime.now())) {
            throw new AccessForbiddenException("Students cannot start an exercise after the due date");
        }

        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(participationService.startExerciseWithInitializationDate(exercise, user, false, ZonedDateTime.now()));
    }
}
