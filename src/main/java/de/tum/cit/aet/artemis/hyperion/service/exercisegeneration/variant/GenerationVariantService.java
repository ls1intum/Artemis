package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmissionService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationExternalMutationService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;

/** Adapts into a new destination using the same authoring job, worker, verification and persistence as in-place adaptation. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationVariantService {

    private final GenerationAdmissionService admission;

    private final GenerationVariantDraftService drafts;

    private final GenerationExternalMutationService mutations;

    private final GenerationJobService jobs;

    private final GenerationVariantPlacementService placement;

    public GenerationVariantService(GenerationAdmissionService admission, GenerationVariantDraftService drafts, GenerationExternalMutationService mutations,
            GenerationJobService jobs, GenerationVariantPlacementService placement) {
        this.admission = admission;
        this.drafts = drafts;
        this.mutations = mutations;
        this.jobs = jobs;
        this.placement = placement;
    }

    /**
     * Starts a variant after the REST caller authorizes the source. No model or repository copy runs on the request thread.
     *
     * @param user     authorized editor
     * @param sourceId source exercise
     * @param request  transformation and destination placement
     * @return common authoring job and explicit source/destination identity
     */
    public ExerciseGenerationJobStartDTO start(User user, long sourceId, VariantGenerationRequestDTO request) {
        var reservation = admission.reserveVariant(user, sourceId, request);
        AtomicReference<GenerationStartedEvent> prepared = new AtomicReference<>();
        String sourceToken = null;
        boolean dispatched = false;
        try {
            placement.validate(reservation.source(), request);
            sourceToken = mutations.claimParticipationSlot(sourceId);
            var context = new GenerationVariantPreparation(sourceId, sourceToken, request);
            GenerationStartedEvent event = drafts.prepare(sourceId, request, destination -> {
                var start = jobs.prepareVariantJob(user, destination, reservation.prompt(), reservation.budgetReservationId(), reservation.settings(), reservation.input(),
                        context);
                prepared.set(start);
                return start;
            });
            // prepare returns only after its repository-owned transaction commits. A worker can never race an invisible draft.
            dispatched = jobs.dispatchPreparedJob(event);
            return new ExerciseGenerationJobStartDTO(event.jobId(), event.exercise().getId(), sourceId);
        }
        catch (RuntimeException failure) {
            GenerationStartedEvent event = prepared.get();
            if (event != null) {
                jobs.clearJob(event.exercise().getId(), event.jobId());
            }
            throw failure;
        }
        finally {
            if (!dispatched) {
                try {
                    if (sourceToken != null) {
                        mutations.clearParticipationSlot(sourceId, sourceToken);
                    }
                }
                finally {
                    admission.release(reservation);
                }
            }
        }
    }

    /**
     * Copies the source while its shared reservation excludes edits; the common destination slot excludes student starts.
     *
     * @param event admitted common job
     */
    public void prepareInfrastructure(GenerationStartedEvent event) {
        var preparation = event.variantPreparation();
        if (preparation == null) {
            return;
        }
        try {
            if (!jobs.isOwnedActiveJob(event.exercise().getId(), event.jobId())) {
                throw new IllegalStateException("The destination reservation was lost before preparation");
            }
            asInitiator(event.user(), () -> {
                drafts.complete(preparation.sourceExerciseId(), event.exercise().getId());
                return null;
            });
        }
        finally {
            releaseSource(event);
        }
    }

    /**
     * Completes the requested placement as part of the guarded save.
     *
     * @param event           common authoring job
     * @param verifyOwnership exact destination ownership check
     * @return placement warnings
     */
    public List<String> place(GenerationStartedEvent event, Runnable verifyOwnership) {
        return asInitiator(event.user(), () -> placement.place(event, verifyOwnership));
    }

    /**
     * Releases the source even when cancellation wins before repository preparation starts. Exact-token release is idempotent.
     *
     * @param event common job with optional source preparation
     */
    public void releaseSource(GenerationStartedEvent event) {
        var preparation = event.variantPreparation();
        if (preparation != null) {
            mutations.clearParticipationSlot(preparation.sourceExerciseId(), preparation.sourceReservationToken());
        }
    }

    private <T> T asInitiator(User user, Supplier<T> operation) {
        var previous = SecurityContextHolder.getContext();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(SecurityUtils.makeAuthorizationObject(user.getLogin()));
        SecurityContextHolder.setContext(context);
        try {
            return operation.get();
        }
        finally {
            SecurityContextHolder.setContext(previous);
        }
    }

}
