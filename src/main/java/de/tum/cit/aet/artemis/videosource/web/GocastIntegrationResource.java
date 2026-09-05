package de.tum.cit.aet.artemis.videosource.web;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.service.featureusage.FeatureUsage;
import de.tum.cit.aet.artemis.videosource.domain.GocastBindingConnectionStatus;
import de.tum.cit.aet.artemis.videosource.dto.GocastApprovalStartDTO;
import de.tum.cit.aet.artemis.videosource.dto.GocastBindingDTO;
import de.tum.cit.aet.artemis.videosource.service.GocastBindingService;

@Lazy
@FeatureUsage("video/tum-live")
@RestController
@RequestMapping("api/videosource/courses/{courseId}/")
public class GocastIntegrationResource {

    private final Optional<GocastBindingService> bindingService;

    public GocastIntegrationResource(Optional<GocastBindingService> bindingService) {
        this.bindingService = bindingService;
    }

    @GetMapping("binding")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<GocastBindingDTO> getBinding(@PathVariable long courseId) {
        return ResponseEntity.ok(bindingService.map(service -> service.getBinding(courseId))
                .orElseGet(() -> new GocastBindingDTO(false, GocastBindingConnectionStatus.UNLINKED, null, null, null, null, null, false)));
    }

    @PostMapping("binding/approval")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<GocastApprovalStartDTO> startApproval(@PathVariable long courseId) {
        return ResponseEntity.ok(requiredService().startApproval(courseId));
    }

    @DeleteMapping("binding")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> unlink(@PathVariable long courseId) {
        requiredService().unlink(courseId);
        return ResponseEntity.noContent().build();
    }

    private GocastBindingService requiredService() {
        return bindingService.orElseThrow(() -> new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TUM.Live course connection is not configured"));
    }
}
