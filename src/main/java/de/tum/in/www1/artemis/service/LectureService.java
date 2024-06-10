package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.Attachment;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Lecture;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.AttachmentType;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.service.connectors.pyris.PyrisWebhookService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.util.PageUtil;

@Profile(PROFILE_CORE)
@Service
public class LectureService {

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final ChannelRepository channelRepository;

    private final ChannelService channelService;

    private final Optional<PyrisWebhookService> pyrisWebhookService;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService, ChannelRepository channelRepository, ChannelService channelService,
            Optional<PyrisWebhookService> pyrisWebhookService) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.pyrisWebhookService = pyrisWebhookService;
    }

    /**
     * For tutors, admins and instructors returns lecture with all attachments, for students lecture with only active attachments
     *
     * @param lectureWithAttachments lecture that has attachments
     * @param user                   the user for which this call should filter
     * @return lecture with filtered attachments
     */
    public Lecture filterActiveAttachments(Lecture lectureWithAttachments, User user) {
        Course course = lectureWithAttachments.getCourse();
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return lectureWithAttachments;
        }

        HashSet<Attachment> filteredAttachments = new HashSet<>();
        for (Attachment attachment : lectureWithAttachments.getAttachments()) {
            if (attachment.getReleaseDate() == null || attachment.getReleaseDate().isBefore(ZonedDateTime.now())) {
                filteredAttachments.add(attachment);
            }
        }
        lectureWithAttachments.setAttachments(filteredAttachments);
        return lectureWithAttachments;
    }

    /**
     * Lecture with only active attachment units
     *
     * @param lectureWithAttachmentUnits lecture that has attachment units
     */
    public void filterActiveAttachmentUnits(Lecture lectureWithAttachmentUnits) {

        List<LectureUnit> filteredAttachmentUnits = new ArrayList<>();
        for (LectureUnit unit : lectureWithAttachmentUnits.getLectureUnits()) {
            if (unit instanceof AttachmentUnit && (((AttachmentUnit) unit).getAttachment().getReleaseDate() == null
                    || ((AttachmentUnit) unit).getAttachment().getReleaseDate().isBefore(ZonedDateTime.now()))) {
                filteredAttachmentUnits.add(unit);
            }
        }
        lectureWithAttachmentUnits.setLectureUnits(filteredAttachmentUnits);
    }

    /**
     * Filter active attachments for a set of lectures. All lectures must be from the same course.
     *
     * @param course                  course all the lectures are from
     * @param lecturesWithAttachments lectures that have attachments
     * @param user                    the user for which this call should filter
     * @return lectures with filtered attachments
     */
    public Set<Lecture> filterVisibleLecturesWithActiveAttachments(Course course, Set<Lecture> lecturesWithAttachments, User user) {
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return lecturesWithAttachments;
        }

        Set<Lecture> lecturesWithFilteredAttachments = new HashSet<>();
        for (Lecture lecture : lecturesWithAttachments) {
            if (lecture.isVisibleToStudents()) {
                lecturesWithFilteredAttachments.add(filterActiveAttachments(lecture, user));
            }
        }
        return lecturesWithFilteredAttachments;
    }

    /**
     * Search for all lectures fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged.
     *
     * @param search The search query defining the search term and the size of the returned page
     * @param user   The user for whom to fetch all available lectures
     * @return A wrapper object containing a list of all found lectures and the total number of pages
     */
    public SearchResultPageDTO<Lecture> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final User user) {
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.LECTURE);
        final var searchTerm = search.getSearchTerm();
        final Page<Lecture> lecturePage;
        if (authCheckService.isAdmin(user)) {
            lecturePage = lectureRepository.findByTitleIgnoreCaseContainingOrCourse_TitleIgnoreCaseContaining(searchTerm, searchTerm, pageable);
        }
        else {
            lecturePage = lectureRepository.findByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
        }
        return new SearchResultPageDTO<>(lecturePage.getContent(), lecturePage.getTotalPages());
    }

    /**
     * Deletes the given lecture (with its lecture units).
     *
     * @param lecture the lecture to be deleted
     */
    public void delete(Lecture lecture) {
        if (pyrisWebhookService.isPresent()) {
            Lecture lectureWithAttachmentUnits = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());
            List<AttachmentUnit> attachmentUnitList = lectureWithAttachmentUnits.getLectureUnits().stream()
                    .filter(lectureUnit -> lectureUnit instanceof AttachmentUnit && ((AttachmentUnit) lectureUnit).getAttachment().getLink().endsWith(".pdf"))
                    .map(lectureUnit -> (AttachmentUnit) lectureUnit).toList();
            if (!attachmentUnitList.isEmpty()) {
                pyrisWebhookService.get().deleteLectureFromPyrisDB(attachmentUnitList);
            }
        }
        Channel lectureChannel = channelRepository.findChannelByLectureId(lecture.getId());
        channelService.deleteChannel(lectureChannel);
        lectureRepository.deleteById(lecture.getId());
    }

    /**
     * Ingest the lectures when triggered by the ingest lectures button
     *
     * @param lectures set of lectures to be ingested
     * @return returns the job token if the operation is successful else it returns null
     */
    public String ingestLecturesInPyris(Set<Lecture> lectures) {
        if (pyrisWebhookService.isPresent()) {
            List<AttachmentUnit> attachmentUnitList = lectures.stream().flatMap(lec -> lec.getLectureUnits().stream()).filter(unit -> unit instanceof AttachmentUnit)
                    .map(unit -> (AttachmentUnit) unit).filter(au -> au.getAttachment().getAttachmentType() == AttachmentType.FILE && au.getAttachment().getLink().endsWith(".pdf"))
                    .toList();
            if (!attachmentUnitList.isEmpty()) {
                return pyrisWebhookService.get().addLectureToPyrisDB(attachmentUnitList);
            }
        }
        return null;
    }
}
