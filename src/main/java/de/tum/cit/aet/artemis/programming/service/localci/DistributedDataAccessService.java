package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.annotation.Nullable;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.cluster.Member;
import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.HazelcastInstanceNotActiveException;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;

/**
 * This service is used to access the distributed data structures in Hazelcast.
 * All data structures are created lazily, meaning they are only created when they are first accessed.
 */
@Lazy
@Service
@Profile({ PROFILE_LOCALCI, PROFILE_BUILDAGENT })
public class DistributedDataAccessService {

    private final HazelcastInstance hazelcastInstance;

    private IQueue<BuildJobQueueItem> buildJobQueue;

    private IMap<String, BuildJobQueueItem> processingJobs;

    private IQueue<ResultQueueItem> buildResultQueue;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    private IMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private ITopic<String> canceledBuildJobsTopic;

    private ITopic<String> pauseBuildAgentTopic;

    private ITopic<String> resumeBuildAgentTopic;

    public DistributedDataAccessService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * This method is used to get the distributed queue of build jobs. This should only be used in special cases like writing to the queue or adding a listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getQueuedJobs()} method.
     * The queue is initialized lazily the first time this method is called if it is still null.
     *
     * @return the distributed queue of build jobs.
     */
    public IQueue<BuildJobQueueItem> getDistributedBuildJobQueue() {
        if (this.buildJobQueue == null) {
            this.buildJobQueue = this.hazelcastInstance.getQueue("buildJobQueue");
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
        return new ArrayList<>(getDistributedBuildJobQueue());
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
    public IMap<String, BuildJobQueueItem> getDistributedProcessingJobs() {
        if (this.processingJobs == null) {
            this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
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
    public IQueue<ResultQueueItem> getDistributedBuildResultQueue() {
        if (this.buildResultQueue == null) {
            this.buildResultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
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
        // NOTE: we should not use streams with IQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(getDistributedBuildResultQueue());
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
    public IMap<String, BuildAgentInformation> getDistributedBuildAgentInformation() {
        if (this.buildAgentInformation == null) {
            this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
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
        return new HashMap<>(getDistributedBuildAgentInformation());
    }

    /**
     * This method is used to get a List containing all build agent information. This should be used for reading/iterating over the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedBuildAgentInformation()} instead.
     *
     * @return a list of build agent information
     */
    public List<BuildAgentInformation> getBuildAgentInformation() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(getDistributedBuildAgentInformation().values());
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
    public IMap<String, ZonedDateTime> getDistributedDockerImageCleanupInfo() {
        if (this.dockerImageCleanupInfo == null) {
            this.dockerImageCleanupInfo = this.hazelcastInstance.getMap("dockerImageCleanupInfo");
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
        return new HashMap<>(getDistributedDockerImageCleanupInfo());
    }

    /**
     * @return ITopic for canceled build jobs
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public ITopic<String> getCanceledBuildJobsTopic() {
        if (this.canceledBuildJobsTopic == null) {
            this.canceledBuildJobsTopic = this.hazelcastInstance.getTopic("canceledBuildJobsTopic");
        }
        return this.canceledBuildJobsTopic;
    }

    /**
     * @return ITopic for pausing build agents
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public ITopic<String> getPauseBuildAgentTopic() {
        if (this.pauseBuildAgentTopic == null) {
            this.pauseBuildAgentTopic = this.hazelcastInstance.getTopic("pauseBuildAgentTopic");
        }
        return this.pauseBuildAgentTopic;
    }

    /**
     * @return ITopic for resuming build agents
     *         The topic is initialized lazily the first time this method is called if it is still null.
     */
    public ITopic<String> getResumeBuildAgentTopic() {
        if (this.resumeBuildAgentTopic == null) {
            this.resumeBuildAgentTopic = this.hazelcastInstance.getTopic("resumeBuildAgentTopic");
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
     * Checks if the Hazelcast instance is active and operational.
     *
     * @return {@code true} if the Hazelcast instance has been initialized and is actively running,
     *         {@code false} if the instance has not been initialized or is no longer running
     */
    public boolean isInstanceRunning() {
        return hazelcastInstance != null && hazelcastInstance.getLifecycleService().isRunning();
    }

    /**
     * @return the address of the local Hazelcast member
     */
    public String getLocalMemberAddress() {
        if (!isInstanceRunning()) {
            throw new HazelcastInstanceNotActiveException();
        }
        return hazelcastInstance.getCluster().getLocalMember().getAddress().toString();
    }

    /**
     * Retrieves the members of the Hazelcast cluster.
     *
     * @return a stream of Hazelcast cluster members
     */
    public Stream<Member> getClusterMembers() {
        if (!isInstanceRunning()) {
            return Stream.empty();
        }
        return hazelcastInstance.getCluster().getMembers().stream();
    }

    /**
     * Retrieves the addresses of all members in the Hazelcast cluster.
     *
     * @return a set of addresses of all cluster members
     */
    public Set<String> getClusterMemberAddresses() {
        return getClusterMembers().map(Member::getAddress).map(Object::toString).collect(Collectors.toSet());
    }

    /**
     * Checks if there are no data members available in the cluster.
     *
     * @return {@code true} if all members in the cluster are lite members (i.e., no data members are available),
     */
    public boolean noDataMemberInClusterAvailable() {
        return getClusterMembers().allMatch(Member::isLiteMember);
    }

    /**
     * Retrieves the build agent status for the local member.
     *
     * @return the status of the local build agent, or {@code null} if the local member is not registered as a build agent
     */
    @Nullable
    public BuildAgentInformation.BuildAgentStatus getLocalBuildAgentStatus() {
        try {
            BuildAgentInformation localAgentInfo = getDistributedBuildAgentInformation().get(getLocalMemberAddress());
            if (localAgentInfo == null) {
                return null;
            }
            return localAgentInfo.status();
        }
        catch (HazelcastInstanceNotActiveException e) {
            return null;
        }
    }
}
