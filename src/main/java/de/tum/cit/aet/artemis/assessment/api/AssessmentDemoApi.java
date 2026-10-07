package de.tum.cit.aet.artemis.assessment.api;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_DEMO_AND_SCHEDULING;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.dto.GradingScaleUpdateDTO;
import de.tum.cit.aet.artemis.assessment.dto.GradingScaleUpdateDTO.GradeStepDTO;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.core.api.AbstractApi;
import de.tum.cit.aet.artemis.core.config.StartupDelayConfig;
import de.tum.cit.aet.artemis.course.domain.Course;

/**
 * Creates the grading scale of the demo course seeded by the {@code demo} profile, and makes the results seeded for the demo course count towards the scores of its students.
 * <p>
 * Only exists on the node that seeds the demo data, so none of this is instantiated on a regular instance.
 */
@Controller
@Lazy
@Profile(PROFILE_DEMO_AND_SCHEDULING)
public class AssessmentDemoApi implements AbstractApi {

    /**
     * The lower bound of the first passing grade, 4.0, in percent.
     */
    private static final double FIRST_PASSING_LOWER_BOUND = 50;

    /**
     * The grade steps the client proposes for a new grading scale, see {@code GradingComponent#getDefaultGradingScale}: German grades from 5.0 to 1.0, of which 4.0 is the
     * first passing grade.
     */
    private static final Set<GradeStepDTO> DEMO_GRADE_STEPS = Set.of(gradeStep(0, 40, "5.0"), gradeStep(40, 45, "4.7"), gradeStep(45, 50, "4.3"), gradeStep(50, 55, "4.0"),
            gradeStep(55, 60, "3.7"), gradeStep(60, 65, "3.3"), gradeStep(65, 70, "3.0"), gradeStep(70, 75, "2.7"), gradeStep(75, 80, "2.3"), gradeStep(80, 85, "2.0"),
            gradeStep(85, 90, "1.7"), gradeStep(90, 95, "1.3"), gradeStep(95, 100, "1.0"));

    private static final Logger log = LoggerFactory.getLogger(AssessmentDemoApi.class);

    private final ParticipantScoreScheduleService participantScoreScheduleService;

    private final GradingScaleService gradingScaleService;

    public AssessmentDemoApi(ParticipantScoreScheduleService participantScoreScheduleService, GradingScaleService gradingScaleService) {
        this.participantScoreScheduleService = participantScoreScheduleService;
        this.gradingScaleService = gradingScaleService;
    }

    /**
     * Lets the participant scores follow result changes right away, so that the results the demo exercises create get participant scores as well.
     * <p>
     * The course scores, the statistics and the competency progress of the demo course are all computed from participant scores. {@link ParticipantScoreScheduleService} however
     * ignores result changes until it activates itself {@link StartupDelayConfig#PARTICIPATION_SCORES_SCHEDULE_DELAY_SEC} seconds after it was created, and its catch-up run at
     * that point only processes results that changed after the latest participant score, which on a fresh database is the moment of the run itself. The results seeded at startup
     * would therefore never get a participant score. Activating the service early only makes it react to result changes sooner, the catch-up run still happens as usual.
     */
    public void activateParticipantScores() {
        participantScoreScheduleService.activate();
    }

    /**
     * Creates the grading scale of the demo course if the course does not have one yet, with the grade steps the client proposes for a new grading scale.
     * <p>
     * This mirrors {@code GradingScaleResource#createGradingScaleForCourse} for the request the client sends when an instructor saves the proposed grade steps unchanged. That
     * request carries neither maximum points nor a presentation score for the course, so the course itself is left as it is.
     *
     * @param course the demo course.
     */
    public void createDemoGradingScale(Course course) {
        if (gradingScaleService.findGradingScaleByCourseId(course.getId()).isPresent()) {
            log.debug("Demo grading scale already exists, skipping creation");
            return;
        }

        GradingScale gradingScale = new GradingScaleUpdateDTO(GradeType.GRADE, null, null, null, null, null, DEMO_GRADE_STEPS, null, null, null).toEntity();
        gradingScale.setCourse(course);
        GradingScale createdGradingScale = gradingScaleService.saveGradingScale(gradingScale);

        log.info("Created demo grading scale for course '{}' with id {}", course.getShortName(), createdGradingScale.getId());
    }

    /**
     * Creates a grade step with the bounds the client applies to its proposed grade steps: every step includes its lower bound, and only the last one also includes its upper
     * bound.
     */
    private static GradeStepDTO gradeStep(double lowerBoundPercentage, double upperBoundPercentage, String gradeName) {
        return new GradeStepDTO(lowerBoundPercentage, true, upperBoundPercentage, upperBoundPercentage == 100, gradeName, lowerBoundPercentage >= FIRST_PASSING_LOWER_BOUND);
    }
}
