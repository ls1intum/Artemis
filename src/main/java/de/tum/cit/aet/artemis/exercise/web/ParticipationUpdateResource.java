package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;

/**
 * REST controller for updating participations.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ParticipationUpdateResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationUpdateResource.class);

    private static final String ENTITY_NAME = "participation";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final ParticipationService participationService;

    private final AuthorizationCheckService authCheckService;

    private final ExerciseDateService exerciseDateService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final GradingScaleService gradingScaleService;

    private final ExerciseRepository exerciseRepository;

    private final UserRepository userRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ParticipationUpdateResource(ParticipationService participationService, ExerciseRepository exerciseRepository, AuthorizationCheckService authCheckService,
            UserRepository userRepository, StudentParticipationRepository studentParticipationRepository, ExerciseDateService exerciseDateService,
            InstanceMessageSendService instanceMessageSendService, GradingScaleService gradingScaleService) {
        this.participationService = participationService;
        this.exerciseRepository = exerciseRepository;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.exerciseDateService = exerciseDateService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.gradingScaleService = gradingScaleService;
    }

    /**
     * PUT /participations : Updates an existing participation.
     *
     * @param exerciseId    the id of the exercise, the participation belongs to
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation, or with status 400 (Bad Request) if the participation is not valid, or with status
     *         500 (Internal Server Error) if the participation couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
    public ResponseEntity<Participation> updateParticipation(@PathVariable long exerciseId, @RequestBody StudentParticipation participation) {
        log.debug("REST request to update Participation : {}", participation);
        if (participation.getId() == null) {
            throw new BadRequestAlertException("The participation object needs to have an id to be changed", ENTITY_NAME, "idmissing");
        }
        if (participation.getExercise() == null || participation.getExercise().getId() == null) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }
        if (participation.getExercise().getId() != exerciseId) {
            throw new ConflictException("The exercise of the participation does not match the exercise id in the URL", ENTITY_NAME, "noidmatch");
        }
        var originalParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, originalParticipation.getExercise(), null);

        Course course = findCourseFromParticipation(participation);
        if (participation.getPresentationScore() != null && participation.getExercise().getPresentationScoreEnabled() != null
                && participation.getExercise().getPresentationScoreEnabled()) {
            Optional<GradingScale> gradingScale = gradingScaleService.findGradingScaleByCourseId(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

            // Presentation Score is only valid for non practice participations
            if (participation.isPracticeMode()) {
                throw new BadRequestAlertException("Presentation score is not allowed for practice participations", ENTITY_NAME, "presentationScoreInvalid");
            }

            // Validity of presentationScore for basic presentations
            if (course.getPresentationScore() != null && course.getPresentationScore() > 0) {
                if (participation.getPresentationScore() >= 1.) {
                    participation.setPresentationScore(1.);
                }
                else {
                    participation.setPresentationScore(null);
                }
            }
            // Validity of presentationScore for graded presentations
            if (gradingScale.isPresent() && gradingScale.get().getPresentationsNumber() != null) {
                if ((participation.getPresentationScore() > 100. || participation.getPresentationScore() < 0.)) {
                    throw new BadRequestAlertException("The presentation grade must be between 0 and 100", ENTITY_NAME, "presentationGradeInvalid");
                }

                long presentationCountForParticipant = studentParticipationRepository.countPresentationScoresForParticipant(course.getId(), participation.getParticipant().getId(),
                        participation.getId());
                if (presentationCountForParticipant >= gradingScale.get().getPresentationsNumber()) {
                    throw new BadRequestAlertException("Participant already gave the maximum number of presentations", ENTITY_NAME,
                            "invalid.presentations.maxNumberOfPresentationsExceeded",
                            Map.of("name", participation.getParticipant().getName(), "presentationsNumber", gradingScale.get().getPresentationsNumber()));
                }
            }
        }
        // Validity of presentationScore for no presentations
        else {
            participation.setPresentationScore(null);
        }

        StudentParticipation currentParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        if (currentParticipation.getPresentationScore() != null && participation.getPresentationScore() == null || course.getPresentationScore() != null
                && currentParticipation.getPresentationScore() != null && currentParticipation.getPresentationScore() > participation.getPresentationScore()) {
            log.info("{} removed the presentation score of {} for exercise with participationId {}", user.getLogin(), originalParticipation.getParticipantIdentifier(),
                    originalParticipation.getExercise().getId());
        }

        Participation updatedParticipation = studentParticipationRepository.saveAndFlush(participation);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, participation.getParticipant().getName()))
                .body(updatedParticipation);
    }

    /**
     * PUT /participations/update-individual-due-date : Updates the individual due dates for the given already existing participations.
     * <p>
     * If the exercise is a programming exercise, also triggers a scheduling
     * update for the participations where the individual due date has changed.
     *
     * @param exerciseId     of the exercise the participations belong to.
     * @param participations for which the individual due date should be updated.
     * @return all participations where the individual due date actually changed.
     */
    @PutMapping("exercises/{exerciseId}/participations/update-individual-due-date")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentParticipation>> updateParticipationDueDates(@PathVariable long exerciseId, @RequestBody List<StudentParticipation> participations) {
        final boolean anyInvalidExerciseId = participations.stream()
                .anyMatch(participation -> participation.getExercise() == null || participation.getExercise().getId() == null || exerciseId != participation.getExercise().getId());
        if (anyInvalidExerciseId) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }

        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot set individual due dates for exam exercises", ENTITY_NAME, "examexercise");
        }
        if (exercise instanceof QuizExercise) {
            throw new BadRequestAlertException("Cannot set individual due dates for quiz exercises", ENTITY_NAME, "quizexercise");
        }

        final List<StudentParticipation> changedParticipations = participationService.updateIndividualDueDates(exercise, participations);
        final List<StudentParticipation> updatedParticipations = studentParticipationRepository.saveAllAndFlush(changedParticipations);

        if (!updatedParticipations.isEmpty() && exercise instanceof ProgrammingExercise programmingExercise) {
            log.info("Updating scheduling for exercise {} (id {}) due to changed individual due dates.", exercise.getTitle(), exercise.getId());
            instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());
            List<StudentParticipation> participationsBeforeDueDate = updatedParticipations.stream().filter(exerciseDateService::isBeforeDueDate).toList();
            List<StudentParticipation> participationsAfterDueDate = updatedParticipations.stream().filter(exerciseDateService::isAfterDueDate).toList();

            if (exercise.isTeamMode()) {
                participationService.initializeTeamParticipations(participationsBeforeDueDate);
                participationService.initializeTeamParticipations(participationsAfterDueDate);
            }
        }

        return ResponseEntity.ok().body(updatedParticipations);
    }

    private Course findCourseFromParticipation(StudentParticipation participation) {
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }
}
