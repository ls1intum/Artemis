package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.UserStoryEffort;
import de.tum.cit.aet.artemis.programming.domain.UserStoryExercise;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortDTO;
import de.tum.cit.aet.artemis.programming.dto.UserStoryEffortStatusDTO;
import de.tum.cit.aet.artemis.programming.repository.UserStoryEffortRepository;

/**
 * Reads and writes the effort a participant reports for a {@link UserStoryExercise}.
 * <p>
 * The pair belongs to the participant's {@code StudentParticipation}, so a team shares one - see {@link UserStoryEffort}
 * for why it does not hang off a submission or the exercise.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class UserStoryEffortService {

    private static final String ENTITY_NAME = "userStoryEffort";

    /**
     * Upper bound on a single reported value, in hours. Not a policy limit - it exists so a slipped decimal point
     * ("1000" for "10.00") is rejected at entry rather than stored and later read as fact.
     */
    private static final double MAX_EFFORT_HOURS = 1000.0;

    private final UserStoryEffortRepository userStoryEffortRepository;

    private final ExerciseRepository exerciseRepository;

    private final ParticipationService participationService;

    private final ParticipationAuthorizationCheckService participationAuthorizationCheckService;

    private final ExerciseDateService exerciseDateService;

    private final StudentParticipationRepository studentParticipationRepository;

    public UserStoryEffortService(UserStoryEffortRepository userStoryEffortRepository, ExerciseRepository exerciseRepository, ParticipationService participationService,
            ParticipationAuthorizationCheckService participationAuthorizationCheckService, ExerciseDateService exerciseDateService,
            StudentParticipationRepository studentParticipationRepository) {
        this.userStoryEffortRepository = userStoryEffortRepository;
        this.exerciseRepository = exerciseRepository;
        this.participationService = participationService;
        this.participationAuthorizationCheckService = participationAuthorizationCheckService;
        this.exerciseDateService = exerciseDateService;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * The effort the user has reported for the story, or an empty pair when they have reported none yet.
     *
     * @param exerciseId the id of the user story exercise
     * @param user       the requesting user
     * @return the reported pair, never {@code null}
     */
    public UserStoryEffortDTO findForUser(long exerciseId, User user) {
        StudentParticipation participation = resolveOwnParticipationElseThrow(exerciseId, user);
        return UserStoryEffortDTO.of(userStoryEffortRepository.findByParticipationId(participation.getId()).orElse(null));
    }

    /**
     * Every user story in the course the user has started, with whatever effort they have reported. Serves the exercise
     * overview's "effort missing" marker in one request.
     *
     * @param courseId the course to report on
     * @param user     the requesting user
     * @return one entry per started story
     */
    public List<UserStoryEffortStatusDTO> findAllForCourse(long courseId, User user) {
        return userStoryEffortRepository.findAllStartedStoriesByCourseIdAndStudentLogin(courseId, user.getLogin());
    }

    /**
     * The effort reported on one participation, for a caller allowed to see it - the participant themself, or a tutor
     * assessing their work.
     *
     * @param participationId the participation to read
     * @return the reported pair, with unset values as {@code null}
     */
    public UserStoryEffortDTO findForParticipation(long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
        return UserStoryEffortDTO.of(userStoryEffortRepository.findByParticipationId(participationId).orElse(null));
    }

    /**
     * Records the effort the user reports for the story, replacing anything they reported before. Either value may be
     * left unset; only both being present counts as fully reported.
     *
     * @param exerciseId the id of the user story exercise
     * @param effortDTO  the reported pair
     * @param user       the reporting user
     * @return the stored pair
     */
    public UserStoryEffortDTO save(long exerciseId, UserStoryEffortDTO effortDTO, User user) {
        StudentParticipation participation = resolveOwnParticipationElseThrow(exerciseId, user);
        if (exerciseDateService.isAfterDueDate(participation)) {
            // Read through ExerciseDateService rather than the exercise's own due date: it honours the participation's
            // individualDueDate, so a participant granted an extension keeps reporting until their own deadline.
            throw new BadRequestAlertException("The effort can no longer be changed after the due date", ENTITY_NAME, "afterDueDate");
        }
        validateValue(effortDTO.estimatedEffort(), "estimatedEffort");
        validateValue(effortDTO.actualEffort(), "actualEffort");

        UserStoryEffort effort = userStoryEffortRepository.findByParticipationId(participation.getId()).orElseGet(() -> {
            UserStoryEffort created = new UserStoryEffort();
            created.setParticipation(participation);
            return created;
        });
        effort.setEstimatedEffort(effortDTO.estimatedEffort());
        effort.setActualEffort(effortDTO.actualEffort());
        return UserStoryEffortDTO.of(userStoryEffortRepository.save(effort));
    }

    /**
     * Resolves the participation the user reports on, rejecting anything that is not the user's own participation in a
     * user story exercise.
     *
     * @param exerciseId the id of the exercise, which must be a {@link UserStoryExercise}
     * @param user       the requesting user
     * @return the user's (or their team's) participation in that exercise
     */
    private StudentParticipation resolveOwnParticipationElseThrow(long exerciseId, User user) {
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        if (!(exercise instanceof UserStoryExercise)) {
            throw new BadRequestAlertException("Effort can only be reported for a user story exercise", ENTITY_NAME, "notUserStoryExercise");
        }
        // Team-aware: for a team exercise this resolves the team's single participation, so its members share one pair.
        StudentParticipation participation = participationService.findOneByExerciseAndStudentLoginAnyState(exercise, user.getLogin())
                .orElseThrow(() -> new BadRequestAlertException("The exercise has to be started before its effort can be reported", ENTITY_NAME, "participationMissing"));
        // Belt and braces: the lookup above is already scoped to this user, so this only ever fires if that changes.
        participationAuthorizationCheckService.checkCanAccessParticipationElseThrow(participation);
        return participation;
    }

    private void validateValue(@Nullable Double value, String fieldName) {
        if (value == null) {
            return;
        }
        if (value < 0) {
            throw new BadRequestAlertException("The reported effort must not be negative", ENTITY_NAME, fieldName + "Negative");
        }
        if (value > MAX_EFFORT_HOURS) {
            throw new BadRequestAlertException("The reported effort must not exceed " + MAX_EFFORT_HOURS + " hours", ENTITY_NAME, fieldName + "TooLarge");
        }
    }
}
