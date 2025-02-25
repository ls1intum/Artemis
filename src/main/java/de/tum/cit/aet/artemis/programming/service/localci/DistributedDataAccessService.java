package de.tum.cit.aet.artemis.programming.service.localci;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_BUILDAGENT;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_LOCALCI;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.hazelcast.collection.IQueue;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;
import com.hazelcast.topic.ITopic;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.ResultQueueItem;

@Service
@Profile({ PROFILE_LOCALCI, PROFILE_BUILDAGENT })
public class DistributedDataAccessService {

    private final HazelcastInstance hazelcastInstance;

    private IQueue<BuildJobQueueItem> queue;

    private IMap<String, BuildJobQueueItem> processingJobs;

    private IQueue<ResultQueueItem> resultQueue;

    private IMap<String, BuildAgentInformation> buildAgentInformation;

    private IMap<String, ZonedDateTime> dockerImageCleanupInfo;

    private ITopic<String> canceledBuildJobsTopic;

    private ITopic<String> pauseBuildAgentTopic;

    private ITopic<String> resumeBuildAgentTopic;

    public DistributedDataAccessService(@Qualifier("hazelcastInstance") HazelcastInstance hazelcastInstance) {
        this.hazelcastInstance = hazelcastInstance;
    }

    /**
     * Initialize relevant data from hazelcast. We use @PostConstruct instead of @EventListener(ApplicationReadyEvent.class) to ensure that the initialization is done before other
     * services can use this service.
     */
    @PostConstruct
    public void init() {
        this.buildAgentInformation = this.hazelcastInstance.getMap("buildAgentInformation");
        this.processingJobs = this.hazelcastInstance.getMap("processingJobs");
        this.queue = this.hazelcastInstance.getQueue("buildJobQueue");
        this.canceledBuildJobsTopic = hazelcastInstance.getTopic("canceledBuildJobsTopic");
        this.dockerImageCleanupInfo = this.hazelcastInstance.getMap("dockerImageCleanupInfo");
        this.pauseBuildAgentTopic = hazelcastInstance.getTopic("pauseBuildAgentTopic");
        this.resumeBuildAgentTopic = hazelcastInstance.getTopic("resumeBuildAgentTopic");
        this.resultQueue = this.hazelcastInstance.getQueue("buildResultQueue");
    }

    /**
     * This method is used to get the distributed queue of build jobs. This should only be used in special cases like writing to the queue or adding a listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getQueuedJobs()} method.
     *
     * @return the distributed queue of build jobs.
     */
    public IQueue<BuildJobQueueItem> getDistributedQueuedJobs() {
        return this.queue;
    }

    /**
     * This method is used to get a List containing all queued build jobs. This should be used for reading the queue.
     * If you want to write to the queue or add a listener, use {@link DistributedDataAccessService#getDistributedQueuedJobs()} instead.
     *
     * @return a list of queued build jobs
     */
    public List<BuildJobQueueItem> getQueuedJobs() {
        // NOTE: we should not use streams with IQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(this.queue);
    }

    /**
     * @return the size of the queued jobs
     */
    public int getQueuedJobsSize() {
        return queue.size();
    }

    /**
     * This method is used to get the distributed map of processing jobs. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getProcessingJobs()} method.
     *
     * @return the distributed map of processing jobs
     */
    public IMap<String, BuildJobQueueItem> getDistributedProcessingJobs() {
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
        return new ArrayList<>(this.processingJobs.values());
    }

    /**
     * @return the size of the processing jobs
     */
    public int getProcessingJobsSize() {
        return processingJobs.size();
    }

    /**
     * @return a list of processing job ids
     */
    public List<String> getProcessingJobIds() {
        // NOTE: we should not use streams with IMap, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(this.processingJobs.keySet());
    }

    /**
     * This method is used to get the distributed queue of build results. This should only be used in special cases like writing to the queue or adding a listener.
     * In general, the queue should be accessed via the {@link DistributedDataAccessService#getResultQueue()} method.
     *
     * @return the distributed queue of build results
     */
    public IQueue<ResultQueueItem> getDistributedResultQueue() {
        return this.resultQueue;
    }

    /**
     * This method is used to get a List containing all build results. This should be used for reading the queue.
     * If you want to write to the queue or add a listener, use {@link DistributedDataAccessService#getDistributedResultQueue()} instead.
     *
     * @return a list of build results
     */
    public List<ResultQueueItem> getResultQueue() {
        // NOTE: we should not use streams with IQueue directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(this.resultQueue);
    }

    /**
     * @return the size of the result queue
     */
    public int getResultQueueSize() {
        return resultQueue.size();
    }

    /**
     * @return a list of result queue ids
     */
    public List<String> getResultQueueIds() {
        return getResultQueue().stream().map(i -> i.buildJobQueueItem().id()).toList();
    }

    /**
     * This method is used to get the distributed map of build agent information. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getBuildAgentInformation()} method.
     *
     * @return the distributed map of build agent information
     */
    public IMap<String, BuildAgentInformation> getDistributedBuildAgentInformation() {
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
        return new HashMap<>(this.buildAgentInformation);
    }

    /**
     * This method is used to get a List containing all build agent information. This should be used for reading/iterating over the map.
     * If you want to write to the map or add a listener, use {@link DistributedDataAccessService#getDistributedBuildAgentInformation()} instead.
     *
     * @return a list of build agent information
     */
    public List<BuildAgentInformation> getBuildAgentInformation() {
        // NOTE: we should not use streams with IMap directly, because it can be unstable, when many items are added at the same time and there is a slow network condition
        return new ArrayList<>(this.buildAgentInformation.values());
    }

    /**
     * @return the size of the build agent information
     */
    public int getBuildAgentInformationSize() {
        return buildAgentInformation.size();
    }

    /**
     * This method is used to get the distributed map of docker image cleanup info. This should only be used in special cases like writing to the map or adding a listener.
     * In general, the map should be accessed via the {@link DistributedDataAccessService#getDockerImageCleanupInfoMap()} method.
     *
     * @return the distributed map of docker image cleanup info
     */
    public IMap<String, ZonedDateTime> getDistributedDockerImageCleanupInfo() {
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
        return new HashMap<>(this.dockerImageCleanupInfo);
    }

    /**
     * @return ITopic for canceled build jobs
     */
    public ITopic<String> getCanceledBuildJobsTopic() {
        return this.canceledBuildJobsTopic;
    }

    /**
     * @return ITopic for pausing build agents
     */
    public ITopic<String> getPauseBuildAgentTopic() {
        return this.pauseBuildAgentTopic;
    }

    /**
     * @return ITopic for resuming build agents
     */
    public ITopic<String> getResumeBuildAgentTopic() {
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
     * @param participationId the participation id
     * @return a list of the queued jobs for a specific participation
     */
    public List<BuildJobQueueItem> getQueuedJobsForParticipation(long participationId) {
        return getProcessingJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    /**
     * @param participationId the participation id
     * @return a list of the processing jobs for a specific participation
     */
    public List<BuildJobQueueItem> getProcessingJobsForParticipation(long participationId) {
        return getProcessingJobs().stream().filter(job -> job.participationId() == participationId).toList();
    }

    /**
     * @return a list of the build agents with the unnecessary information removed
     */
    public List<BuildAgentInformation> getBuildAgentInformationWithoutRecentBuildJobs() {
        return getBuildAgentInformation().stream().map(agent -> new BuildAgentInformation(agent.buildAgent(), agent.maxNumberOfConcurrentBuildJobs(),
                agent.numberOfCurrentBuildJobs(), agent.runningBuildJobs(), agent.status(), null, null)).toList();
    }
}
