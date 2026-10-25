package de.tum.cit.aet.artemis.assessment.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.dto.CourseFeatureBonusSettingsDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeatureBonusAwardRequestDTO;
import de.tum.cit.aet.artemis.assessment.dto.ParticipationFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.dto.StudentFeatureBonusDTO;
import de.tum.cit.aet.artemis.assessment.service.FeatureBonusService;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastTutorInExercise;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.core.service.featureusage.UserFeature;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;

/**
 * REST controller for the feature bonus: points tutors award for additional features while assessing, and the bonus on the
 * final grade the course converts them into.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/assessment/")
@FeatureUsage(UserFeature.MANUAL_ASSESSMENT)
public class FeatureBonusResource {

    private final FeatureBonusService featureBonusService;

    private final ExerciseRepository exerciseRepository;

    private final CourseRepository courseRepository;

    public FeatureBonusResource(FeatureBonusService featureBonusService, ExerciseRepository exerciseRepository, CourseRepository courseRepository) {
        this.featureBonusService = featureBonusService;
        this.exerciseRepository = exerciseRepository;
        this.courseRepository = courseRepository;
    }

    /**
     * GET participations/{participationId}/feature-bonus-awards : the feature bonus awards of a participation.
     *
     * @param participationId the id of the participation
     * @return the awards, oldest first
     */
    @GetMapping("participations/{participationId}/feature-bonus-awards")
    @EnforceAtLeastTutor
    public ResponseEntity<List<FeatureBonusAwardDTO>> getAwards(@PathVariable long participationId) {
        return ResponseEntity.ok(featureBonusService.getAwards(participationId));
    }

    /**
     * POST participations/{participationId}/feature-bonus-awards : awards feature bonus points; only the tutor assessing the
     * participation may do so, after its due date.
     *
     * @param participationId the id of the participation
     * @param request         the feature, the reason and the points
     * @return the stored award
     */
    @PostMapping("participations/{participationId}/feature-bonus-awards")
    @EnforceAtLeastTutor
    public ResponseEntity<FeatureBonusAwardDTO> createAward(@PathVariable long participationId, @Valid @RequestBody FeatureBonusAwardRequestDTO request) {
        FeatureBonusAwardDTO award = featureBonusService.createAward(participationId, request);
        return ResponseEntity.created(URI.create("/api/assessment/participations/" + participationId + "/feature-bonus-awards/" + award.id())).body(award);
    }

    /**
     * PUT participations/{participationId}/feature-bonus-awards/{awardId} : changes a feature bonus award.
     *
     * @param participationId the id of the participation
     * @param awardId         the id of the award
     * @param request         the new feature, reason and points
     * @return the changed award
     */
    @PutMapping("participations/{participationId}/feature-bonus-awards/{awardId}")
    @EnforceAtLeastTutor
    public ResponseEntity<FeatureBonusAwardDTO> updateAward(@PathVariable long participationId, @PathVariable long awardId,
            @Valid @RequestBody FeatureBonusAwardRequestDTO request) {
        return ResponseEntity.ok(featureBonusService.updateAward(participationId, awardId, request));
    }

    /**
     * DELETE participations/{participationId}/feature-bonus-awards/{awardId} : removes a feature bonus award.
     *
     * @param participationId the id of the participation
     * @param awardId         the id of the award
     * @return 200 (OK)
     */
    @DeleteMapping("participations/{participationId}/feature-bonus-awards/{awardId}")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> deleteAward(@PathVariable long participationId, @PathVariable long awardId) {
        featureBonusService.deleteAward(participationId, awardId);
        return ResponseEntity.ok().build();
    }

    /**
     * GET exercises/{exerciseId}/feature-bonus : the feature bonus of every participation of an exercise that holds awards.
     *
     * @param exerciseId the id of the exercise
     * @return the points and the converted bonus per participation
     */
    @GetMapping("exercises/{exerciseId}/feature-bonus")
    @EnforceAtLeastTutorInExercise
    public ResponseEntity<List<ParticipationFeatureBonusDTO>> getFeatureBonusOfExercise(@PathVariable long exerciseId) {
        return ResponseEntity.ok(featureBonusService.getFeatureBonusPerParticipation(exerciseRepository.findByIdElseThrow(exerciseId)));
    }

    /**
     * GET courses/{courseId}/feature-bonus : the feature bonus of every student of a course who holds awards, over all exercises
     * and teams.
     *
     * @param courseId the id of the course
     * @return the points and the converted bonus per student
     */
    @GetMapping("courses/{courseId}/feature-bonus")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<List<StudentFeatureBonusDTO>> getFeatureBonusOfCourse(@PathVariable long courseId) {
        return ResponseEntity.ok(featureBonusService.getFeatureBonusPerStudent(courseRepository.findByIdElseThrow(courseId)));
    }

    /**
     * GET courses/{courseId}/feature-bonus-settings : the conversion of feature bonus points the course uses; exercises can only
     * allow the feature bonus while it is set.
     *
     * @param courseId the id of the course
     * @return the percent per point and the maximum, both unset while the course does not use the feature bonus
     */
    @GetMapping("courses/{courseId}/feature-bonus-settings")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<CourseFeatureBonusSettingsDTO> getFeatureBonusSettings(@PathVariable long courseId) {
        return ResponseEntity.ok(featureBonusService.getSettings(courseId));
    }
}
