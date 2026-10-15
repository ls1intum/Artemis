package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.variant;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.account.domain.User;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.hyperion.config.HyperionExerciseGenerationEnabled;
import de.tum.cit.aet.artemis.hyperion.dto.ExerciseGenerationJobStartDTO;
import de.tum.cit.aet.artemis.hyperion.dto.VariantGenerationRequestDTO;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationAdmissionService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationJobService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationLiveSaveService;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationStartedEvent;
import de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration.GenerationVariantPreparation;

/** Adapts into a new destination using the same authoring job, worker, verification and persistence as in-place adaptation. */
@Service
@Lazy
@Conditional(HyperionExerciseGenerationEnabled.class)
public class GenerationVariantService {

    private final GenerationAdmissionService admission;

    private final GenerationVariantDraftService drafts;

    private final GenerationJobService jobs;

    private final GenerationVariantPlacementService placement;

    private final GenerationLiveSaveService liveSaves;

    private final AuthorizationCheckService authCheck;

    public GenerationVariantService(GenerationAdmissionService admission, GenerationVariantDraftService drafts, GenerationJobService jobs,
            GenerationVariantPlacementService placement, GenerationLiveSaveService liveSaves, AuthorizationCheckService authCheck) {
        this.liveSaves = liveSaves;
        this.authCheck = authCheck;
        this.admission = admission;
        this.drafts = drafts;
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
        GenerationAdmissionService.ReservedRun reservation = null;
        AtomicReference<GenerationStartedEvent> prepared = new AtomicReference<>();
        boolean dispatched = false;
        try {
            GenerationStartedEvent event;
            String sourceToken = liveSaves.claimReader(sourceId);
            try {
                var admitted = admission.reserveVariant(user, sourceId, request);
                reservation = admitted;
                authCheck.checkHasAtLeastRoleForExerciseElseThrow(Role.EDITOR, admitted.source(), user);
                placement.validate(admitted.source(), request);
                var sourceSnapshot = drafts.captureSource(sourceId);
                var context = new GenerationVariantPreparation(sourceId, sourceSnapshot, request);
                event = drafts.prepare(sourceId, request, destination -> {
                    var start = jobs.prepareVariantJob(user, destination, admitted.prompt(), admitted.budgetReservationId(), admitted.settings(), admitted.input(), context);
                    prepared.set(start);
                    drafts.verifySource(sourceId, sourceSnapshot);
                    return start;
                });
            }
            finally {
                liveSaves.releaseReader(sourceId, sourceToken);
            }
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
            if (!dispatched && reservation != null) {
                admission.release(reservation);
            }
        }
    }

    /**
     * Copies a stable source baseline while excluding live source writes only during the copy.
     *
     * @param event admitted common job
     */
    public void prepareInfrastructure(GenerationStartedEvent event) {
        var preparation = event.variantPreparation();
        if (preparation == null) {
            return;
        }
        if (!jobs.isOwnedActiveJob(event.exercise().getId(), event.jobId())) {
            throw new IllegalStateException("The destination reservation was lost before preparation");
        }
        String sourceToken = liveSaves.claimReader(preparation.sourceExerciseId());
        try {
            asInitiator(event.user(), () -> {
                drafts.verifySource(preparation.sourceExerciseId(), preparation.sourceSnapshot());
                drafts.complete(preparation.sourceExerciseId(), event.exercise().getId());
                drafts.verifySource(preparation.sourceExerciseId(), preparation.sourceSnapshot());
                return null;
            });
        }
        finally {
            liveSaves.releaseReader(preparation.sourceExerciseId(), sourceToken);
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
