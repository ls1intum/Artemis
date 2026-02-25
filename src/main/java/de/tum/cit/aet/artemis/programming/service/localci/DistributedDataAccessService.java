package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.Instant;
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
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.jwk.JWK;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentStatus;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;
import de.tum.cit.aet.artemis.core.dto.passkey.PublicKeyCredentialCreationOptionsDTO;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.iris.service.pyris.job.PyrisJob;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.DistributedDataProvider;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.map.DistributedMap;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.queue.DistributedQueue;
import de.tum.cit.aet.artemis.programming.service.localci.distributed.api.set.DistributedSet;
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

    private DistributedMap<Feature, Boolean> features;

    private DistributedSet<Long> activePlagiarismChecksPerCourse;

    private DistributedMap<String, PyrisJob> pyrisJobMap;

    private DistributedMap<String, PublicKeyCredentialCreationOptionsDTO> passkeyCreationOptionsMap;

    private DistributedMap<String, JWK> clientRegistrationIdToJwk;

    private DistributedMap<String, OAuth2AuthorizationRequest> ltiOAuth2AuthorizationRequestMap;

    private DistributedMap<String, PublicKeyCredentialRequestOptions> passkeyAuthOptionsMap;

    private DistributedMap<String, Instant> participationTeamLastTypingTracker;

    private DistributedMap<String, Instant> participationTeamLastActionTracker;

    private DistributedMap<String, String> participationTeamDestinationTracker;

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
     * <p>
     * Important: The returned BuildAgentInformation objects are enriched with the CURRENT processing jobs
     * from the distributed processing jobs map. This ensures the runningBuildJobs list and numberOfCurrentBuildJobs
     * are always accurate, even if the build agent hasn't updated its information recently.
     *
     * @return a list of active build agent information (excludes disconnected agents on core nodes)
     */
    public List<BuildAgentInformation> getBuildAgentInformation() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        List<BuildAgentInformation> allAgents = new ArrayList<>(getDistributedBuildAgentInformation().values());

        // Get current processing jobs to enrich agent information with accurate running jobs data
        List<BuildJobQueueItem> currentProcessingJobs = getProcessingJobs();

        // Get connected client names from Hazelcast (only available on core nodes)
        Set<String> connectedClients = distributedDataProvider.getConnectedClientNames();

        // Enrich and filter agents
        return allAgents.stream()
                // Guard against null entries from distributed map
                .filter(agent -> agent != null && agent.buildAgent() != null)
                // Filter to only connected agents if we can determine connectivity
                .filter(agent -> connectedClients.isEmpty() || connectedClients.contains(agent.buildAgent().name()))
                // Enrich with current processing jobs for accurate runningBuildJobs data
                .map(agent -> enrichWithCurrentProcessingJobs(agent, currentProcessingJobs)).toList();
    }

    /**
     * Enriches a BuildAgentInformation with the current processing jobs from the distributed map.
     * This ensures that the runningBuildJobs list and numberOfCurrentBuildJobs are always accurate,
     * even if the build agent hasn't updated its information recently.
     *
     * @param agent                 the build agent information to enrich
     * @param currentProcessingJobs the current list of all processing jobs
     * @return a new BuildAgentInformation with accurate running jobs data
     */
    private BuildAgentInformation enrichWithCurrentProcessingJobs(BuildAgentInformation agent, List<BuildJobQueueItem> currentProcessingJobs) {
        // Filter processing jobs for this specific agent
        List<BuildJobQueueItem> agentJobs = currentProcessingJobs.stream().filter(job -> job.buildAgent() != null && job.buildAgent().name().equals(agent.buildAgent().name()))
                .toList();

        int currentJobCount = agentJobs.size();

        // Determine accurate status based on current job count
        BuildAgentStatus status = agent.status();
        if (status != BuildAgentStatus.PAUSED && status != BuildAgentStatus.SELF_PAUSED) {
            status = currentJobCount > 0 ? BuildAgentStatus.ACTIVE : BuildAgentStatus.IDLE;
        }

        // Return enriched agent info with current processing jobs
        return new BuildAgentInformation(agent.buildAgent(), agent.maxNumberOfConcurrentBuildJobs(), currentJobCount, agentJobs, status, agent.publicSshKey(),
                agent.buildAgentDetails(), agent.pauseAfterConsecutiveBuildFailures());
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
     * @param memberAddress the build agent member address to retrieve jobs for
     * @return a list of the processing jobs on a specific build agent by member address
     */
    public List<BuildJobQueueItem> getProcessingJobsForAgent(String memberAddress) {
        return getProcessingJobs().stream().filter(job -> job.buildAgent() != null && job.buildAgent().memberAddress().equals(memberAddress)).toList();
    }

    /**
     * @param agentName the build agent name (short name) to retrieve jobs for
     * @return a list of the processing jobs on a specific build agent by name
     */
    public List<BuildJobQueueItem> getProcessingJobsForAgentByName(String agentName) {
        return getProcessingJobs().stream().filter(job -> job.buildAgent() != null && job.buildAgent().name().equals(agentName)).toList();
    }

    /**
     * @param agentName the build agent name (short name) to retrieve job IDs for
     * @return a list of the processing job IDs on a specific build agent by name
     */
    public List<String> getProcessingJobIdsForAgentByName(String agentName) {
        return getProcessingJobsForAgentByName(agentName).stream().map(BuildJobQueueItem::id).toList();
    }

    /**
     * @param memberAddress the build agent member address to retrieve job IDs for
     * @return a list of the processing job IDs on a specific build agent by member address
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

    /**
     * Registers a callback that will be invoked when a client (build agent) disconnects from the cluster.
     * This is only available on data members (core nodes), not on clients (build agents).
     * On clients, this method returns null and the callback is never invoked.
     *
     * <p>
     * This is important for cleaning up stale data when build agents crash or disconnect
     * unexpectedly. The callback receives the client name (build agent short name) that disconnected.
     *
     * @param callback a consumer that receives the disconnected client's name
     * @return a unique identifier that can be used to remove the listener later, or null if not supported
     */
    public UUID addClientDisconnectionListener(Consumer<String> callback) {
        return distributedDataProvider.addClientDisconnectionListener(callback);
    }

    /**
     * Removes a previously registered client disconnection listener.
     *
     * @param listenerId the unique identifier returned by {@link #addClientDisconnectionListener}
     * @return true if the listener was found and removed, false otherwise
     */
    public boolean removeClientDisconnectionListener(UUID listenerId) {
        return distributedDataProvider.removeClientDisconnectionListener(listenerId);
    }

    /**
     * This method is used to get the distributed map of feature toggle. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getFeatures()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of feature toggles
     */
    public DistributedMap<Feature, Boolean> getDistributedFeatures() {
        if (this.features == null) {
            this.features = this.distributedDataProvider.getMap("features");
        }
        return this.features;
    }

    /**
     * This method is used to get a Map containing all feature toggles. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedFeatures()} instead.
     *
     * @return a map of feature toggles
     */
    public Map<Feature, Boolean> getFeatures() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedFeatures().getMapCopy();
    }

    /**
     * This method is used to get the distributed set of active plagiarism checks per course. This should only be used in special cases like writing to the queue or adding a
     * listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getActivePlagiarismChecksPerCourse()} method.
     * The queue is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed set of active plagiarism checks per course.
     */
    public DistributedSet<Long> getDistributedActivePlagiarismChecksPerCourse() {
        if (this.activePlagiarismChecksPerCourse == null) {
            this.activePlagiarismChecksPerCourse = this.distributedDataProvider.getSet(HAZELCAST_ACTIVE_PLAGIARISM_CHECKS_PER_COURSE_CACHE);
        }
        return this.activePlagiarismChecksPerCourse;
    }

    /**
     * This method is used to get a set of active plagiarism checks per course. This should be used for reading the queue.
     * If you want to write to the queue or add a listener, use {@link DistributedDataAccessService#getDistributedActivePlagiarismChecksPerCourse()} instead.
     *
     * @return a set of active plagiarism checks per course
     */
    public Set<Long> getActivePlagiarismChecksPerCourse() {
        // NOTE: we should not use streams with IQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedActivePlagiarismChecksPerCourse().getSetCopy();
    }

    /**
     * This method is used to get the distributed map of pyris jobs. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getPyrisJobMap()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of pyris jobs
     */
    public DistributedMap<String, PyrisJob> getDistributedPyrisJobMap() {
        if (this.pyrisJobMap == null) {
            this.pyrisJobMap = this.distributedDataProvider.getMap("pyris-job-map");
        }
        return this.pyrisJobMap;
    }

    /**
     * This method is used to get a Map containing pyris jobs. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedPyrisJobMap()} instead.
     *
     * @return a map of pyris jobs
     */
    public Map<String, PyrisJob> getPyrisJobMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedPyrisJobMap().getMapCopy();
    }

    /**
     * This method is used to get the distributed map of passkey creation options. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getPasskeyCreationOptionsMap()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of passkey creation options
     */
    public DistributedMap<String, PublicKeyCredentialCreationOptionsDTO> getDistributedPasskeyCreationOptionsMap() {
        if (this.passkeyCreationOptionsMap == null) {
            this.passkeyCreationOptionsMap = this.distributedDataProvider.getMap("http-session-public-key-credential-creation-options-map");
        }
        return this.passkeyCreationOptionsMap;
    }

    /**
     * This method is used to get a Map containing passkey creation options. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedPasskeyCreationOptionsMap()} instead.
     *
     * @return a map of passkey creation options
     */
    public Map<String, PublicKeyCredentialCreationOptionsDTO> getPasskeyCreationOptionsMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedPasskeyCreationOptionsMap().getMapCopy();
    }

    /**
     * This method is used to get the distributed map of passkey creation options. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getClientRegistrationIdToJwk()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of passkey creation options
     */
    public DistributedMap<String, JWK> getDistributedClientRegistrationIdToJwk() {
        if (this.clientRegistrationIdToJwk == null) {
            this.clientRegistrationIdToJwk = this.distributedDataProvider.getMap("ltiJwkMap");
        }
        return this.clientRegistrationIdToJwk;
    }

    /**
     * This method is used to get a Map containing passkey creation options. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedClientRegistrationIdToJwk()} instead.
     *
     * @return a map of passkey creation options
     */
    public Map<String, JWK> getClientRegistrationIdToJwk() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedClientRegistrationIdToJwk().getMapCopy();
    }

    /**
     * This method is used to get the distributed map of LTI OAuth2 authorization requests. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getLtiOAuth2AuthorizationRequestMap()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of LTI OAuth2 authorization requests
     */
    public DistributedMap<String, OAuth2AuthorizationRequest> getDistributedLtiOAuth2AuthorizationRequestMap() {
        if (this.ltiOAuth2AuthorizationRequestMap == null) {
            this.ltiOAuth2AuthorizationRequestMap = this.distributedDataProvider.getMap("ltiStateAuthorizationRequestStore");
        }
        return this.ltiOAuth2AuthorizationRequestMap;
    }

    /**
     * This method is used to get a Map containing LTI OAuth2 authorization requests. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedLtiOAuth2AuthorizationRequestMap()} instead.
     *
     * @return a map of LTI OAuth2 authorization requests
     */
    public Map<String, OAuth2AuthorizationRequest> getLtiOAuth2AuthorizationRequestMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedLtiOAuth2AuthorizationRequestMap().getMapCopy();
    }

    /**
     * This method is used to get the distributed map of passkey auth options. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getPasskeyAuthOptionsMap()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of passkey auth options
     */
    public DistributedMap<String, PublicKeyCredentialRequestOptions> getDistributedPasskeyAuthOptionsMap() {
        if (this.passkeyAuthOptionsMap == null) {
            this.passkeyAuthOptionsMap = this.distributedDataProvider.getMap("public-key-credentials-request-options-map");
        }
        return this.passkeyAuthOptionsMap;
    }

    /**
     * This method is used to get a Map containing passkey auth options. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedPasskeyAuthOptionsMap()} instead.
     *
     * @return a map of passkey auth options
     */
    public Map<String, PublicKeyCredentialRequestOptions> getPasskeyAuthOptionsMap() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedPasskeyAuthOptionsMap().getMapCopy();
    }

    /**
     * Returns the last typing tracker map which keeps track of the last typing date for each user in a participation.
     * This is used to determine which team members are currently typing.
     * This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getParticipationTeamLastTypingTracker()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of last typing trackers
     */
    public DistributedMap<String, Instant> getDistributedParticipationTeamLastTypingTracker() {
        if (this.participationTeamLastTypingTracker == null) {
            this.participationTeamLastTypingTracker = this.distributedDataProvider.getMap("lastTypingTracker");
        }
        return this.participationTeamLastTypingTracker;
    }

    /**
     * This method is used to get a Map containing last typing trackers. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedParticipationTeamLastTypingTracker()} instead.
     *
     * @return a map of last typing trackers
     */
    public Map<String, Instant> getParticipationTeamLastTypingTracker() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedParticipationTeamLastTypingTracker().getMapCopy();
    }

    /**
     * Returns the last action tracker map which keeps track of the last action date for each user in a participation.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     * This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getParticipationTeamLastActionTracker()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of last action trackers
     */
    public DistributedMap<String, Instant> getDistributedParticipationTeamLastActionTracker() {
        if (this.participationTeamLastActionTracker == null) {
            this.participationTeamLastActionTracker = this.distributedDataProvider.getMap("lastActionTracker");
        }
        return this.participationTeamLastActionTracker;
    }

    /**
     * This method is used to get a Map containing last action trackers. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedParticipationTeamLastActionTracker()} instead.
     *
     * @return a map of last action trackers
     */
    public Map<String, Instant> getParticipationTeamLastActionTracker() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedParticipationTeamLastActionTracker().getMapCopy();
    }

    // TODO

    /**
     * Returns the destination tracker map which keeps track of the destination that each session is subscribed to.
     * This is used to send out the list of online team members when a user subscribes or unsubscribes.
     * This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getParticipationTeamDestinationTracker()} method.
     * The map is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed map of destination trackers
     */
    public DistributedMap<String, String> getDistributedParticipationTeamDestinationTracker() {
        if (this.participationTeamDestinationTracker == null) {
            this.participationTeamDestinationTracker = this.distributedDataProvider.getMap("public-key-credentials-request-options-map");
        }
        return this.participationTeamDestinationTracker;
    }

    /**
     * This method is used to get a Map containing destination trackers. This should be used for reading the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedParticipationTeamDestinationTracker()} instead.
     *
     * @return a map of destination trackers
     */
    public Map<String, String> getParticipationTeamDestinationTracker() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return getDistributedParticipationTeamDestinationTracker().getMapCopy();
    }

    // TODO

    public DistributedMap<Object, Object> getDistributedMap(String mapName) {
        return this.distributedDataProvider.getMap(mapName);
    }

    public Map<Object, Object> getMap(String mapName) {
        return getDistributedMap(mapName).getMapCopy();
    }

    public <T> DistributedTopic<T> getDistributedTopic(String topicName) {
        return this.distributedDataProvider.getTopic(topicName);
    }
}
