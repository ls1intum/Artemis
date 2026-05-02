package de.tum.cit.aet.artemis.deimos.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import jakarta.validation.Valid;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.repository.UserRepository;
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
@RequestMapping("api/programming")
@FeatureToggle(Feature.Deimos)
public class DeimosResource implements DeimosBatchApi {

    private final DeimosBatchService deimosBatchService;

    private final UserRepository userRepository;

    public DeimosResource(DeimosBatchService deimosBatchService, UserRepository userRepository) {
        this.deimosBatchService = deimosBatchService;
        this.userRepository = userRepository;
    }

    @Override
    @PostMapping("courses/{course-id}/deimos/batch")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<DeimosBatchTriggerResponseDTO> triggerCourseBatch(@PathVariable("course-id") long courseId, @Valid @RequestBody DeimosBatchRequestDTO request) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.accepted().body(deimosBatchService.triggerCourseBatch(courseId, request, user));
    }

    @Override
    @PostMapping("programming-exercises/{exercise-id}/deimos/batch")
    @EnforceAtLeastInstructorInExercise
    public ResponseEntity<DeimosBatchTriggerResponseDTO> triggerExerciseBatch(@PathVariable("exercise-id") long exerciseId, @Valid @RequestBody DeimosBatchRequestDTO request) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.accepted().body(deimosBatchService.triggerExerciseBatch(exerciseId, request, user));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }
}
