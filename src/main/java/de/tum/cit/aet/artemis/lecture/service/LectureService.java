package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.VideoUnit;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;

@Profile(PROFILE_CORE)
@Service
public class LectureService {

    private final LectureRepository lectureRepository;

    private final AuthorizationCheckService authCheckService;

    private final ChannelRepository channelRepository;

    private final ChannelService channelService;

    private final Optional<IrisLectureApi> irisLectureApi;

    private final Optional<CompetencyProgressApi> competencyProgressApi;

    private final Optional<CompetencyRelationApi> competencyRelationApi;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService, ChannelRepository channelRepository, ChannelService channelService,
            Optional<IrisLectureApi> irisLectureApi, Optional<CompetencyProgressApi> competencyProgressApi, Optional<CompetencyRelationApi> competencyRelationApi) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.irisLectureApi = irisLectureApi;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyRelationApi = competencyRelationApi;
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
     * @param lecture                  the lecture to be deleted
     * @param updateCompetencyProgress whether the competency progress should be updated
     */
    public void delete(Lecture lecture, boolean updateCompetencyProgress) {
        if (irisLectureApi.isPresent()) {
            Lecture lectureWithAttachmentUnits = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());
            List<AttachmentUnit> attachmentUnitList = lectureWithAttachmentUnits.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentUnit)
                    .map(lectureUnit -> (AttachmentUnit) lectureUnit).toList();
            if (!attachmentUnitList.isEmpty()) {
                irisLectureApi.get().deleteLectureFromPyrisDB(attachmentUnitList);
            }
        }

        if (updateCompetencyProgress && competencyProgressApi.isPresent()) {
            var api = competencyProgressApi.get();
            lecture.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit))
                    .forEach(lectureUnit -> api.updateProgressForUpdatedLearningObjectAsync(lectureUnit, Optional.empty()));
        }

        Channel lectureChannel = channelRepository.findChannelByLectureId(lecture.getId());
        channelService.deleteChannel(lectureChannel);

        competencyRelationApi.ifPresent(api -> api.deleteAllLectureUnitLinksByLectureId(lecture.getId()));

        lectureRepository.deleteById(lecture.getId());
    }

    /**
     * Ingest the lectures when triggered by the ingest lectures button
     *
     * @param lectures set of lectures to be ingested
     */
    public void ingestLecturesInPyris(Set<Lecture> lectures) {
        if (irisLectureApi.isPresent()) {
            List<AttachmentUnit> attachmentUnitList = lectures.stream().flatMap(lec -> lec.getLectureUnits().stream()).filter(unit -> unit instanceof AttachmentUnit)
                    .map(unit -> (AttachmentUnit) unit).toList();
            for (AttachmentUnit attachmentUnit : attachmentUnitList) {
                irisLectureApi.get().addLectureUnitToPyrisDB(attachmentUnit);
            }
        }
    }

    /**
     * Ingest the transcriptions in the Pyris system
     *
     * @param transcription Transcription to be ingested
     * @param course        The course containing the transcription
     * @param lecture       The lecture containing the transcription
     * @param lectureUnit   The lecture unit containing the transcription
     */
    public void ingestTranscriptionInPyris(LectureTranscription transcription, Course course, Lecture lecture, VideoUnit lectureUnit) {
        irisLectureApi.ifPresent(webhookService -> webhookService.addTranscriptionsToPyrisDB(transcription, course, lecture, lectureUnit));
    }

    /**
     * Deletes an existing Lecture transcription from the Pyris system. If the PyrisWebhookService is unavailable, the method does nothing.
     *
     * @param existingLectureTranscription the Lecture transcription to be removed from Pyris
     */
    public void deleteLectureTranscriptionInPyris(LectureTranscription existingLectureTranscription) {
        irisLectureApi.ifPresent(webhookService -> webhookService.deleteLectureTranscription(existingLectureTranscription));
    }
}
