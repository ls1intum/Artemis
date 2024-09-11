package de.tum.cit.aet.artemis.web.rest;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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

import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastEditor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.domain.Course;
import de.tum.cit.aet.artemis.domain.Exercise;
import de.tum.cit.aet.artemis.domain.Lecture;
import de.tum.cit.aet.artemis.domain.User;
import de.tum.cit.aet.artemis.domain.lecture.AttachmentUnit;
import de.tum.cit.aet.artemis.domain.lecture.ExerciseUnit;
import de.tum.cit.aet.artemis.domain.lecture.LectureUnit;
import de.tum.cit.aet.artemis.domain.metis.conversation.Channel;
import de.tum.cit.aet.artemis.repository.CourseRepository;
import de.tum.cit.aet.artemis.repository.LectureRepository;
import de.tum.cit.aet.artemis.repository.UserRepository;
import de.tum.cit.aet.artemis.repository.metis.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.service.ExerciseService;
import de.tum.cit.aet.artemis.service.LectureImportService;
import de.tum.cit.aet.artemis.service.LectureService;
import de.tum.cit.aet.artemis.service.metis.conversation.ChannelService;
import de.tum.cit.aet.artemis.web.rest.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.web.rest.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.web.rest.errors.BadRequestAlertException;
import de.tum.cit.aet.artemis.web.rest.util.HeaderUtil;

/**
 * REST controller for managing Lecture.
 */
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/")
public class LectureResource {

    private static final Logger log = LoggerFactory.getLogger(LectureResource.class);

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
     * @param lecture the lecture to create with a unique channel name
     * @return the ResponseEntity with status 201 (Created) and with body the new lecture, or with status 400 (Bad Request) if the lecture has already an ID
     * @throws URISyntaxException if the Location URI syntax is incorrect
     */
    @PostMapping("lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<Lecture> createLecture(@RequestBody Lecture lecture) throws URISyntaxException {
        log.debug("REST request to save Lecture : {}", lecture);
        if (lecture.getId() != null) {
            throw new BadRequestAlertException("A new lecture cannot already have an ID", ENTITY_NAME, "idExists");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        Lecture savedLecture = lectureRepository.save(lecture);
        channelService.createLectureChannel(savedLecture, Optional.ofNullable(lecture.getChannelName()));
        return ResponseEntity.created(new URI("/api/lectures/" + savedLecture.getId())).body(savedLecture);
    }

    /**
     * PUT /lectures : Updates an existing lecture.
     *
     * @param lecture the lecture to update and the updated channel name
     * @return the ResponseEntity with status 200 (OK) and with body the updated lecture, or with status 400 (Bad Request) if the lecture is not valid, or with status 500 (Internal
     *         Server Error) if the lecture couldn't be updated
     */
    @PutMapping("lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<Lecture> updateLecture(@RequestBody Lecture lecture) {
        log.debug("REST request to update Lecture : {}", lecture);
        if (lecture.getId() == null) {
            throw new BadRequestAlertException("Invalid id", ENTITY_NAME, "idNull");
        }
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.EDITOR, lecture.getCourse(), null);

        // Make sure that the original references are preserved.
        Lecture originalLecture = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture.getId());

        // NOTE: Make sure that all references are preserved here
        lecture.setLectureUnits(originalLecture.getLectureUnits());

        channelService.updateLectureChannel(lecture, lecture.getChannelName());

        Lecture result = lectureRepository.save(lecture);
        return ResponseEntity.ok().body(result);
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
     * GET /courses/:courseId/lectures : get all the lectures of a course for the course administration page
     *
     * @param withLectureUnits if set associated lecture units will also be loaded
     * @param courseId         the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the list of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures")
    @EnforceAtLeastEditor
    public ResponseEntity<Set<Lecture>> getLecturesForCourse(@PathVariable Long courseId, @RequestParam(required = false, defaultValue = "false") boolean withLectureUnits) {
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
        return ResponseEntity.ok().body(lectures);
    }

    /**
     * GET /courses/:courseId/lectures : get all the lectures of a course with their lecture units and slides
     *
     * @param courseId the courseId of the course for which all lectures should be returned
     * @return the ResponseEntity with status 200 (OK) and the set of lectures in body
     */
    @GetMapping("courses/{courseId}/lectures-with-slides")
    @EnforceAtLeastStudent
    public ResponseEntity<Set<Lecture>> getLecturesWithSlidesForCourse(@PathVariable Long courseId) {
        log.debug("REST request to get all Lectures with slides of the units for the course with id : {}", courseId);

        Course course = courseRepository.findByIdElseThrow(courseId);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, null);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        Set<Lecture> lectures = lectureRepository.findAllByCourseIdWithAttachmentsAndLectureUnitsAndSlides(courseId);
        lectures = lectureService.filterVisibleLecturesWithActiveAttachments(course, lectures, user);
        lectures.forEach(lectureService::filterActiveAttachmentUnits);
        return ResponseEntity.ok().body(lectures);
    }

    /**
     * GET /lectures/:lectureId : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}")
    @EnforceAtLeastStudent
    public ResponseEntity<Lecture> getLecture(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {}", lectureId);
        Lecture lecture = lectureRepository.findById(lectureId).orElseThrow();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(lecture, userRepository.getUserWithGroupsAndAuthorities());

        Channel lectureChannel = channelRepository.findChannelByLectureId(lectureId);
        if (lectureChannel != null) {
            lecture.setChannelName(lectureChannel.getName());
        }

        return ResponseEntity.ok(lecture);
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
    public ResponseEntity<Lecture> importLecture(@PathVariable long sourceLectureId, @RequestParam long courseId) throws URISyntaxException {
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

        final var savedLecture = lectureImportService.importLecture(sourceLecture, destinationCourse);
        channelService.createLectureChannel(savedLecture, Optional.empty());
        return ResponseEntity.created(new URI("/api/lectures/" + savedLecture.getId())).body(savedLecture);
    }

    /**
     * POST /courses/{courseId}/ingest
     * This endpooint is for starting the ingestion of all lectures or only one lecture when triggered in Artemis.
     *
     * @param courseId  the ID of the course for which all lectures should be ingested in pyris
     * @param lectureId If this id is present then only ingest this one lecture of the respective course
     * @return the ResponseEntity with status 200 (OK) and a message success or null if the operation failed
     */
    @PostMapping("courses/{courseId}/ingest")
    public ResponseEntity<Boolean> ingestLectures(@PathVariable Long courseId, @RequestParam(required = false) Optional<Long> lectureId) {
        log.debug("REST request to ingest lectures of course : {}", courseId);
        Course course = courseRepository.findByIdWithLecturesAndLectureUnitsElseThrow(courseId);
        if (lectureId.isPresent()) {
            Optional<Lecture> lectureToIngest = course.getLectures().stream().filter(lecture -> lecture.getId().equals(lectureId.get())).findFirst();
            if (lectureToIngest.isPresent()) {
                Set<Lecture> lecturesToIngest = new HashSet<>();
                lecturesToIngest.add(lectureToIngest.get());
                return ResponseEntity.ok().body(lectureService.ingestLecturesInPyris(lecturesToIngest));
            }
            return ResponseEntity.badRequest()
                    .headers(HeaderUtil.createAlert(applicationName, "Could not send lecture to Iris, no lecture found with the provided id.", "idExists")).body(null);

        }
        return ResponseEntity.ok().body(lectureService.ingestLecturesInPyris(course.getLectures()));
    }

    /**
     * GET /lectures/:lectureId/details : get the "lectureId" lecture.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and competencies, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/details")
    @EnforceAtLeastStudent
    public ResponseEntity<Lecture> getLectureWithDetails(@PathVariable Long lectureId) {
        log.debug("REST request to get lecture {} with details", lectureId);
        Lecture lecture = lectureRepository.findByIdWithAttachmentsAndPostsAndLectureUnitsAndCompetenciesAndCompletionsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkIsAllowedToSeeLectureElseThrow(lecture, user);
        lecture = filterLectureContentForUser(lecture, user);

        return ResponseEntity.ok(lecture);
    }

    /**
     * GET /lectures/:lectureId/details-with-slides : get the "lectureId" lecture with active lecture units and with slides.
     *
     * @param lectureId the lectureId of the lecture to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the lecture including posts, lecture units and competencies, or with status 404 (Not Found)
     */
    @GetMapping("lectures/{lectureId}/details-with-slides")
    @EnforceAtLeastStudent
    public ResponseEntity<Lecture> getLectureWithDetailsAndSlides(@PathVariable long lectureId) {
        log.debug("REST request to get lecture {} with details with slides ", lectureId);
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndSlidesAndAttachmentsElseThrow(lectureId);
        Course course = lecture.getCourse();
        if (course == null) {
            return ResponseEntity.badRequest().build();
        }
        authCheckService.checkIsAllowedToSeeLectureElseThrow(lecture, userRepository.getUserWithGroupsAndAuthorities());

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
    @GetMapping("lectures/{lectureId}/title")
    @EnforceAtLeastStudent
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
                ((ExerciseUnit) lectureUnit).getExercise().setCompetencies(exercise.getCompetencies());
            }
        }).toList();

        lecture.setLectureUnits(lectureUnitsUserIsAllowedToSee);
        return lecture;
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
