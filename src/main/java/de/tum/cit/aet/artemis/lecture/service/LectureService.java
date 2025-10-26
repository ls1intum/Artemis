package de.tum.cit.aet.artemis.lecture.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.springframework.context.annotation.Lazy;
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
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.LectureCalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.CalendarEventType;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.iris.api.IrisLectureApi;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;

@Profile(PROFILE_CORE)
@Lazy
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
            lecturePage = lectureRepository.findNonTutorialLecturesByTitleOrCourseTitle(searchTerm, searchTerm, pageable);
        }
        else {
            lecturePage = lectureRepository.findNonTutorialLecturesByTitleInLectureOrCourseAndUserHasAccessToCourse(searchTerm, searchTerm, user.getGroups(), pageable);
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
     * Retrieves a detailed {@link Lecture} for a given lecture ID and user.
     * <p>
     * This method:
     * <ul>
     * <li>Fetches the lecture with units and attachments.</li>
     * <li>Ensures the lecture is linked to a valid course.</li>
     * <li>Determines which lecture units the user has completed and updates them accordingly.</li>
     * <li>Optionally enriches the lecture with competency links via the injected {@code competencyApi}.</li>
     * <li>Filters the lecture content to match the user’s access rights.</li>
     * </ul>
     * <p>
     * <strong>Rationale:</strong> Combines lecture details, user-specific completion data, and optional competencies into a user-tailored view. It enforces data integrity,
     * supports optional enrichment, and ensures proper access control.
     *
     * @param lectureId the ID of the lecture
     * @param user      the user requesting lecture details
     * @return the filtered {@link Lecture} object
     * @throws BadRequestAlertException if the lecture is not linked to a course
     */
    // TODO: use a DTO instead of the Lecture entity to avoid sending unnecessary data to the client
    public Lecture getForDetails(long lectureId, User user) {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsWithCompetencyLinksAndAttachmentsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            throw new BadRequestAlertException("The course belonging to this lecture does not exist", "lecture", "courseNotFound");
        }
        Set<LectureUnitCompletion> completionsForLectureAndUser = lectureUnitRepository.findCompletionsForLectureAndUser(lectureId, user.getId());
        Map<Long, LectureUnitCompletion> byUnit = completionsForLectureAndUser.stream().collect(toMap(cu -> cu.getLectureUnit().getId(), identity()));

        lecture.getLectureUnits().forEach(lectureUnit -> {
            LectureUnitCompletion completion = byUnit.get(lectureUnit.getId());
            lectureUnit.setCompletedUsers(completion != null ? Set.of(completion) : Set.of());
            lectureUnit.getCompetencyLinks().forEach(competencyLink -> {
                if (competencyLink.getCompetency() != null && Hibernate.isInitialized(competencyLink.getCompetency())) {
                    competencyLink.getCompetency().setCourse(null); // Avoid sending the course to the client multiple times in the response to save data
                }
            });
        });
        competencyApi.ifPresent(api -> api.addCompetencyLinksToExerciseUnits(lecture));
        return filterLectureContentForUser(lecture, user);
    }

    /**
     * Filters a {@link Lecture} object’s content based on the user's access rights.
     * <p>
     * This method:
     * <ul>
     * <li>Filters out inactive attachments not visible to the user.</li>
     * <li>Removes Hibernate-added {@code null} lecture units to maintain integrity.</li>
     * <li>Collects exercises from the lecture units and filters out those the user should not see.</li>
     * <li>Enriches permitted exercises with full details needed for the dashboard.</li>
     * <li>Filters lecture units based on user permissions and updates each with completion status and competencies.</li>
     * </ul>
     * <p>
     * <strong>Rationale:</strong> Ensures that only authorized and fully detailed content is shown to the user. It handles Hibernate’s quirks (e.g., null entries) and aligns with
     * access control and information completeness for the dashboard.
     *
     * @param lecture the {@link Lecture} to filter which includes lecture units (with competency links) and attachments
     * @param user    the user requesting access
     * @return the filtered {@link Lecture}
     */
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
            // lecture units already contain the competency links, so we do not need to load them again
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

    /**
     * Retrieves a {@link LectureCalendarEventDTO} for each {@link Lecture} associated to the given courseId.
     * Each DTO encapsulates the visibleDate, startDate and endDate of the respective lecture.
     * <p>
     * The method then derives a set of {@link CalendarEventDTO}s from the DTOs. Whether events are included in the result
     * depends on the visibleDate and whether the logged-in user is a student of the {@link Course})
     *
     * @param courseId      the ID of the course
     * @param userIsStudent indicates whether the logged-in user is a student of the course
     * @param language      the language that will be used add context information to titles (e.g. the title of a lecture end event will be prefixed with "End: ")
     * @return the set of results
     */
    public Set<CalendarEventDTO> getCalendarEventDTOsFromLectures(long courseId, boolean userIsStudent, Language language) {
        Set<LectureCalendarEventDTO> dtos = lectureRepository.getLectureCalendarEventDTOsForCourseId(courseId);
        return dtos.stream().flatMap(dto -> deriveCalendarEventDTOs(dto, userIsStudent, language).stream()).collect(Collectors.toSet());
    }

    /**
     * Derives calendar events from the given {@link LectureCalendarEventDTO}. Expects at least one of startDate and endDate of the LectureCalendarEventDTO to be non-null-
     *
     * @param dto           the dto from which to derive the event
     * @param userIsStudent indicates whether the logged-in user is a student of the course (hence no tutor, editor, instructor)
     * @param language      the language that will be used add context information to titles (e.g. the title of a lecture end event will be prefixed with "End: ")
     * @throws IllegalArgumentException if both startDate and endDate of the LectureCalendarEventDTO are null
     * @return the derived event
     */
    private Set<CalendarEventDTO> deriveCalendarEventDTOs(LectureCalendarEventDTO dto, boolean userIsStudent, Language language) {
        ZonedDateTime startDate = dto.startDate();
        ZonedDateTime endDate = dto.endDate();

        boolean noDatesAvailable = startDate == null && endDate == null;
        if (noDatesAvailable) {
            throw new IllegalArgumentException("Tried to derive CalendarEventDTOs from a LectureCalendarEventDTO without startDate and endDate.");
        }

        boolean lectureIsInvisible = userIsStudent && dto.visibleDate() != null && ZonedDateTime.now().isBefore(dto.visibleDate());
        if (lectureIsInvisible) {
            return Set.of();
        }

        boolean onlyEndDateAvailable = startDate == null && endDate != null;
        if (onlyEndDateAvailable) {
            String titlePrefix = switch (language) {
                case ENGLISH -> "End: ";
                case GERMAN -> "Ende: ";
            };
            return Set.of(new CalendarEventDTO("lectureEndEvent-" + dto.originEntityId(), CalendarEventType.LECTURE, titlePrefix + dto.title(), dto.endDate(), null, null, null));
        }

        boolean onlyStartDateAvailable = startDate != null && endDate == null;
        if (onlyStartDateAvailable) {
            String titlePrefix = "Start: ";
            return Set
                    .of(new CalendarEventDTO("lectureStartEvent-" + dto.originEntityId(), CalendarEventType.LECTURE, titlePrefix + dto.title(), dto.startDate(), null, null, null));
        }

        final int TWELVE_HOURS_IN_MINUTES = 12 * 60;
        boolean lectureLengthExceedsTwelveHours = Duration.between(startDate, endDate).abs().toMinutes() > TWELVE_HOURS_IN_MINUTES;
        if (lectureLengthExceedsTwelveHours) {
            String startTitlePrefix = "Start: ";
            CalendarEventDTO startDto = new CalendarEventDTO("lectureStartEvent-" + dto.originEntityId(), CalendarEventType.LECTURE, startTitlePrefix + dto.title(),
                    dto.startDate(), null, null, null);
            String endTitlePrefix = switch (language) {
                case ENGLISH -> "End: ";
                case GERMAN -> "Ende: ";
            };
            CalendarEventDTO endDto = new CalendarEventDTO("lectureEndEvent-" + dto.originEntityId(), CalendarEventType.LECTURE, endTitlePrefix + dto.title(), dto.endDate(), null,
                    null, null);
            return Set.of(startDto, endDto);
        }

        return Set.of(new CalendarEventDTO("lectureStartAndEndEvent-" + dto.originEntityId(), CalendarEventType.LECTURE, dto.title(), dto.startDate(), dto.endDate(), null, null));
    }
}
