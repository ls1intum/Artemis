package de.tum.cit.aet.artemis.iris.web;

import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.account.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisProactiveOutcome;
import de.tum.cit.aet.artemis.iris.dto.CancelStruggleJobRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.EpisodeOutcomeAppliedDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisMessageResponseDTO;
import de.tum.cit.aet.artemis.iris.dto.IrisStruggleInterventionRequestDTO;
import de.tum.cit.aet.artemis.iris.dto.RevealAmbientRequestDTO;
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
    public ResponseEntity<StruggleInterventionAcceptedDTO> triggerStruggleIntervention(@PathVariable long exerciseId,
            @Valid @RequestBody IrisStruggleInterventionRequestDTO requestDTO) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        // Explicit server-side AI opt-in gate (spec §10), before any pipeline work.
        user.hasOptedIntoLLMUsageElseThrow();
        var outcome = struggleInterventionService.requestStruggleIntervention(exerciseId, requestDTO.struggleSignal(), requestDTO.uncommittedFiles(), requestDTO.intent(),
                requestDTO.episode(), requestDTO.confirmReason(), requestDTO.requestToken(), user);
        return ResponseEntity.accepted().body(new StruggleInterventionAcceptedDTO(outcome.accepted(), outcome.courseDisabled(), exerciseId, outcome.jobToken()));
    }

    /**
     * POST exercises/{exerciseId}/episodes/{episodeId}/reveal : persist a previously-hidden ambient hint.
     *
     * <p>
     * Idempotent on {@code clientMessageId}: a retry with the same UUID returns the same row. Does NOT broadcast
     * over the chat websocket (the client owns the optimistic bubble; C2 reconciles via the returned DTO).
     *
     * @param exerciseId the programming exercise id (session scope)
     * @param episodeId  the client-allocated episode UUID
     * @param body       the hint text, level tag, and client idempotency key
     * @return {@code 200 OK} with the persisted {@link IrisMessageResponseDTO} ({@code id} + {@code proactiveEpisodeId})
     */
    @PostMapping("exercises/{exerciseId}/episodes/{episodeId}/reveal")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<IrisMessageResponseDTO> revealAmbient(@PathVariable long exerciseId, @PathVariable String episodeId, @RequestBody RevealAmbientRequestDTO body) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        user.hasOptedIntoLLMUsageElseThrow();
        var dto = struggleInterventionService.revealAmbient(user, exerciseId, episodeId, body.hintText(), body.level(), body.clientMessageId());
        return ResponseEntity.ok(dto);
    }

    /**
     * DELETE exercises/{exerciseId}/messages/{messageId}/proactive : durably delete a superseded proactive row.
     *
     * <p>
     * Only deletes if the row is {@code PROACTIVE_STRUGGLE} origin, belongs to the requesting user's session, and
     * has a null {@code proactiveOutcome}. All other cases (missing, wrong origin, wrong user, terminal outcome)
     * are silent {@code 204} noops.
     *
     * @param exerciseId the programming exercise id (kept for route symmetry; auth is via session ownership)
     * @param messageId  the id of the proactive message row to delete
     * @return {@code 204 No Content}
     */
    @DeleteMapping("exercises/{exerciseId}/messages/{messageId}/proactive")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<Void> deleteProactiveMessage(@PathVariable long exerciseId, @PathVariable long messageId) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        struggleInterventionService.deleteSupersededProactiveMessage(user, messageId);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST exercises/{exerciseId}/struggle-intervention/cancel : scoped-cancel an in-flight struggle request.
     *
     * <p>
     * Removes the pending job ONLY IF its stamped {@code requestToken} matches the provided value (prevents
     * {@code cancel(A)} from accidentally removing a since-started B). Idempotent: no matching job returns
     * {@code 204} as well.
     *
     * @param exerciseId the programming exercise id
     * @param body       contains the {@code requestToken} identifying the specific in-flight request to cancel
     * @return {@code 204 No Content}
     */
    @PostMapping("exercises/{exerciseId}/struggle-intervention/cancel")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<Void> cancelStruggleJob(@PathVariable long exerciseId, @RequestBody CancelStruggleJobRequestDTO body) {
        var user = userRepository.getUserWithGroupsAndAuthorities();
        struggleInterventionService.cancelOutstandingStruggleJob(user, exerciseId, body.requestToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * PUT exercises/{exerciseId}/episodes/{episodeId}/proactive-outcome : episode-keyed first-terminal-wins
     * outcome write. Accepts {@code DISMISSED}, {@code RECOVERED}, and {@code ABANDONED}.
     *
     * <p>
     * Returns a body-bearing {@code 200} with {@code {"applied": true}} when a canonical row existed (outcome
     * written or first-terminal-wins already set), or {@code {"applied": false}} when no row exists yet for the
     * episode (deferred; the client back-fills once the reveal row is persisted). Never {@code 204}: a
     * {@code 204 No Content} cannot carry the {@code applied} discriminator.
     *
     * @param exerciseId the programming exercise id (auth scope; ensures the caller is a student in this exercise)
     * @param episodeId  the client-allocated episode UUID
     * @param outcome    the terminal outcome to write
     * @return {@code 200 OK} with {@link EpisodeOutcomeAppliedDTO}
     */
    @PutMapping("exercises/{exerciseId}/episodes/{episodeId}/proactive-outcome")
    @EnforceAtLeastStudent
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<EpisodeOutcomeAppliedDTO> setEpisodeOutcome(@PathVariable long exerciseId, @PathVariable String episodeId, @RequestBody IrisProactiveOutcome outcome) {
        if (outcome == null) {
            throw new BadRequestException("An episode outcome is required");
        }
        // No LLM opt-in gate (unlike the trigger/reveal): recording a student's reaction to an already-delivered hint
        // (notably DISMISSED) must never be rejected, even if the student's opt-in lapsed after the hint was shown.
        // The brief mandates that an explicit terminal outcome is always eventually written - a 403 here would make the
        // client's back-fill retry loop forever. This matches the ungated existing proactive-outcome endpoint.
        boolean applied = struggleInterventionService.writeEpisodeOutcome(episodeId, outcome);
        return ResponseEntity.ok(new EpisodeOutcomeAppliedDTO(applied));
    }
}
