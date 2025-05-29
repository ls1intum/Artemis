package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.atlas.api.CompetencyApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyProgressApi;
import de.tum.cit.aet.artemis.atlas.api.CompetencyRelationApi;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureTranscription;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

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

    private final Optional<CompetencyApi> competencyApi;

    private final ExerciseService exerciseService;

    private final LectureUnitRepository lectureUnitRepository;

    public LectureService(LectureRepository lectureRepository, AuthorizationCheckService authCheckService, ChannelRepository channelRepository, ChannelService channelService,
            Optional<IrisLectureApi> irisLectureApi, Optional<CompetencyProgressApi> competencyProgressApi, Optional<CompetencyRelationApi> competencyRelationApi,
            Optional<CompetencyApi> competencyApi, ExerciseService exerciseService, LectureUnitRepository lectureUnitRepository) {
        this.lectureRepository = lectureRepository;
        this.authCheckService = authCheckService;
        this.channelRepository = channelRepository;
        this.channelService = channelService;
        this.irisLectureApi = irisLectureApi;
        this.competencyProgressApi = competencyProgressApi;
        this.competencyRelationApi = competencyRelationApi;
        this.competencyApi = competencyApi;
        this.exerciseService = exerciseService;
        this.lectureUnitRepository = lectureUnitRepository;
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
     * It only returns results for which the user (at least editor) has access to the course.
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
            Lecture lectureWithAttachmentVideoUnits = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());
            List<AttachmentVideoUnit> attachmentVideoUnitList = lectureWithAttachmentVideoUnits.getLectureUnits().stream()
                    .filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit).map(lectureUnit -> (AttachmentVideoUnit) lectureUnit).toList();

            if (!attachmentVideoUnitList.isEmpty()) {
                irisLectureApi.get().deleteLectureFromPyrisDB(attachmentVideoUnitList);
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
            List<AttachmentVideoUnit> attachmentVideoUnitList = lectures.stream().flatMap(lec -> lec.getLectureUnits().stream()).filter(unit -> unit instanceof AttachmentVideoUnit)
                    .map(unit -> (AttachmentVideoUnit) unit).toList();
            for (AttachmentVideoUnit attachmentVideoUnit : attachmentVideoUnitList) {
                irisLectureApi.get().addLectureUnitToPyrisDB(attachmentVideoUnit);
            }
        }
    }

    /**
     * Ingest the transcriptions in the Pyris system
     *
     * @param transcription       Transcription to be ingested
     * @param course              The course containing the transcription
     * @param lecture             The lecture containing the transcription
     * @param attachmentVideoUnit The lecture unit containing the transcription
     */
    public void ingestTranscriptionInPyris(LectureTranscription transcription, Course course, Lecture lecture, AttachmentVideoUnit attachmentVideoUnit) {
        irisLectureApi.ifPresent(webhookService -> webhookService.addTranscriptionsToPyrisDB(transcription, course, lecture, attachmentVideoUnit));
    }

    /**
     * Deletes an existing Lecture transcription from the Pyris system. If the PyrisWebhookService is unavailable, the method does nothing.
     *
     * @param existingLectureTranscription the Lecture transcription to be removed from Pyris
     */
    public void deleteLectureTranscriptionInPyris(LectureTranscription existingLectureTranscription) {
        irisLectureApi.ifPresent(webhookService -> webhookService.deleteLectureTranscription(existingLectureTranscription));
    }

    /**
     * Returns a lecture with all lecture units and attachments for the given lectureId.
     * The lecture is filtered for the given user, so that only the content the user is allowed to see is returned.
     * The lecture units are also enriched with the completion information for the user.
     *
     * @param lectureId the id of the lecture to retrieve
     * @param user      the user for which to filter the lecture content
     * @return the lecture with all lecture units and attachments, filtered for the user
     */
    public Lecture getForDetails(long lectureId, User user) {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this lecture does not exist", "lecture", "courseNotFound");
        }
        Set<LectureUnitCompletion> completionsForLectureAndUser = lectureUnitRepository.findCompletionsForLectureAndUser(lectureId, user.getId());
        Map<Long, LectureUnitCompletion> byUnit = completionsForLectureAndUser.stream().collect(toMap(cu -> cu.getLectureUnit().getId(), identity()));

        lecture.getLectureUnits().forEach(lu -> {
            LectureUnitCompletion completion = byUnit.get(lu.getId());
            lu.setCompletedUsers(completion != null ? Set.of(completion) : Collections.emptySet());
        });
        competencyApi.ifPresent(api -> api.addCompetencyLinksToExerciseUnits(lecture));
        return filterLectureContentForUser(lecture, user);

    }

    private Lecture filterLectureContentForUser(Lecture lecture, User user) {
        lecture = filterActiveAttachments(lecture, user);

        // The Objects::nonNull is needed here because the relationship lecture -> lecture units is ordered and
        // hibernate sometimes adds nulls into the list of lecture units to keep the order
        Set<Exercise> relatedExercises = lecture.getLectureUnits().stream().filter(Objects::nonNull).filter(ExerciseUnit.class::isInstance).map(ExerciseUnit.class::cast)
                .map(ExerciseUnit::getExercise).collect(Collectors.toSet());

        Set<Long> exerciseIdsUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(relatedExercises, user).stream().map(Exercise::getId)
                .collect(Collectors.toSet());

        Map<Long, Exercise> exerciseIdToExercise = exerciseService.loadExercisesWithInformationForDashboard(exerciseIdsUserIsAllowedToSee, user).stream()
                .collect(Collectors.toMap(Exercise::getId, Function.identity()));

        List<LectureUnit> lectureUnitsUserIsAllowedToSee = lecture.getLectureUnits().stream().filter(lectureUnit -> switch (lectureUnit) {
            case null -> false;
            case ExerciseUnit exerciseUnit -> exerciseUnit.getExercise() != null && authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)
                    && exerciseIdToExercise.containsKey(exerciseUnit.getExercise().getId());
            default -> authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user);
        }).peek(lectureUnit -> {
            lectureUnit.setCompleted(lectureUnit.isCompletedFor(user));

            if (lectureUnit instanceof ExerciseUnit exerciseUnit) {
                Exercise exercise = exerciseUnit.getExercise();
                // we replace the exercise with one that contains all the information needed for correct display
                Exercise exerciseWithInfo = exerciseIdToExercise.get(exercise.getId());
                if (exerciseWithInfo != null) {
                    exerciseUnit.setExercise(exerciseWithInfo);
                }
                // re-add the competencies already loaded with the exercise unit
                exerciseUnit.getExercise().setCompetencyLinks(exercise.getCompetencyLinks());
            }
        }).toList();

        lecture.setLectureUnits(lectureUnitsUserIsAllowedToSee);
        return lecture;
    }
}
