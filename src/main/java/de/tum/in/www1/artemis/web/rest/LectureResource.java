package de.tum.in.www1.artemis.web.rest;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.lecture.AttachmentUnit;
import de.tum.in.www1.artemis.domain.lecture.ExerciseUnit;
import de.tum.in.www1.artemis.domain.lecture.LectureUnit;
import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.LectureRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import de.tum.in.www1.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.service.AuthorizationCheckService;
import de.tum.in.www1.artemis.service.ExerciseService;
import de.tum.in.www1.artemis.service.LectureImportService;
import de.tum.in.www1.artemis.service.LectureService;
import de.tum.in.www1.artemis.service.metis.conversation.ChannelService;
import de.tum.in.www1.artemis.web.rest.dto.LectureDTO;
import de.tum.in.www1.artemis.web.rest.dto.PageableSearchDTO;
import de.tum.in.www1.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.in.www1.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.in.www1.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Lecture.
 */
@RestController
@RequestMapping("/api")
public class LectureResource {

    private final Logger log = LoggerFactory.getLogger(LectureResource.class);

    private static final String ENTITY_NAME = "lecture";

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    private final LectureRepository lectureRepository;

    private final LectureService lectureService;

    private final LectureImportService lectureImportService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authCheckService;

    private final UserRepository userRepository;

    private final ExerciseService exerciseService;

    private final ChannelService channelService;

    private final ChannelRepository channelRepository;

    public LectureResource(LectureRepository lectureRepository, LectureService lectureService, LectureImportService lectureImportService, CourseRepository courseRepository,
            UserRepository userRepository, AuthorizationCheckService authCheckService, ExerciseService exerciseService, ChannelService channelService,
            ChannelRepository channelRepository) {
        this.lectureRepository = lectureRepository;
        this.lectureService = lectureService;
        this.lectureImportService = lectureImportService;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.authCheckService = authCheckService;
        this.exerciseService = exerciseService;
        this.channelService = channelService;
        this.channelRepository = channelRepository;
    }

    /**
     * POST /lectures : Create a new lecture.
     *
     * @param lectureDTO the lecture to create and a unique channel name
     * @return the ResponseEntity with status 201 (Created) and with body the new lecture, or with status 400 (Bad Request) if the lecture has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("/lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<LectureDTO> createLecture(@RequestBody LectureDTO lectureDTO) throws URISyntaxException {
        Lecture lecture = lectureDTO.lecture();
        log.debug("REST request to save Lecture : {}", lecture);
        if (lecture.getId() != null) {
            throw new BadRequestAlertException("A new lecture cannot already have an ID", ENTITY_NAME, "idExists");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);
        Lecture savedLecture = lectureRepository.save(lecture);
        channelService.createLectureChannel(savedLecture, lectureDTO.channelName());
        LectureDTO result = new LectureDTO(savedLecture, lectureDTO.channelName());
        return ResponseEntity.created(new URI("/api/lectures/" + savedLecture.getId())).body(result);
    }

    /**
     * PUT /lectures : Updates an existing lecture.
     *
     * @param lectureDTO the lecture to update and the updated channel name
     * @return the ResponseEntity with status 200 (OK) and with body the updated lecture, or with status 400 (Bad Request) if the lecture is not valid, or with status 500 (Internal
     *         Server Error) if the lecture couldn't be updated
     */
    @PutMapping("/lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<LectureDTO> updateLecture(@RequestBody LectureDTO lectureDTO) {
        Lecture lecture = lectureDTO.lecture();
        log.debug("REST request to update Lecture : {}", lecture);
        if (lecture.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idNull");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // Make sure that the original references are preserved.
        Lecture originalLecture = lectureRepository.findByIdWithLectureUnitsElseThrow(lecture.getId());

        // NOTE: Make sure that all references are preserved here
        lecture.setLectureUnits(originalLecture.getLectureUnits());

        // Make sure that the original references are preserved and the channel is updated if necessary
        channelService.updateLectureChannel(originalLecture, lectureDTO.channelName());

        Lecture result = lectureRepository.save(lecture);
        LectureDTO lectureDTOResult = new LectureDTO(result, lectureDTO.channelName());
        return ResponseEntity.ok().body(lectureDTOResult);
    }

    /**
     * Search for all lectures by title and course title. The result is pageable.
     *
     * @param search The pageable search containing the page size, page number and query string
     * @return The desired page, sorted and matching the given query
     */
    @GetMapping("lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<SearchResultPageDTO<Lecture>> getAllLecturesOnPage(PageableSearchDTO<String> search) {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        return ResponseEntity.ok(lectureService.getAllOnPageWithSize(search, user));
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course for the course administration page
     *
     * @param withLectureUnits if set associated lecture units will also be loaded
     * @param courseId         the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping(value = "/courses/{courseId}/lectures")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<Set<LectureDTO>> getLecturesForCourse(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "false") boolean withLectureUnits) {
        log.debug("REST request to get all Lectures for the course with id : {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, course, null);

        Set<Lecture> lectures;
        if (withLectureUnits) {
            lectures = lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnits(courseId);
        }
        else {
            lectures = lectureRepository.findAllByCourseIdWithAttachments(courseId);
        }

        Set<LectureDTO> lectureDTOS = lectures.stream().map(lecture -> new LectureDTO(lecture, "")).collect(Collectors.toSet());

        return ResponseEntity.ok().body(lectureDTOS);
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course with their lecture units and slides
     *
     * @param courseId the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the set of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures-with-slides")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Set<Lecture>> getLecturesWithSlidesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Lectures with slides of the units for the course with id : {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Set<Lecture> lectures = lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnitsAndSlides(courseId);
        lectures.forEach(lectureService::filterActiveAttachmentUnits);
        lectures.forEach(lecture -> lectureService.filterActiveAttachments(lecture, user));
        return ResponseEntity.ok().body(lectures);
    }

    /**
     * GET /lectures/:lectureId : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture, or with status 404 (Not Found)
     */
    @GetMapping("/lectures/{lectureId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<LectureDTO> getLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {}", lectureId);
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow();
        Channel lectureChannel = channelRepository.findChannelByLectureId(lectureId);
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.STUDENT, lecture, null);
        LectureDTO lectureDTO = new LectureDTO(lecture, lectureChannel.getName());
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
    @PostMapping("/lectures/import/{sourceLectureId}")
    @PreAuthorize("hasRole('EDITOR')")
    public ResponseEntity<LectureDTO> importLecture(@PathVariable long sourceLectureId, @RequestParam long courseId) throws URISyntaxException {
        final var user = userRepository.getUserWithGroupsAndAuthorities();
        final var sourceLecture = lectureRepository.findByIdWithLectureUnitsElseThrow(sourceLectureId);
        final var destinationCourse = courseRepository.findByIdWithLecturesElseThrow(courseId);

        Course course = sourceLecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        // Check that the user is an editor in both the source and target course
        authCheckService.checkHasAtLeastRoleForLectureElseThrow(Role.EDITOR, sourceLecture, user);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, destinationCourse, user);

        final var result = lectureImportService.importLecture(sourceLecture, destinationCourse);
        Channel createdChannel = channelService.createLectureChannel(result, result.getTitle());

        LectureDTO lectureDTO = new LectureDTO(result, createdChannel.getName());
        return ResponseEntity.created(new URI("/api/lectures/" + result.getId())).body(lectureDTO);
    }

    /**
     * GET /lectures/:lectureId/details : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and competencies, or with status 404 (Not Found)
     */
    @GetMapping("/lectures/{lectureId}/details")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Lecture> getLectureWithDetails(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {} with details", lectureId);
        Lecture lecture = lectureRepository.findByIdWithPostsAndLectureUnitsAndLearningGoalsAndCompletionsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);
        lecture = filterLectureContentForUser(lecture, user);

        return ResponseEntity.ok(lecture);
    }

    /**
     * GET /lectures/:lectureId/details-with-slides : get the "lectureId" lecture with active lecture units and with slides.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and learning goals, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/details-with-slides")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<Lecture> getLectureWithDetailsAndSlides(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {} with details with slides ", lectureId);
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndWithSlidesElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        lectureService.filterActiveAttachmentUnits(lecture);
        lectureService.filterActiveAttachments(lecture, user);
        return ResponseEntity.ok(lecture);
    }

    /**
     * GET /lectures/:lectureId/title : Returns the title of the lecture with the given id
     *
     * @param lectureId the id of the lecture
     * @return the title of the lecture wrapped in an ResponseEntity or 404 Not Found if no lecture with that id exists
     */
    @GetMapping("/lectures/{lectureId}/title")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<String> getLectureTitle(@PathVariable Long lectureId) {
        final var title = lectureRepository.getLectureTitle(lectureId);
        return title == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(title);
    }

    private Lecture filterLectureContentForUser(Lecture lecture, User user) {
        lecture = lectureService.filterActiveAttachments(lecture, user);

        // The Objects::nonNull is needed here because the relationship lecture -> lecture units is ordered and
        // hibernate sometimes adds nulls into the list of lecture units to keep the order
        Set<Exercise> relatedExercises = lecture.getLectureUnits().stream().filter(Objects::nonNull).filter(lectureUnit -> lectureUnit instanceof ExerciseUnit)
                .map(lectureUnit -> ((ExerciseUnit) lectureUnit).getExercise()).collect(Collectors.toSet());

        Set<Exercise> exercisesUserIsAllowedToSee = exerciseService.filterOutExercisesThatUserShouldNotSee(relatedExercises, user);
        Set<Exercise> exercisesWithAllInformationNeeded = exerciseService
                .loadExercisesWithInformationForDashboard(exercisesUserIsAllowedToSee.stream().map(Exercise::getId).collect(Collectors.toSet()), user);

        List<LectureUnit> lectureUnitsUserIsAllowedToSee = lecture.getLectureUnits().stream().filter(lectureUnit -> {
            if (lectureUnit == null) {
                return false;
            }
            if (lectureUnit instanceof ExerciseUnit) {
                return ((ExerciseUnit) lectureUnit).getExercise() != null && authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user)
                        && exercisesWithAllInformationNeeded.contains(((ExerciseUnit) lectureUnit).getExercise());
            }
            else if (lectureUnit instanceof AttachmentUnit) {
                return ((AttachmentUnit) lectureUnit).getAttachment() != null && authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user);
            }
            else {
                return authCheckService.isAllowedToSeeLectureUnit(lectureUnit, user);
            }
        }).peek(lectureUnit -> {
            lectureUnit.setCompleted(lectureUnit.isCompletedFor(user));

            if (lectureUnit instanceof ExerciseUnit) {
                Exercise exercise = ((ExerciseUnit) lectureUnit).getExercise();
                // we replace the exercise with one that contains all the information needed for correct display
                exercisesWithAllInformationNeeded.stream().filter(exercise::equals).findAny().ifPresent(((ExerciseUnit) lectureUnit)::setExercise);
                // re-add the competencies already loaded with the exercise unit
                ((ExerciseUnit) lectureUnit).getExercise().setLearningGoals(exercise.getLearningGoals());
            }
        }).collect(Collectors.toCollection(ArrayList::new));

        lecture.setLectureUnits(lectureUnitsUserIsAllowedToSee);
        return lecture;
    }

    /**
     * DELETE /lectures/:lectureId : delete the "id" lecture.
     *
     * @param lectureId the id of the lecture to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("/lectures/{lectureId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<Void> deleteLecture(@PathVariable Long lectureId) {
        Lecture lecture = lectureRepository.findByIdElseThrow(lectureId);

        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }

        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, null);

        log.debug("REST request to delete Lecture : {}", lectureId);
        lectureService.delete(lecture);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, ENTITY_NAME, lectureId.toString())).build();
    }
}
