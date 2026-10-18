package de.tum.cit.aet.artemis.assessment.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.assessment.domain.FeatureBonusAward;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusPointsSumDTO;
import de.tum.cit.aet.artemis.assessment.dto.ParticipationFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.repository.FeatureBonusAwardRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.course.domain.CourseConfiguration;
import de.tum.cit.aet.artemis.course.repository.CourseConfigurationRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

/**
 * Awards feature bonus points and converts them into the bonus on the final grade.
 * <p>
 * Tutors award points for features students implemented beyond the task while they assess a participation. The points never
 * change the score of the exercise. The course converts a student's points, over all exercises and teams, with its percent per
 * point and caps the result at its maximum; the instructor adds that bonus on top of the final grade outside of Artemis.
 */
@Profile(PROFILE_CORE)
@Lazy
@Service
public class FeatureBonusService {

    private static final String ENTITY_NAME = "featureBonusAward";

    private final FeatureBonusAwardRepository featureBonusAwardRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final CourseConfigurationRepository courseConfigurationRepository;

    private final AuthorizationCheckService authCheckService;

    private final AssessmentService assessmentService;

    private final UserRepository userRepository;

    public FeatureBonusService(FeatureBonusAwardRepository featureBonusAwardRepository, StudentParticipationRepository studentParticipationRepository,
            ResultRepository resultRepository, CourseConfigurationRepository courseConfigurationRepository, AuthorizationCheckService authCheckService,
            AssessmentService assessmentService, UserRepository userRepository) {
        this.featureBonusAwardRepository = featureBonusAwardRepository;
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.courseConfigurationRepository = courseConfigurationRepository;
        this.authCheckService = authCheckService;
        this.assessmentService = assessmentService;
        this.userRepository = userRepository;
    }

    /**
     * Returns the awards of a participation to a tutor of its exercise.
     *
     * @param participationId the id of the participation
     * @return the awards, oldest first
     */
    public List<FeatureBonusAwardDTO> getAwards(long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, participation.getExercise(), userRepository.getUserWithAuthorities());
        return featureBonusAwardRepository.findAllByParticipationId(participationId);
    }

    /**
     * Awards feature bonus points to a participation.
     *
     * @param participationId the id of the participation
     * @param request         the feature, the reason and the points
     * @return the stored award
     */
    public FeatureBonusAwardDTO createAward(long participationId, FeatureBonusAwardRequestDTO request) {
        StudentParticipation participation = checkIsAllowedToAwardElseThrow(participationId);
        FeatureBonusAward award = new FeatureBonusAward();
        award.setParticipation(participation);
        applyRequest(award, request);
        return FeatureBonusAwardDTO.of(featureBonusAwardRepository.save(award));
    }

    /**
     * Changes an award of a participation.
     *
     * @param participationId the id of the participation
     * @param awardId         the id of the award
     * @param request         the new feature, reason and points
     * @return the changed award
     */
    public FeatureBonusAwardDTO updateAward(long participationId, long awardId, FeatureBonusAwardRequestDTO request) {
        checkIsAllowedToAwardElseThrow(participationId);
        FeatureBonusAward award = findAwardOfParticipationElseThrow(participationId, awardId);
        applyRequest(award, request);
        return FeatureBonusAwardDTO.of(featureBonusAwardRepository.save(award));
    }

    /**
     * Removes an award of a participation.
     *
     * @param participationId the id of the participation
     * @param awardId         the id of the award
     */
    public void deleteAward(long participationId, long awardId) {
        checkIsAllowedToAwardElseThrow(participationId);
        featureBonusAwardRepository.delete(findAwardOfParticipationElseThrow(participationId, awardId));
    }

    /**
     * Sums the feature bonus of every participation of an exercise that holds awards.
     *
     * @param exercise the exercise
     * @return the points and the converted bonus per participation; participations without awards are left out
     */
    public List<ParticipationFeatureBonusDTO> getFeatureBonusPerParticipation(Exercise exercise) {
        Optional<CourseConfiguration> configuration = findConfiguration(exercise.getCourseViaExerciseGroupOrCourseMember());
        Course course = exercise.getCourseViaExerciseGroupOrCourseMember();
        return featureBonusAwardRepository.sumPointsPerParticipationByExerciseId(exercise.getId()).stream()
                .map(sum -> new ParticipationFeatureBonusDTO(sum.id(), sum.points(), toBonusPercent(sum.points(), configuration, course))).toList();
    }

    /**
     * Sums the feature bonus of every student of a course who holds awards, adding the awards of the student's teams.
     *
     * @param course the course
     * @return the points and the converted bonus per student; students without awards are left out
     */
    public List<StudentFeatureBonusDTO> getFeatureBonusPerStudent(Course course) {
        Optional<CourseConfiguration> configuration = findConfiguration(course);
        Map<Long, Double> pointsPerStudent = new HashMap<>();
        for (FeatureBonusPointsSumDTO sum : featureBonusAwardRepository.sumIndividualPointsPerStudentByCourseId(course.getId())) {
            pointsPerStudent.merge(sum.id(), sum.points(), Double::sum);
        }
        for (FeatureBonusPointsSumDTO sum : featureBonusAwardRepository.sumTeamPointsPerStudentByCourseId(course.getId())) {
            pointsPerStudent.merge(sum.id(), sum.points(), Double::sum);
        }
        return pointsPerStudent.entrySet().stream()
                .map(entry -> new StudentFeatureBonusDTO(entry.getKey(), entry.getValue(), toBonusPercent(entry.getValue(), configuration, course))).toList();
    }

    /**
     * Converts feature bonus points into the bonus on the final grade: the points times the percent per point, capped at the
     * maximum and rounded like the scores of the course.
     *
     * @param points        the awarded points
     * @param configuration the configuration of the course
     * @param course        the course, for the rounding
     * @return the bonus in percent, or null while the course has no conversion
     */
    private static Double toBonusPercent(double points, Optional<CourseConfiguration> configuration, Course course) {
        return configuration.filter(config -> config.getFeatureBonusPercentPerPoint() != null && config.getFeatureBonusMaxPercent() != null).map(
                config -> RoundingUtil.roundScoreSpecifiedByCourseSettings(Math.min(points * config.getFeatureBonusPercentPerPoint(), config.getFeatureBonusMaxPercent()), course))
                .orElse(null);
    }

    private Optional<CourseConfiguration> findConfiguration(Course course) {
        return course == null ? Optional.empty() : courseConfigurationRepository.findByCourseId(course.getId());
    }

    /**
     * A tutor awards feature bonus points while assessing: the exercise must allow it, the due date of the participation must
     * have passed, and the user must hold the assessment of the participation, which is the manual result the tutor locked
     * when opening the submission. Like the assessment itself, instructors may also change a result someone else holds.
     *
     * @param participationId the id of the participation
     * @return the participation
     */
    private StudentParticipation checkIsAllowedToAwardElseThrow(long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        if (!(participation.getExercise() instanceof ProgrammingExercise exercise) || !exercise.isCourseExercise() || !exercise.isFeatureBonusEnabled()) {
            throw new BadRequestAlertException("The exercise does not allow feature bonus points", ENTITY_NAME, "featureBonusDisabled");
        }
        User user = userRepository.getUserWithAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, user);
        if (participation.isPracticeMode() || !exercise.areManualResultsAllowed() || !isAfterDueDate(participation)) {
            throw new BadRequestAlertException("Feature bonus points can only be awarded in the assessment after the due date", ENTITY_NAME, "featureBonusNotAssessable");
        }
        Result manualResult = participation.getSubmissions().stream().flatMap(submission -> submission.getResults().stream()).filter(Objects::nonNull).filter(Result::isManual)
                .max(Comparator.comparing(Result::getId))
                .orElseThrow(() -> new AccessForbiddenException("Start the assessment of the participation before awarding feature bonus points"));
        manualResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(manualResult.getId());
        boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(exercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(manualResult, exercise, participation, user, isAtLeastInstructor)) {
            throw new AccessForbiddenException("Only the tutor assessing the participation can award feature bonus points");
        }
        return participation;
    }

    /**
     * A participation without a due date can be assessed at any time, like the manual results of its exercise.
     */
    private static boolean isAfterDueDate(StudentParticipation participation) {
        return ExerciseDateService.getDueDate(participation).map(dueDate -> ZonedDateTime.now().isAfter(dueDate)).orElse(true);
    }

    private FeatureBonusAward findAwardOfParticipationElseThrow(long participationId, long awardId) {
        FeatureBonusAward award = featureBonusAwardRepository.findByIdElseThrow(awardId);
        if (award.getParticipation().getId() != participationId) {
            throw new BadRequestAlertException("The award does not belong to the participation", ENTITY_NAME, "participationMismatch");
        }
        return award;
    }

    private static void applyRequest(FeatureBonusAward award, FeatureBonusAwardRequestDTO request) {
        award.setTitle(request.title().strip());
        award.setJustification(request.justification() == null || request.justification().isBlank() ? null : request.justification().strip());
        award.setPoints(request.points());
    }
}
