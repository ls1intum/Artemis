package de.tum.cit.aet.artemis.aiworker.api;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import de.tum.cit.aet.artemis.aiworker.dto.ExecutionAssignmentDTO;
import de.tum.cit.aet.artemis.aiworker.dto.ExecutionIdentityDTO;
import de.tum.cit.aet.artemis.aiworker.dto.WorkloadCapabilityDTO;

/** Installed, trusted workload implementation. Inputs never select classes or executable code. */
public interface WorkloadApi {

    /**
     * Identifies the installed handler, independently of the configured worker profile.
     *
     * @return the exact supported workload schema and execution profile
     */
    WorkloadCapabilityDTO capability();

    /**
     * Executes validated workload data. A stop signal asks the handler to return its best available result promptly;
     * the supervisor separately determines whether the terminal state is completion or cancellation.
     *
     * @param assignment immutable execution authority and bounded workload payload
     * @param stopping   cooperative finish, cancellation or coordinator-loss signal
     * @param observer   bounded progress and reliable evidence delivery
     * @param checkpoint publishes the latest candidate without requesting durable application writes
     * @return bounded workload-owned result payload
     */
    String execute(ExecutionAssignmentDTO assignment, BooleanSupplier stopping, ExecutionObserver observer, Consumer<String> checkpoint);

    /**
     * Allows a workload to cancel execution-scoped resources before supervisor cleanup.
     *
     * @param identity exact execution to cancel, never another run sharing the resource reference
     * @return true if the workload handles immediate cancellation; false requests immediate sandbox destruction
     */
    default boolean requestCancel(ExecutionIdentityDTO identity) {
        return false;
    }
}
