package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.topic.DistributedTopic;

/**
 * This service is used to access the distributed data structures.
 * All data structures are created lazily, meaning they are only created when they are first accessed.
 */
@Lazy
@Service
@Profile({ PROFILE_LOCALCI, PROFILE_BUILDAGENT })
public class DistributedDataAccessService {

    private final DistributedDataProvider distributedDataProvider;

    private DistributedQueue<BuildJobQueueItem> buildJobQueue;

    private DistributedMap<String, BuildJobQueueItem> processingJobs;

    private DistributedQueue<ResultQueueItem> buildResultQueue;

    private DistributedMap<String, BuildAgentInformation> buildAgentInformation;

    private DistributedMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private DistributedTopic<String> canceledBuildJobsTopic;

    private DistributedTopic<String> pauseBuildAgentTopic;

    private DistributedTopic<String> resumeBuildAgentTopic;

    public DistributedDataAccessService(Optional<DistributedDataProvider> distributedDataProvider) {
        this.distributedDataProvider = distributedDataProvider.orElseThrow(
                () -> new IllegalStateException("DistributedDataProvider is not available. " + "Please ensure that the application is running with the correct profile"));
    }

    /**
     * This method is used to get the distributed queue of build jobs. This should only be used in special cases like writing to the queue or adding a listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getQueuedJobs()} method.
     * The queue is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed queue of build jobs.
     */
    public DistributedQueue<BuildJobQueueItem> getDistributedBuildJobQueue() {
        if (this.buildJobQueue == null) {
            this.buildJobQueue = this.distributedDataProvider.getPriorityQueue("buildJobQueue");
        }
        return this.buildJobQueue;
    }

    /**
     * This method is used to get a List containing all queued build jobs. This should be used for reading the queue.
     * If you want to write to the queue or add a listener, use {@link DistributedDataAccessService#getDistributedBuildJobQueue()} instead.
     *
     * @return a list of queued build jobs
     */
    public List<BuildJobQueueItem> getQueuedJobs() {
        // NOTE: we should not use streams with IQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedBuildJobQueue().getAll();
    }

    /**
     * @return the size of the queued jobs
     */
    public int getQueuedJobsSize() {
        return getDistributedBuildJobQueue().size();
    }

    /**
     * This method is used to get the distributed map of processing jobs. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getProcessingJobs()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of processing jobs
     */
    public DistributedMap<String, BuildJobQueueItem> getDistributedProcessingJobs() {
        if (this.processingJobs == null) {
            this.processingJobs = this.distributedDataProvider.getMap("processingJobs");
        }
        return this.processingJobs;
    }

    /**
     * This method is used to get a List containing all processing build jobs. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedProcessingJobs()} instead.
     *
     * @return a list of processing build jobs
     */
    public List<BuildJobQueueItem> getProcessingJobs() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(getDistributedProcessingJobs().values());
    }

    /**
     * @return the size of the processing jobs
     */
    public int getProcessingJobsSize() {
        return getDistributedProcessingJobs().size();
    }

    /**
     * @return a list of processing job ids
     */
    public List<String> getProcessingJobIds() {
        // NOTE: we should not use streams with IMap, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(getDistributedProcessingJobs().keySet());
    }

    /**
     * This method is used to get the distributed queue of build results. This should only be used in special cases like writing to the queue or adding a listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getBuildResultQueue()} method.
     * The queue is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed queue of build results
     */
    public DistributedQueue<ResultQueueItem> getDistributedBuildResultQueue() {
        if (this.buildResultQueue == null) {
            this.buildResultQueue = this.distributedDataProvider.getQueue("buildResultQueue");
        }
        return this.buildResultQueue;
    }

    /**
     * This method is used to get a List containing all build results. This should be used for reading the queue.
     * If you want to write to the queue or add a listener, use {@link DistributedDataAccessService#getDistributedBuildResultQueue()} instead.
     *
     * @return a list of build results
     */
    public List<ResultQueueItem> getBuildResultQueue() {
        // NOTE: we should not use streams with DistributedQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network
        // condition
        return getDistributedBuildResultQueue().getAll();
    }

    /**
     * @return the size of the result queue
     */
    public int getResultQueueSize() {
        return getDistributedBuildResultQueue().size();
    }

    /**
     * @return a list of result queue ids
     */
    public List<String> getResultQueueIds() {
        // stream is ok, because we use the converted version as list
        return getBuildResultQueue().stream().map(item -> item.buildJobQueueItem().id()).toList();
    }

    /**
     * This method is used to get the distributed map of build agent information. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getBuildAgentInformation()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of build agent information
     */
    public DistributedMap<String, BuildAgentInformation> getDistributedBuildAgentInformation() {
        if (this.buildAgentInformation == null) {
            this.buildAgentInformation = this.distributedDataProvider.getMap("buildAgentInformation");
        }
        return this.buildAgentInformation;
    }

    /**
     * This method is used to get a Map containing all build agent information. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedBuildAgentInformation()} instead.
     *
     * @return a map of build agent information
     */
    public Map<String, BuildAgentInformation> getBuildAgentInformationMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedBuildAgentInformation().getMapCopy();
    }

    /**
     * This method is used to get a List containing all build agent information. This should be used for reading/iterating over the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedBuildAgentInformation()} instead.
     * On core nodes (data members), this filters out disconnected build agents using Hazelcast's client tracking.
     *
     * @return a list of active build agent information (excludes disconnected agents on core nodes)
     */
    public List<BuildAgentInformation> getBuildAgentInformation() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        List<BuildAgentInformation> allAgents = new ArrayList<>(getDistributedBuildAgentInformation().values());

        // Get connected client names from Hazelcast (only available on core nodes)
        Set<String> connectedClients = distributedDataProvider.getConnectedClientNames();

        // If we can't get connected clients (running as client or not supported), return all agents
        // This happens on build agent nodes and when using local/Redis providers
        if (connectedClients.isEmpty()) {
            return allAgents;
        }

        // Filter to only include agents that are currently connected
        // The build agent short name (map key) matches the Hazelcast client instance name
        return allAgents.stream().filter(agent -> connectedClients.contains(agent.buildAgent().name())).toList();
    }

    /**
     * @return the size of the build agent information
     */
    public int getBuildAgentInformationSize() {
        return getDistributedBuildAgentInformation().size();
    }

    /**
     * This method is used to get the distributed map of docker image cleanup info. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getDockerImageCleanupInfoMap()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of docker image cleanup info
     */
    public DistributedMap<String, ZonedDateTime> getDistributedDockerImageCleanupInfo() {
        if (this.dockerImageCleanupInfo == null) {
            this.dockerImageCleanupInfo = this.distributedDataProvider.getMap("dockerImageCleanupInfo");
        }
        return this.dockerImageCleanupInfo;
    }

    /**
     * This method is used to get a Map containing all docker image cleanup info. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedDockerImageCleanupInfo()} instead.
     *
     * @return a map of docker image cleanup info
     */
    public Map<String, ZonedDateTime> getDockerImageCleanupInfoMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new HashMap<>(getDistributedDockerImageCleanupInfo().getMapCopy());
    }

    /**
     * @return ITopic for canceled build jobs
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public DistributedTopic<String> getCanceledBuildJobsTopic() {
        if (this.canceledBuildJobsTopic == null) {
            this.canceledBuildJobsTopic = this.distributedDataProvider.getTopic("canceledBuildJobsTopic");
        }
        return this.canceledBuildJobsTopic;
    }

    /**
     * @return ITopic for pausing build agents
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public DistributedTopic<String> getPauseBuildAgentTopic() {
        if (this.pauseBuildAgentTopic == null) {
            this.pauseBuildAgentTopic = this.distributedDataProvider.getTopic("pauseBuildAgentTopic");
        }
        return this.pauseBuildAgentTopic;
    }

    /**
     * @return ITopic for resuming build agents
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public DistributedTopic<String> getResumeBuildAgentTopic() {
        if (this.resumeBuildAgentTopic == null) {
            this.resumeBuildAgentTopic = this.distributedDataProvider.getTopic("resumeBuildAgentTopic");
        }
        return this.resumeBuildAgentTopic;
    }

    /**
     * @param courseId the course id
     * @return a list of the queued jobs for a specific course
     */
    public List<BuildJobQueueItem> getQueuedJobsForCourse(long courseId) {
        return getQueuedJobs().stream().filter(job -> job.courseId() == courseId).toList();
    }

    /**
     * @param courseId the course id
     * @return a list of the processing jobs for a specific course
     */
    public List<BuildJobQueueItem> getProcessingJobsForCourse(long courseId) {
        return getProcessingJobs().stream().filter(job -> job.courseId() == courseId).toList();
    }

    /**
     * @param memberAddress the build agent to retrieve job IDs for
     * @return a list of the processing job IDs on a specific build agent
     */
    public List<BuildJobQueueItem> getProcessingJobsForAgent(String memberAddress) {
        return getProcessingJobs().stream().filter(job -> job.buildAgent().memberAddress().equals(memberAddress)).toList();
    }

    /**
     * @param memberAddress the build agent to retrieve job IDs for
     * @return a list of the processing job IDs on a specific build agent
     */
    public List<String> getProcessingJobIdsForAgent(String memberAddress) {
        return getProcessingJobsForAgent(memberAddress).stream().map(BuildJobQueueItem::id).toList();
    }

    /**
     * @param participationId the participation id
     * @return a list of the queued jobs for a specific participation
     */
    public List<BuildJobQueueItem> getQueuedJobsForParticipation(long participationId) {
        return getQueuedJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    /**
     * @param participationId the participation id
     * @return a list of the processing jobs for a specific participation
     */
    public List<BuildJobQueueItem> getProcessingJobsForParticipation(long participationId) {
        return getProcessingJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    /**
     * Checks if the instance is active and operational.
     *
     * @return {@code true} if the instance has been initialized and is actively running,
     *         {@code false} if the instance has not been initialized or is no longer running
     */
    public boolean isInstanceRunning() {
        return distributedDataProvider.isInstanceRunning();
    }

    /**
     * @return the address of the local member
     */
    public String getLocalMemberAddress() {
        return distributedDataProvider.getLocalMemberAddress();
    }

    /**
     * Retrieves the addresses of all members in the cluster.
     *
     * @return a set of addresses of all cluster members
     */
    public Set<String> getClusterMemberAddresses() {
        return distributedDataProvider.getClusterMemberAddresses();
    }

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return {@code true} if all members in the cluster are lite members (i.e., no data members are available),
     */
    public boolean noDataMemberInClusterAvailable() {
        return distributedDataProvider.noDataMemberInClusterAvailable();
    }

    /**
     * Checks if the distributed data provider is connected and ready to use.
     * For cluster members (core nodes), this is equivalent to isInstanceRunning().
     * For clients (build agents with asyncStart=true), this checks if the client
     * has established a connection to at least one cluster member.
     *
     * <p>
     * This is important for async-start clients that may be running but not yet
     * connected to the cluster. Operations on distributed objects will fail until
     * the client is connected.
     *
     * @return true if the instance is connected and ready to use, false otherwise
     */
    public boolean isConnectedToCluster() {
        return distributedDataProvider.isConnectedToCluster();
    }

    /**
     * Retrieves the build agent status for a specific agent by its key.
     *
     * @param agentKey the key identifying the build agent (typically the short name)
     * @return the status of the build agent, or {@code null} if the agent is not registered
     */
    @Nullable
    public BuildAgentStatus getBuildAgentStatus(String agentKey) {
        BuildAgentInformation agentInfo = getDistributedBuildAgentInformation().get(agentKey);
        if (agentInfo == null) {
            return null;
        }
        return agentInfo.status();
    }

    /**
     * Registers a callback that will be invoked when the client connects or reconnects to the cluster.
     * This is important for re-registering listeners on distributed objects after a connection loss.
     *
     * <p>
     * The callback receives a boolean indicating whether this is the initial connection (true)
     * or a reconnection after disconnection (false). Services can use this to differentiate
     * between first-time setup and re-initialization after connection loss.
     *
     * @param callback a consumer that receives true for initial connection, false for reconnection
     * @return a unique identifier that can be used to remove the listener later
     */
    public UUID addConnectionStateListener(Consumer<Boolean> callback) {
        return distributedDataProvider.addConnectionStateListener(callback);
    }

    /**
     * Removes a previously registered connection state listener.
     *
     * @param listenerId the unique identifier returned by {@link #addConnectionStateListener}
     * @return true if the listener was found and removed, false otherwise
     */
    public boolean removeConnectionStateListener(UUID listenerId) {
        return distributedDataProvider.removeConnectionStateListener(listenerId);
    }
}
