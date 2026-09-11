package de.tum.cit.aet.artemis.localci.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.buildagent.dto.BuildAgentInformation;
import de.tum.cit.aet.artemis.buildagent.dto.BuildConfig;
import de.tum.cit.aet.artemis.buildagent.dto.BuildJobQueueItem;
import de.tum.cit.aet.artemis.buildagent.dto.FinishedBuildJobDTO;
import de.tum.cit.aet.artemis.buildagent.dto.RepositoryInfo;

/**
 * This service is responsible for sending build job queue information over websockets.
 * It listens to changes in the build job queue and sends the updated information to the client.
 * NOTE: This service is only active if the profile "localci" and "scheduling" are active. This avoids sending the
 * same information multiple times and thus also avoids unnecessary load on the server.
 */
@Lazy
@Service
@Profile("localci & scheduling")
public class LocalCIQueueWebsocketService {

    private static final Logger log = LoggerFactory.getLogger(LocalCIQueueWebsocketService.class);

    private final LocalCIWebsocketMessagingService localCIWebsocketMessagingService;

    private final DistributedDataAccessService distributedDataAccessService;

    /**
     * Who is currently subscribed to what, across the cluster.
     * <p>
     * The broker relay is configured with {@code setUserRegistryBroadcast}, so Spring aggregates the registries of all
     * nodes here rather than only this one's - which matters because the node that broadcasts the queue is the
     * scheduling node, and the admin watching the queue page is usually connected to another one. The remote half is
     * refreshed periodically, so a new subscriber becomes visible within a few seconds rather than instantly. That
     * window costs nothing: the page loads the current queue over REST when it opens, so a change that happened while
     * nobody was listening yet is already in what the new subscriber sees.
     */
    private final SimpUserRegistry simpUserRegistry;

    /**
     * Courses whose queued jobs changed since the last broadcast, and the same for the jobs being processed.
     * <p>
     * Each of these payloads is the whole collection, so broadcasting one per queue change costs a read of the whole
     * distributed collection and a serialization of the whole list - per change, and twice over, once for the admin
     * topic and once for the course topic. During an exam the queue changes several times a second and holds thousands
     * of jobs, which is quadratic in the size of the exam: on a 2000 student benchmark run this was the largest single
     * consumer of CPU on the scheduling node. What the clients render is a live view of the current state, and a view
     * that refreshes once a second is indistinguishable from one that refreshes a hundred times a second, so the
     * changes are collected here and {@link #broadcastPendingChanges()} sends one snapshot per interval instead.
     */
    private final Set<Long> coursesWithQueuedJobChanges = ConcurrentHashMap.newKeySet();

    private final Set<Long> coursesWithProcessingJobChanges = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean buildAgentSummaryNeedsBroadcast = new AtomicBoolean();

    /**
     * Instantiates a new Local ci queue websocket service.
     *
     * @param localCIWebsocketMessagingService the local ci build queue websocket service
     * @param distributedDataAccessService     access to the distributed build job collections
     * @param simpUserRegistry                 the cluster-wide registry of who is subscribed to what
     */
    public LocalCIQueueWebsocketService(LocalCIWebsocketMessagingService localCIWebsocketMessagingService, DistributedDataAccessService distributedDataAccessService,
            SimpUserRegistry simpUserRegistry) {
        this.localCIWebsocketMessagingService = localCIWebsocketMessagingService;
        this.distributedDataAccessService = distributedDataAccessService;
        this.simpUserRegistry = simpUserRegistry;
    }

    /**
     * Records that the queued jobs of a course changed. The next {@link #broadcastPendingChanges()} sends the queue as
     * it stands then, so a burst of changes costs one broadcast rather than one each.
     *
     * @param courseId the course id of the programming exercise related to the job
     */
    void queuedJobsChanged(long courseId) {
        coursesWithQueuedJobChanges.add(courseId);
    }

    /**
     * Records that the jobs being processed for a course changed. Collected the same way as {@link #queuedJobsChanged}.
     *
     * @param courseId the course id of the programming exercise related to the job
     */
    void processingJobsChanged(long courseId) {
        coursesWithProcessingJobChanges.add(courseId);
    }

    /**
     * Sends one snapshot per collection that changed since the last run.
     * <p>
     * Each collection is read and sanitized once per run, not once per affected course, and the admin topic gets one
     * message rather than one per course. A failed run puts the courses it drained back so the next one retries them;
     * without that a broadcast that throws would leave the queue views stale until the next unrelated queue event.
     */
    @Scheduled(fixedRateString = "${artemis.continuous-integration.build-queue-broadcast-interval-milliseconds:1000}")
    public void broadcastPendingChanges() {
        broadcastQueuedJobs();
        broadcastProcessingJobs();
        broadcastBuildAgentSummary();
    }

    private void broadcastQueuedJobs() {
        if (coursesWithQueuedJobChanges.isEmpty()) {
            return;
        }
        Set<Long> courseIds = drain(coursesWithQueuedJobChanges);
        boolean admin = hasSubscribers(LocalCIWebsocketMessagingService.ADMIN_QUEUED_JOBS_TOPIC);
        Set<Long> watched = watchedCourses(courseIds, LocalCIWebsocketMessagingService::queuedJobsTopicForCourse);
        if (!admin && watched.isEmpty()) {
            return;
        }
        try {
            var queuedJobs = removeUnnecessaryInformation(distributedDataAccessService.getQueuedJobs());
            if (admin) {
                localCIWebsocketMessagingService.sendQueuedBuildJobs(queuedJobs);
            }
            for (Long courseId : courseIds) {
                if (watched.contains(courseId)) {
                    localCIWebsocketMessagingService.sendQueuedBuildJobsForCourse(courseId, queuedJobs.stream().filter(job -> job.courseId() == courseId).toList());
                }
            }
        }
        catch (Exception e) {
            coursesWithQueuedJobChanges.addAll(courseIds);
            log.warn("Failed to broadcast the queued build jobs, retrying on the next run", e);
        }
    }

    private void broadcastProcessingJobs() {
        if (coursesWithProcessingJobChanges.isEmpty()) {
            return;
        }
        Set<Long> courseIds = drain(coursesWithProcessingJobChanges);
        boolean admin = hasSubscribers(LocalCIWebsocketMessagingService.ADMIN_RUNNING_JOBS_TOPIC);
        Set<Long> watched = watchedCourses(courseIds, LocalCIWebsocketMessagingService::runningJobsTopicForCourse);
        if (!admin && watched.isEmpty()) {
            return;
        }
        try {
            var processingJobs = removeUnnecessaryInformation(distributedDataAccessService.getProcessingJobs());
            if (admin) {
                localCIWebsocketMessagingService.sendRunningBuildJobs(processingJobs);
            }
            for (Long courseId : courseIds) {
                if (watched.contains(courseId)) {
                    localCIWebsocketMessagingService.sendRunningBuildJobsForCourse(courseId, processingJobs.stream().filter(job -> job.courseId() == courseId).toList());
                }
            }
        }
        catch (Exception e) {
            coursesWithProcessingJobChanges.addAll(courseIds);
            log.warn("Failed to broadcast the running build jobs, retrying on the next run", e);
        }
    }

    private void broadcastBuildAgentSummary() {
        if (!buildAgentSummaryNeedsBroadcast.getAndSet(false) || !hasSubscribers(LocalCIWebsocketMessagingService.ADMIN_BUILD_AGENTS_TOPIC)) {
            return;
        }
        try {
            localCIWebsocketMessagingService.sendBuildAgentSummary(removeUnnecessaryInformationFromBuildAgentInformation(distributedDataAccessService.getBuildAgentInformation()));
        }
        catch (Exception e) {
            buildAgentSummaryNeedsBroadcast.set(true);
            log.warn("Failed to broadcast the build agent summary, retrying on the next run", e);
        }
    }

    /**
     * Whether anyone anywhere in the cluster is subscribed to a destination.
     *
     * @param destination the topic to check
     * @return true if at least one session is subscribed to it
     */
    private boolean hasSubscribers(String destination) {
        return !simpUserRegistry.findSubscriptions(subscription -> destination.equals(subscription.getDestination())).isEmpty();
    }

    /**
     * Of the courses that changed, the ones whose topic somebody is subscribed to.
     *
     * @param courseIds the courses that changed
     * @param topic     how to build the destination for a course
     * @return the subset that is worth sending
     */
    private Set<Long> watchedCourses(Set<Long> courseIds, LongFunction<String> topic) {
        return courseIds.stream().filter(courseId -> hasSubscribers(topic.apply(courseId))).collect(Collectors.toSet());
    }

    /**
     * Takes everything currently pending, leaving the set empty for the changes that arrive while this run is sending.
     * Removing each element as it is read rather than clearing at the end means a change that lands mid-drain is either
     * taken now or still pending afterwards, never dropped.
     *
     * @param pending the set of course ids to drain
     * @return what was pending
     */
    private static Set<Long> drain(Set<Long> pending) {
        Set<Long> drained = new HashSet<>();
        for (Iterator<Long> courses = pending.iterator(); courses.hasNext();) {
            drained.add(courses.next());
            courses.remove();
        }
        return drained;
    }

    /**
     * Sends a single build job update over websocket.
     * This is used for the build job detail page to receive live updates.
     *
     * @param buildJob the build job to send the update for
     */
    void sendBuildJobUpdateOverWebsocket(BuildJobQueueItem buildJob) {
        if (buildJob == null) {
            return;
        }
        localCIWebsocketMessagingService.sendBuildJobUpdate(buildJob);
    }

    /**
     * Sends a finished build job update over websocket.
     * This notifies clients that a new finished build job is available.
     *
     * @param finishedBuildJob the finished build job DTO to send
     */
    void sendFinishedBuildJobOverWebsocket(FinishedBuildJobDTO finishedBuildJob) {
        if (finishedBuildJob == null) {
            return;
        }
        localCIWebsocketMessagingService.sendFinishedBuildJobUpdate(finishedBuildJob);
        // Also send to the individual build job topic for the build job detail page
        localCIWebsocketMessagingService.sendFinishedBuildJobDetailUpdate(finishedBuildJob);
    }

    /**
     * Sends build agent information over websocket. This method is called when a new build agent is added or removed.
     *
     * @param agentName the name of the build agent
     */
    void sendBuildAgentInformationOverWebsocket(String agentName) {
        buildAgentSummaryChanged();
        sendBuildAgentDetailsOverWebsocket(agentName);
    }

    /**
     * Records that the build agent summary shown on the admin build agents page changed. Called both when agent
     * information changes and on every processing job, so it is collected like the queue changes above.
     */
    void buildAgentSummaryChanged() {
        buildAgentSummaryNeedsBroadcast.set(true);
    }

    private void sendBuildAgentDetailsOverWebsocket(String agentName) {
        distributedDataAccessService.getBuildAgentInformation().stream().filter(agent -> agent.buildAgent().name().equals(agentName)).findFirst()
                .ifPresent(localCIWebsocketMessagingService::sendBuildAgentDetails);
    }

    /**
     * Removes unnecessary information (e.g. repository info, build config, result) from the queued jobs before sending them over the websocket.
     *
     * @param queuedJobs the queued jobs
     */
    private static List<BuildJobQueueItem> removeUnnecessaryInformation(List<BuildJobQueueItem> queuedJobs) {
        var filteredQueuedJobs = new ArrayList<BuildJobQueueItem>(); // make list mutable in case it is not
        for (BuildJobQueueItem job : queuedJobs) {
            var buildConfig = removeUnnecessaryInformationFromBuildConfig(job.buildConfig());
            var repositoryInfo = removeUnnecessaryInformationFromRepositoryInfo(job.repositoryInfo());
            // The trailing null is the clone token, deliberately dropped rather than forwarded. @JsonIgnore on the
            // record component is what actually keeps it out of every payload, including the single-item updates and
            // the admin endpoints that do not pass through here; clearing it is a redundant second layer on this path.
            filteredQueuedJobs.add(new BuildJobQueueItem(job.id(), job.name(), job.buildAgent(), job.participationId(), job.courseId(), job.exerciseId(), job.retryCount(),
                    job.priority(), job.status(), repositoryInfo, job.jobTimingInfo(), buildConfig, null, null));

        }
        return filteredQueuedJobs;
    }

    /**
     * Removes unnecessary information (e.g. build script, docker image) from the build config before sending it over the websocket.
     *
     * @param buildConfig the build config
     */
    private static BuildConfig removeUnnecessaryInformationFromBuildConfig(BuildConfig buildConfig) {
        // We pass "" instead of null strings to avoid errors when serializing to JSON
        return new BuildConfig("", "", buildConfig.commitHashToBuild(), "", "", "", null, null, buildConfig.scaEnabled(), buildConfig.sequentialTestRunsEnabled(), null,
                buildConfig.timeoutSeconds(), "", "", "", null);
    }

    /**
     * Removes unnecessary information (RepositoryUris) from the repository info before sending it over the websocket.
     *
     * @param repositoryInfo the repository info
     */
    private static RepositoryInfo removeUnnecessaryInformationFromRepositoryInfo(RepositoryInfo repositoryInfo) {
        // We pass "" instead of null strings to avoid errors when serializing to JSON
        return new RepositoryInfo(repositoryInfo.repositoryName(), repositoryInfo.repositoryType(), repositoryInfo.triggeredByPushTo(), "", "", "", null, null);
    }

    /**
     * Removes unnecessary information (e.g. recent build jobs, public ssh key, result) from the running jobs before sending them over the websocket.
     *
     * @param buildAgentSummary the build agent summary
     */
    private static List<BuildAgentInformation> removeUnnecessaryInformationFromBuildAgentInformation(List<BuildAgentInformation> buildAgentSummary) {
        var filteredBuildAgentSummary = new ArrayList<BuildAgentInformation>(); // make list mutable in case it is not
        for (BuildAgentInformation agent : buildAgentSummary) {
            var runningJobs = removeUnnecessaryInformation(agent.runningBuildJobs());
            filteredBuildAgentSummary.add(new BuildAgentInformation(agent.buildAgent(), agent.maxNumberOfConcurrentBuildJobs(), agent.numberOfCurrentBuildJobs(), runningJobs,
                    agent.status(), null, null, agent.pauseAfterConsecutiveBuildFailures()));
        }
        return filteredBuildAgentSummary;
    }

}
