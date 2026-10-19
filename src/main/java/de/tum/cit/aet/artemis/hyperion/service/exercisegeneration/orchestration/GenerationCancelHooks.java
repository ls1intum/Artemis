package de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.orchestration;

import java.io.Serial;
import java.io.Serializable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tum.cit.aet.artemis.core.service.distributed.api.DistributedDataProvider;

/**
 * Runs the local cancel hook of a generation job and broadcasts the interrupt to every other core node.
 * <p>
 * A hook closes over live sandbox objects, so it can only run on the node holding them. The authoritative cancellation state lives in the job service's distributed map; this
 * class only makes cancellation prompt, so a lost broadcast is logged rather than treated as a failure.
 */
final class GenerationCancelHooks {

    private static final Logger log = LoggerFactory.getLogger(GenerationCancelHooks.class);

    static final String CANCEL_TOPIC_NAME = "hyperion-exercise-generation-cancel-requests";

    private final DistributedDataProvider distributedDataProvider;

    private final Executor executor;

    private final ConcurrentMap<String, Runnable> hooks = new ConcurrentHashMap<>();

    GenerationCancelHooks(DistributedDataProvider distributedDataProvider, Executor executor) {
        this.distributedDataProvider = distributedDataProvider;
        this.executor = executor;
    }

    /** Subscribes this node to cluster-wide interrupts; a message for a job whose hook lives elsewhere is a no-op here. */
    void subscribe() {
        distributedDataProvider.<CancelRequest>getTopic(CANCEL_TOPIC_NAME).addMessageListener(message -> runLocal(message.jobId()));
    }

    /**
     * Registers the hook for a job. When cancellation was already requested before the hook existed, the hook runs once immediately instead of never.
     *
     * @param jobId            the job the hook belongs to
     * @param hook             the interrupt to run on this node
     * @param alreadyCancelled whether cancellation of the job has already been recorded
     */
    void register(String jobId, Runnable hook, boolean alreadyCancelled) {
        hooks.put(jobId, hook);
        if (alreadyCancelled && hooks.remove(jobId, hook)) {
            dispatch(jobId, hook);
        }
    }

    void deregister(String jobId) {
        hooks.remove(jobId);
    }

    /** Runs the hook here and broadcasts, so cancellation is prompt even when the request hits a different core node than the one running the sandbox. */
    void interruptCluster(String jobId) {
        runLocal(jobId);
        try {
            distributedDataProvider.<CancelRequest>getTopic(CANCEL_TOPIC_NAME).publish(new CancelRequest(jobId));
        }
        catch (RuntimeException e) {
            log.warn("Could not publish the cluster interrupt for cancelled generation job {}; workers will still observe the authoritative cancellation", jobId, e);
        }
    }

    private void runLocal(String jobId) {
        Runnable hook = hooks.remove(jobId);
        if (hook != null) {
            dispatch(jobId, hook);
        }
    }

    private void dispatch(String jobId, Runnable hook) {
        try {
            executor.execute(() -> run(jobId, hook));
        }
        catch (RejectedExecutionException e) {
            log.error("Cancel hook dispatch for job {} was rejected; the generation worker will still observe the authoritative cancellation", jobId, e);
        }
    }

    private static void run(String jobId, Runnable hook) {
        try {
            hook.run();
        }
        catch (RuntimeException e) {
            log.warn("Cancel hook for job {} failed", jobId, e);
        }
    }

    private record CancelRequest(String jobId) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;
    }
}
