package de.tum.cit.aet.artemis.iris.web;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.StruggleInterventionAcceptedDTO;
import de.tum.cit.aet.artemis.iris.service.session.IrisStruggleInterventionService;

/**
 * Exercise-keyed trigger for the proactive struggle-intervention feature (spec §5.2). The client engine has
 * already gated the alert; Iris acts as a downstream intervention gate. Async command: returns {@code 202}
 * immediately; the outcome arrives over the per-user struggle topic. No user message is persisted, no session
 * is created here.
 */
@Conditional(IrisEnabled.class)
@Lazy
@RestController
@RequestMapping("api/iris/chat/")
public class IrisStruggleInterventionResource {

    private final IrisStruggleInterventionService struggleInterventionService;

    private final UserRepository userRepository;

    public IrisStruggleInterventionResource(IrisStruggleInterventionService struggleInterventionService, UserRepository userRepository) {
        this.struggleInterventionService = struggleInterventionService;
        this.userRepository = userRepository;
    }

    /**
     * POST exercises/{exerciseId}/struggle-intervention : trigger a proactive struggle intervention.
     *
     * @param exerciseId the struggled programming exercise (the binding key)
     * @param requestDTO struggle signal + exercise-scoped uncommitted files
     * @return {@code 202 Accepted} {@code {accepted, exerciseId, jobId}} (fire-and-forget; result over websocket)
     */
    @PostMapping("exercises/{exerciseId}/struggle-intervention")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<StruggleInterventionAcceptedDTO> triggerStruggleIntervention(@PathVariable long exerciseId, @RequestBody IrisStruggleInterventionRequestDTO requestDTO) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Explicit server-side AI opt-in gate (spec §10), before any pipeline work.
        user.hasOptedIntoLLMUsageElseThrow();
        var jobId = struggleInterventionService.requestStruggleIntervention(exerciseId, requestDTO.struggleSignal(), requestDTO.uncommittedFiles(), user);
        return ResponseEntity.accepted().body(new StruggleInterventionAcceptedDTO(jobId.isPresent(), exerciseId, jobId.orElse(null)));
    }
}
