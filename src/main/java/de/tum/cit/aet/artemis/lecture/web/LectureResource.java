package de.tum.cit.aet.artemis.lecture.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_IRIS;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.service.conversation.ChannelService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastEditorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastInstructorInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInCourse.EnforceAtLeastStudentInCourse;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInLecture.EnforceAtLeastStudentInLecture;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.dto.LectureDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureDetailsDTO;
import de.tum.cit.aet.artemis.lecture.dto.LectureSeriesCreateLectureDTO;
import de.tum.cit.aet.artemis.lecture.dto.SlideDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureRepository;
import de.tum.cit.aet.artemis.lecture.repository.SlideRepository;
import de.tum.cit.aet.artemis.lecture.service.LectureImportService;
import de.tum.cit.aet.artemis.lecture.service.LectureService;

/**
 * REST controller for managing Lecture.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/lecture/")
public class LectureResource {

    private static final Logger log = LoggerFactory.getLogger(LectureResource.class);

    private static final String ENTITY_NAME = "lecture";

    private final SlideRepository slideRepository;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureRepository lectureRepository;

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    public LectureResource(LectureRepository lectureRepository, LectureService lectureService, LectureImportService lectureImportService, CourseRepository courseRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, ChannelService channelService, ChannelRepository channelRepository,
            SlideRepository slideRepository) {
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
        this.slideRepository = slideRepository;
    }

    /**
     * POST /lectures : Create a new lecture.
     *
     * @param newLectureDto the lecture to create with a unique channel name
     * @return the ResponseEntity with status 201 (Created) and with body the new lecture, or with status 400 (Bad Request) if the lecture has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<SimpleLectureDTO> createLecture(@RequestBody SimpleLectureDTO newLectureDto) throws URISyntaxException {
        log.debug("REST request to save Lecture : {}", newLectureDto);
        if (newLectureDto.id() != null) {
            throw new BadRequestAlertException("A new lecture cannot already have an ID", ENTITY_NAME, "idExists");
        }
        Course course = courseRepository.findByIdElseThrow(newLectureDto.course.id());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        Lecture newLecture = new Lecture();
        updateLectureAttributesFromDTO(newLecture, newLectureDto);
        newLecture.setCourse(course);

        Lecture savedLecture = lectureRepository.save(newLecture);
        String channelName = channelService.createLectureChannel(savedLecture, Optional.ofNullable(newLectureDto.channelName()));
        SimpleLectureDTO savedLectureDTO = SimpleLectureDTO.from(savedLecture, course, channelName);
        return ResponseEntity.created(new URI("/api/lecture/lectures/" + savedLecture.getId())).body(savedLectureDTO);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record SimpleLectureDTO(Long id, String title, String description, ZonedDateTime startDate, ZonedDateTime endDate,
            @JsonProperty("isTutorialLecture") boolean isTutorialLecture, String channelName, CourseDTO course) implements LectureDTO {

        public static SimpleLectureDTO from(Lecture lecture, String channelName) {
            return from(lecture, lecture.getCourse(), channelName);
        }

        public static SimpleLectureDTO from(Lecture lecture, @Nullable Course course, @Nullable String channelName) {
            return new SimpleLectureDTO(lecture.getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(), lecture.getEndDate(), lecture.isTutorialLecture(),
                    channelName, course == null ? null : CourseDTO.from(course));
        }

        public record CourseDTO(Long id, String title, String shortName, String studentGroupName, String teachingAssistantGroupName, String editorGroupName,
                String instructorGroupName) {

            public static CourseDTO from(@NonNull Course course) {
                return new CourseDTO(course.getId(), course.getTitle(), course.getShortName(), course.getStudentGroupName(), course.getTeachingAssistantGroupName(),
                        course.getEditorGroupName(), course.getInstructorGroupName());
            }
        }
    }

    /**
     * POST /courses/{courseId}/lectures : Creates a series of lectures for the given course. Adjusts default lecture and channel names to new lecture order.
     *
     * @param courseId    the ID of the course for which to create the lectures
     * @param lectureDTOs a list of DTOs defining the individual lectures to create
     * @return 204 (No Content) if the lecture series was successfully created
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user is not at least editor in the course or the course does not exist
     */
    @PostMapping("courses/{courseId}/lectures")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Void> createLectureSeries(@PathVariable long courseId, @RequestBody @NotEmpty List<@Valid LectureSeriesCreateLectureDTO> lectureDTOs) {
        log.debug("REST request to save Lecture series for courseId {} with lectures: {}", courseId, lectureDTOs);
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUser();

        List<Lecture> newLectures = lectureDTOs.stream().map(lectureDTO -> createLectureUsing(lectureDTO, course)).toList();
        List<Lecture> savedLectures = lectureRepository.saveAll(newLectures);
        channelService.createChannelsForLectures(savedLectures, course, user);

        lectureService.correctDefaultLectureAndChannelNames(courseId);

        return ResponseEntity.noContent().build();
    }

    private Lecture createLectureUsing(LectureSeriesCreateLectureDTO lectureDTO, Course course) {
        Lecture lecture = new Lecture();
        lecture.setCourse(course);
        lecture.setTitle(lectureDTO.title());
        lecture.setStartDate(lectureDTO.startDate());
        lecture.setEndDate(lectureDTO.endDate());
        return lecture;
    }

    /**
     * PUT /lectures : Updates an existing lecture.
     *
     * @param updatedLectureDto the lecture to update and the updated channel name
     * @return the ResponseEntity with status 200 (OK) and with body the updated lecture, or with status 400 (Bad Request) if the lecture is not valid, or with status 500 (Internal
     *         Server Error) if the lecture couldn't be updated
     */
    @PutMapping("lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<SimpleLectureDTO> updateLecture(@RequestBody SimpleLectureDTO updatedLectureDto) {
        log.debug("REST request to update Lecture : {}", updatedLectureDto);
        if (updatedLectureDto.id() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idNull");
        }
        Course course = courseRepository.findByIdElseThrow(updatedLectureDto.course.id());
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        // This explicitly does NOT change any relationships such as attachments or lecture units
        Lecture originalLecture = lectureRepository.findByIdElseThrow(updatedLectureDto.id());
        updateLectureAttributesFromDTO(originalLecture, updatedLectureDto);

        channelService.updateLectureChannel(originalLecture, updatedLectureDto.channelName());
        Lecture result = lectureRepository.save(originalLecture);

        SimpleLectureDTO resultDTO = SimpleLectureDTO.from(result, course, updatedLectureDto.channelName());
        return ResponseEntity.ok().body(resultDTO);
    }

    private static void updateLectureAttributesFromDTO(Lecture lecture, SimpleLectureDTO lectureDTO) {
        lecture.setTitle(lectureDTO.title());
        lecture.setDescription(lectureDTO.description());
        lecture.setStartDate(lectureDTO.startDate());
        lecture.setEndDate(lectureDTO.endDate());
        lecture.setIsTutorialLecture(lectureDTO.isTutorialLecture());
    }

    /**
     * Search for all lectures by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<SearchResultPageDTO<Lecture>> getAllLecturesOnPage(SearchTermPageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(lectureService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course for the course management page
     *
     * @param courseId the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<SimpleLectureDTO>> getLecturesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Lectures for the course with id : {}", courseId);

        Set<Lecture> lectures = lectureRepository.findAllByCourseId(courseId);
        // Note: the course (which is set by lecture.getCourse()) is currently required in the client for access control checks
        // While it would be enough to send it once separately, we keep it like this for now to avoid overengineering. Ideally, the course data is only sent once
        var lectureDtos = lectures.stream().map(lecture -> SimpleLectureDTO.from(lecture, null)).collect(Collectors.toSet());

        return ResponseEntity.ok().body(lectureDtos);
    }

    /**
     * GET /courses/:courseId/tutorial-lectures : get all the tutorial-lectures of a course
     *
     * @param courseId the courseId of the course for which the lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping("courses/{courseId}/tutorial-lectures")
    @EnforceAtLeastEditorInCourse
    public ResponseEntity<Set<SimpleLectureDTO>> getTutorialLecturesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Lectures for the course with id : {}", courseId);

        Set<Lecture> lectures = lectureRepository.findAllTutorialLecturesByCourseId(courseId);
        // Note: the course (which is set by lecture.getCourse()) is currently required in the client for access control checks
        // While it would be enough to send it once separately, we keep it like this for now to avoid overengineering. Ideally, the course data is only sent once
        var lectureDtos = lectures.stream().map(lecture -> SimpleLectureDTO.from(lecture, null)).collect(Collectors.toSet());
        return ResponseEntity.ok().body(lectureDtos);
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course with their attachment units and slides
     * NOTE: the response does not include other types of lecture units except attachment video units
     *
     * @param courseId the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the set of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures-with-slides")
    @EnforceAtLeastStudentInCourse
    public ResponseEntity<List<GetLecturesDTO>> getLecturesWithSlidesForCourse(@PathVariable Long courseId) {
        log.info("Getting all lectures with slides for course {}", courseId);
        long start = System.currentTimeMillis();

        /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
        /* TODO: #11479 - remove the commented out code OR add back in such that the result of the query is filtered for visible lectures again */
        var lectures = lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnits(courseId); // .stream().filter(Lecture::isVisibleToStudents).collect(Collectors.toSet());
        Set<Long> attachmentVideoUnitIds = lectures.stream().flatMap(lecture -> lecture.getLectureUnits().stream())
                .filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit).map(DomainObject::getId).collect(Collectors.toSet());

        // Load slides separately to avoid too large data exchange
        Set<SlideDTO> slides = slideRepository.findVisibleSlidesByAttachmentVideoUnits(attachmentVideoUnitIds);

        // Group slides by attachment video unit id to combine them into the DTOs
        Map<Long, List<SlideDTO>> slidesByAttachmentVideoUnitId = slides.stream().collect(Collectors.groupingBy(SlideDTO::attachmentVideoUnitId));
        // Convert visible lectures to DTOs (filtering active attachments) and add non hidden slides to the DTOs
        List<GetLecturesDTO> lectureDTOs = lectures.stream().map(GetLecturesDTO::from).sorted(Comparator.comparingLong(GetLecturesDTO::id)).toList();

        lectureDTOs.forEach(lectureDTO -> {
            for (AttachmentVideoUnitDTO attachmentVideoUnitDTO : lectureDTO.lectureUnits) {
                List<SlideDTO> slidesForAttachmentVideoUnit = slidesByAttachmentVideoUnitId.get(attachmentVideoUnitDTO.id);
                if (slidesForAttachmentVideoUnit != null) {
                    // remove unnecessary fields from the slide DTOs
                    var finalSlides = slidesForAttachmentVideoUnit.stream().map(slideDTO -> new SlideDTO(slideDTO.id(), slideDTO.slideNumber(), null, null))
                            .sorted(Comparator.comparingInt(SlideDTO::slideNumber)).toList();
                    attachmentVideoUnitDTO.slides.addAll(finalSlides);
                }
            }
        });

        log.info("     Finished getting all lectures with slides in {}ms", System.currentTimeMillis() - start);
        return ResponseEntity.ok().body(lectureDTOs);
    }

    // includes visible attachments and attachment video units only (no other lecture unit types)
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record GetLecturesDTO(Long id, String title, String description, ZonedDateTime startDate, ZonedDateTime endDate,
            @JsonProperty("isTutorialLecture") boolean isTutorialLecture, List<AttachmentDTO> attachments, List<AttachmentVideoUnitDTO> lectureUnits)
            implements de.tum.cit.aet.artemis.lecture.dto.LectureDTO {

        /**
         * Converts a lecture to a DTO. Only the attachments and attachment video units that are visible to students are included.
         *
         * @param lecture The lecture to convert
         * @return The converted lecture DTO
         */
        public static GetLecturesDTO from(Lecture lecture) {
            // only attachments visible to students are included
            List<AttachmentDTO> attachmentDTOs = lecture.getAttachments().stream().filter(Attachment::isVisibleToStudents).map(AttachmentDTO::from).toList();
            // only attachment video units visible to students are included
            List<AttachmentVideoUnitDTO> attachmentVideoUnitDTOs = lecture.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit)
                    .map(lectureUnit -> (AttachmentVideoUnit) lectureUnit).filter(AttachmentVideoUnit::isVisibleToStudents).map(AttachmentVideoUnitDTO::from).toList();
            /* The visibleDate property of the Lecture entity is deprecated. We’re keeping the related logic temporarily to monitor for user feedback before full removal */
            /* TODO: #11479 - remove the visibleDate property from the LectureDTO OR leave as is */
            return new GetLecturesDTO(lecture.getId(), lecture.getTitle(), lecture.getDescription(), lecture.getStartDate(), lecture.getEndDate(), lecture.isTutorialLecture(),
                    attachmentDTOs, attachmentVideoUnitDTOs);
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AttachmentDTO(Long id, String link, String name, ZonedDateTime releaseDate) {

        public static AttachmentDTO from(Attachment attachment) {
            return new AttachmentDTO(attachment.getId(), attachment.getLink(), attachment.getName(), attachment.getReleaseDate());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record AttachmentVideoUnitDTO(Long id, String name, List<SlideDTO> slides, @Nullable AttachmentDTO attachment, ZonedDateTime releaseDate, String type) {

        public static AttachmentVideoUnitDTO from(AttachmentVideoUnit attachmentVideoUnit) {
            var attachment = attachmentVideoUnit.getAttachment();
            return new AttachmentVideoUnitDTO(attachmentVideoUnit.getId(), attachmentVideoUnit.getName(), new ArrayList<>(),
                    attachment != null ? AttachmentDTO.from(attachment) : null, attachmentVideoUnit.getReleaseDate(), "attachment");
        }
    }

    /**
     * GET /lectures/:lectureId : get the lecture for the given ID.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}")
    @EnforceAtLeastStudentInLecture
    public ResponseEntity<SimpleLectureDTO> getLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {}", lectureId);
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow();
        String lectureChannelName = channelRepository.findChannelNameByLectureId(lectureId);
        SimpleLectureDTO lectureDTO = SimpleLectureDTO.from(lecture, lectureChannelName);
        return ResponseEntity.ok(lectureDTO);
    }

    /**
     * POST /lectures/import: Imports an existing lecture into an existing course
     * <p>
     * This will clone and import the whole lecture with associated lectureUnits and attachments.
     *
     * @param sourceLectureId The ID of the original lecture which should get imported
     * @param courseId        The ID of the course to import the lecture to
     * @return The imported lecture (200), a not found error (404) if the lecture does not exist,
     *         or a forbidden error (403) if the user is not at least an editor in the source or target course.
     * @throws URISyntaxException When the URI of the response entity is invalid
     */
    @PostMapping("lectures/import/{sourceLectureId}")
    @EnforceAtLeastEditor
    public ResponseEntity<SimpleLectureDTO> importLecture(@PathVariable long sourceLectureId, @RequestParam long courseId) throws URISyntaxException {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var sourceLecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(sourceLectureId);
        final var destinationCourse = courseRepository.findByIdWithLecturesElseThrow(courseId);

        Course course = sourceLecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        // Check that the user is an editor in both the source and target course
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, sourceLecture, user);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, destinationCourse, user);

        final var savedLecture = lectureImportService.importLecture(sourceLecture, destinationCourse, true);

        final var lectureDTO = SimpleLectureDTO.from(savedLecture, destinationCourse, null /* channel name not needed in client */);
        return ResponseEntity.created(new URI("/api/lecture/lectures/" + savedLecture.getId())).body(lectureDTO);
    }

    /**
     * POST /courses/{courseId}/ingest
     * This endpoint is for starting the ingestion of all lectures or only one lecture when triggered in Artemis.
     *
     * @param courseId  the ID of the course for which all lectures should be ingested in pyris
     * @param lectureId If this id is present then only ingest this one lecture of the respective course
     * @return the ResponseEntity with status 200 (OK) and a message success or null if the operation failed
     */
    @Profile(PROFILE_IRIS)
    @PostMapping("courses/{courseId}/ingest")
    @EnforceAtLeastInstructorInCourse
    public ResponseEntity<Void> ingestLectures(@PathVariable Long courseId, @RequestParam(required = false) Optional<Long> lectureId) {
        Course course = courseRepository.findWithLecturesAndLectureUnitsAndAttachmentsByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        if (lectureId.isPresent()) {
            Optional<Lecture> lectureToIngest = course.getLectures().stream().filter(lecture -> lecture.getId().equals(lectureId.get())).findFirst();
            if (lectureToIngest.isPresent()) {
                Set<Lecture> lecturesToIngest = new HashSet<>();
                lecturesToIngest.add(lectureToIngest.get());
                lectureService.ingestLecturesInPyris(lecturesToIngest);
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.badRequest().headers(HeaderUtil.createAlert(applicationName, "artemisApp.iris.ingestionAlert.allLecturesError", "idExists")).body(null);
        }
        lectureService.ingestLecturesInPyris(course.getLectures());
        return ResponseEntity.ok().build();
    }

    /**
     * GET /lectures/:lectureId/details : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and competencies, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/details")
    @EnforceAtLeastStudentInLecture
    public ResponseEntity<LectureDetailsDTO> getLectureWithDetails(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {} with details", lectureId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(lectureService.getForDetails(lectureId, user));
    }

    /**
     * GET /lectures/:lectureId/title : Returns the title of the lecture with the given id
     *
     * @param lectureId the id of the lecture
     * @return the title of the lecture wrapped in an ResponseEntity or 404 Not Found if no lecture with that id exists
     */
    @GetMapping("lectures/{lectureId}/title")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getLectureTitle(@PathVariable Long lectureId) {
        final var title = lectureRepository.getLectureTitle(lectureId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    /**
     * DELETE /lectures/:lectureId : delete the "id" lecture.
     *
     * @param lectureId the id of the lecture to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("lectures/{lectureId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteLecture(@PathVariable Long lectureId) {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lectureId);

        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);
        log.debug("REST request to delete Lecture : {}", lectureId);
        lectureService.delete(lecture, true);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureId.toString())).build();
    }
}
