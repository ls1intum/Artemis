package de.tum.cit.aet.artemis.iris.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_SCHEDULING;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.communication.dto.CourseMemoryThreadDTO;
import de.tum.cit.aet.artemis.communication.repository.ConversationMessageRepository;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.AiSelectionDecision;
import de.tum.cit.aet.artemis.course.repository.CourseRepository;
import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSupportLevel;
import de.tum.cit.aet.artemis.iris.service.pyris.PyrisConnectorService;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.PyrisPipelineExecutionSettingsDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryCourseSyncDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemoryInstanceSyncDTO;
import de.tum.cit.aet.artemis.iris.service.pyris.dto.coursememorywebhook.PyrisCourseMemorySyncThreadDTO;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

/**
 * Nightly backstop for Course Memory: tells Pyris, per course, which threads may have an entry and at which version, and
 * which courses still exist and which of them have such threads. Pyris retracts every entry that missed an update (its
 * version is older), whose thread may no longer be stored, or whose thread or course was deleted.
 * <p>
 * Everything Artemis dispatches right away when something changes is best-effort: a Pyris outage, a crash or a lost
 * callback can leave an entry that should be gone. Every such change bumps the thread's version right before it is saved,
 * so this sync finds what the immediate update missed within a day. It runs on the scheduling node only; set
 * {@code artemis.iris.course-memory.sync-cron} to {@code -} to switch it off.
 */
@Lazy
@Service
@Profile(PROFILE_SCHEDULING)
@Conditional(IrisEnabled.class)
public class CourseMemorySyncService {

    private static final Logger log = LoggerFactory.getLogger(CourseMemorySyncService.class);

    /** Threads read per database round-trip while assembling one course's list. */
    private static final int PAGE_SIZE = 1000;

    private final ConversationMessageRepository conversationMessageRepository;

    private final ChannelRepository channelRepository;

    private final CourseRepository courseRepository;

    private final IrisSettingsService irisSettingsService;

    private final PyrisConnectorService pyrisConnectorService;

    @Value("${server.url}")
    private String artemisBaseUrl;

    public CourseMemorySyncService(ConversationMessageRepository conversationMessageRepository, ChannelRepository channelRepository, CourseRepository courseRepository,
            IrisSettingsService irisSettingsService, PyrisConnectorService pyrisConnectorService) {
        this.conversationMessageRepository = conversationMessageRepository;
        this.channelRepository = channelRepository;
        this.courseRepository = courseRepository;
        this.irisSettingsService = irisSettingsService;
        this.pyrisConnectorService = pyrisConnectorService;
    }

    /**
     * Runs the sync. The schedule is bound by placeholder because {@code @Scheduled} resolves it before any bean exists.
     */
    @Scheduled(cron = "${artemis.iris.course-memory.sync-cron:0 0 3 * * *}")
    public void syncCourseMemory() {
        ZonedDateTime snapshotAt = ZonedDateTime.now();
        String snapshot = snapshotAt.toInstant().toString();

        // Read before the instance sync is sent: Pyris cleans up every existing course not in this list itself.
        Set<Long> courseIds = conversationMessageRepository.findCourseIdsWithCourseMemory();
        pyrisConnectorService.executeCourseMemoryInstanceSync(
                new PyrisCourseMemoryInstanceSyncDTO(settings(), snapshot, List.copyOf(courseRepository.findAllCourseIds()), List.copyOf(courseIds)));

        for (long courseId : courseIds) {
            try {
                syncCourse(courseId, snapshotAt, snapshot);
            }
            catch (Exception e) {
                log.error("Course memory sync of course {} failed", courseId, e);
            }
        }
        log.info("Course memory sync sent for {} courses", courseIds.size());
    }

    /**
     * Sends the complete list of one course's threads that have a Course Memory version. Read page by page, sent in one
     * request: Pyris retracts every entry whose thread is not listed, so a partial list would retract valid entries.
     */
    private void syncCourse(long courseId, ZonedDateTime snapshotAt, String snapshot) {
        boolean irisEnabled = irisSettingsService.isEnabledForCourse(courseRepository.findByIdElseThrow(courseId));
        Set<Long> readableChannels = channelRepository.findIdsOfChannelsReadableByAllStudents(courseId, snapshotAt);
        List<PyrisCourseMemorySyncThreadDTO> threads = new ArrayList<>();
        long afterPostId = 0;
        List<CourseMemoryThreadDTO> batch;
        do {
            batch = conversationMessageRepository.findCourseMemoryThreadsOfCourseAfter(courseId, afterPostId, PageRequest.of(0, PAGE_SIZE));
            for (CourseMemoryThreadDTO thread : batch) {
                boolean eligible = irisEnabled && readableChannels.contains(thread.conversationId());
                threads.add(new PyrisCourseMemorySyncThreadDTO(thread.postId(), thread.version(), eligible));
                afterPostId = thread.postId();
            }
        }
        while (batch.size() == PAGE_SIZE);
        pyrisConnectorService.executeCourseMemoryCourseSync(new PyrisCourseMemoryCourseSyncDTO(settings(), snapshot, courseId, threads));
    }

    /**
     * Settings for a request that has no status callback; the token only has to be present.
     */
    private PyrisPipelineExecutionSettingsDTO settings() {
        return new PyrisPipelineExecutionSettingsDTO("course-memory-sync-" + UUID.randomUUID(), AiSelectionDecision.CLOUD_AI, artemisBaseUrl, "default",
                IrisSupportLevel.MODERATE.jsonValue());
    }
}
