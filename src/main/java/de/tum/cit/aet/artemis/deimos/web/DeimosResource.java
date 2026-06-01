package de.tum.cit.aet.artemis.deimos.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastInstructorInExercise;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.deimos.api.DeimosBatchApi;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchRequestDTO;
import de.tum.cit.aet.artemis.deimos.dto.DeimosBatchTriggerResponseDTO;
import de.tum.cit.aet.artemis.deimos.service.DeimosBatchService;

@Profile(PROFILE_CORE)
@ConditionalOnProperty(name = "artemis.deimos.enabled", havingValue = "true")
@Lazy
@RestController
@RequestMapping("api/deimos/")
@FeatureToggle(Feature.Deimos)
public class DeimosResource implements DeimosBatchApi {

    private final DeimosBatchService deimosBatchService;

    private final UserRepository userRepository;

    public DeimosResource(DeimosBatchService deimosBatchService, UserRepository userRepository) {
        this.deimosBatchService = deimosBatchService;
        this.userRepository = userRepository;
    }

    @Override
    @PostMapping("courses/{courseId}/analysis-runs")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<DeimosBatchTriggerResponseDTO> triggerCourseBatch(@PathVariable long courseId, @Valid @RequestBody DeimosBatchRequestDTO request) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.accepted().body(deimosBatchService.triggerCourseBatch(courseId, request, user));
    }

    @Override
    @PostMapping("programming-exercises/{exerciseId}/analysis-runs")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<DeimosBatchTriggerResponseDTO> triggerExerciseBatch(@PathVariable long exerciseId, @Valid @RequestBody DeimosBatchRequestDTO request) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.accepted().body(deimosBatchService.triggerExerciseBatch(exerciseId, request, user));
    }
}
