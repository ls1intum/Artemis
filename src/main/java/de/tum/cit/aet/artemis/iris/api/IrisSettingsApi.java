package de.tum.cit.aet.artemis.iris.api;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.iris.config.IrisEnabled;
import de.tum.cit.aet.artemis.iris.domain.message.IrisMessageContent;
import de.tum.cit.aet.artemis.iris.domain.session.IrisChatSession;
import de.tum.cit.aet.artemis.iris.dto.IrisCourseSettingsWithRateLimitDTO;
import de.tum.cit.aet.artemis.iris.dto.export.IrisChatSessionExportDTO;
import de.tum.cit.aet.artemis.iris.dto.export.IrisMessageExportDTO;
import de.tum.cit.aet.artemis.iris.repository.IrisChatSessionRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisCourseSettingsRepository;
import de.tum.cit.aet.artemis.iris.repository.IrisProactiveEpisodeRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;

@Conditional(IrisEnabled.class)
@Controller
@Lazy
public class IrisSettingsApi extends AbstractIrisApi {

    private final IrisSettingsService irisSettingsService;

    private final IrisCourseSettingsRepository irisCourseSettingsRepository;

    private final IrisChatSessionRepository irisChatSessionRepository;

    private final IrisProactiveEpisodeRepository irisProactiveEpisodeRepository;

    public IrisSettingsApi(IrisSettingsService irisSettingsService, IrisCourseSettingsRepository irisCourseSettingsRepository, IrisChatSessionRepository irisChatSessionRepository,
            IrisProactiveEpisodeRepository irisProactiveEpisodeRepository) {
        this.irisSettingsService = irisSettingsService;
        this.irisCourseSettingsRepository = irisCourseSettingsRepository;
        this.irisChatSessionRepository = irisChatSessionRepository;
        this.irisProactiveEpisodeRepository = irisProactiveEpisodeRepository;
    }

    public IrisCourseSettingsWithRateLimitDTO getSettingsForCourse(long courseId) {
        return irisSettingsService.getCourseSettingsWithRateLimit(courseId);
    }

    public boolean isIrisEnabledForCourse(long courseId) {
        return irisSettingsService.isEnabledForCourse(courseId);
    }

    /**
     * Deletes the Iris course settings for a given course.
     *
     * @param courseId the ID of the course
     */
    public void deleteCourseSettings(long courseId) {
        irisCourseSettingsRepository.deleteByCourseId(courseId);
    }

    /**
     * Deletes all Iris course chat sessions for a given course.
     *
     * @param courseId the ID of the course
     */
    public void deleteCourseChatSessions(long courseId) {
        irisChatSessionRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Deletes the proactive struggle episodes of a course's own exercises.
     *
     * <p>
     * Separate from the chat sessions because an episode is not one: it is registered when a struggle trigger is
     * accepted, outlives the session it was decided in, and is keyed on the exercise rather than the session. A
     * student-data reset preserves the course's exercises, so nothing else would remove these rows.
     *
     * <p>
     * The scope is the delete query's, see {@code IrisProactiveEpisodeRepository#deleteAllByCourseId}.
     *
     * @param courseId the ID of the course
     * @return number of episodes deleted
     */
    public int deleteCourseProactiveEpisodes(long courseId) {
        return irisProactiveEpisodeRepository.deleteAllByCourseId(courseId);
    }

    /**
     * Counts the number of Iris course chat sessions for a given course.
     *
     * @param courseId the ID of the course
     * @return the number of chat sessions in the course
     */
    public long countCourseChatSessionsByCourseId(long courseId) {
        return irisChatSessionRepository.countByCourseId(courseId);
    }

    /**
     * Finds all Iris course chat sessions with messages for export.
     *
     * @param courseId the ID of the course
     * @return list of chat session export DTOs with messages, sorted by creation date
     */
    public List<IrisChatSessionExportDTO> findCourseChatSessionsForExport(long courseId) {
        List<IrisChatSession> sessions = irisChatSessionRepository.findAllWithMessagesByCourseIdSortedByCreationDate(courseId);
        return sessions.stream().map(this::convertToExportDTO).toList();
    }

    private IrisChatSessionExportDTO convertToExportDTO(IrisChatSession session) {
        List<IrisMessageExportDTO> messages = session.getMessages().stream().map(message -> new IrisMessageExportDTO(message.getId(), message.getSentAt(),
                message.getSender() != null ? message.getSender().name() : null, extractMessageContent(message.getContent()), message.getHelpful())).toList();

        return new IrisChatSessionExportDTO(session.getId(), session.getUserId(), session.getCreationDate(), messages);
    }

    private String extractMessageContent(List<IrisMessageContent> contents) {
        if (contents == null || contents.isEmpty()) {
            return null;
        }
        return contents.stream().map(IrisMessageContent::getContentAsString).filter(s -> s != null && !s.isEmpty()).collect(Collectors.joining("\n"));
    }
}
