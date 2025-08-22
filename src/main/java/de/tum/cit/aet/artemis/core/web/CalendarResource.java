package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.CalendarSubscriptionService;
import de.tum.cit.aet.artemis.core.service.CalendarSubscriptionService.CalendarEventFilterOption;
import de.tum.cit.aet.artemis.core.util.CalendarUtil;
import de.tum.cit.aet.artemis.exam.api.ExamApi;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.lecture.api.LectureApi;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.tutorialgroup.api.TutorialGroupApi;

@Lazy
@Profile(PROFILE_CORE)
@RestController
@RequestMapping("api/core/calendar/")
public class CalendarResource {

    private static final Logger log = LoggerFactory.getLogger(CalendarResource.class);

    private final UserRepository userRepository;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<ExamApi> examApi;

    private final LectureApi lectureApi;

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CalendarSubscriptionService calendarSubscriptionService;

    public CalendarResource(UserRepository userRepository, Optional<TutorialGroupApi> tutorialGroupApi, Optional<ExamApi> examApi, LectureApi lectureApi,
            ExerciseService exerciseService, QuizExerciseService quizExerciseService, CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService,
            CalendarSubscriptionService calendarSubscriptionService) {
        this.userRepository = userRepository;
        this.tutorialGroupApi = tutorialGroupApi;
        this.examApi = examApi;
        this.lectureApi = lectureApi;
        this.quizExerciseService = quizExerciseService;
        this.exerciseService = exerciseService;
        this.courseRepository = courseRepository;
        this.authorizationCheckService = authorizationCheckService;
        this.calendarSubscriptionService = calendarSubscriptionService;
    }

    @GetMapping("/subscriptionToken")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getSubscriptionToken() {
        User user = userRepository.getUser();
        String token = calendarSubscriptionService.getOrCreateSubscriptionTokenFor(user);
        return ResponseEntity.ok(token);
    }

    @GetMapping("/courses/{courseId}/subscription/calendarEvents.ics")
    public ResponseEntity<String> getSubscriptionFile(@PathVariable long courseId, @RequestParam("token") String token,
            @RequestParam("filterOptions") Set<CalendarEventFilterOption> filterOptions, @RequestParam("filterOptions") Language language) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.findOneWithGroupsAndAuthoritiesByCalendarSubscriptionToken(token).orElseThrow(() -> new AccessForbiddenException("Invalid token!"));
        boolean userIsStudent = authorizationCheckService.isStudentInCourse(course, user);
        boolean userIsCourseStaff = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!userIsStudent && !userIsCourseStaff) {
            throw new AccessForbiddenException("You are not allowed to access this course's resources!");
        }

        boolean includeTutorialEvents = filterOptions.contains(CalendarEventFilterOption.TUTORIALS);
        boolean includeExamEvents = filterOptions.contains(CalendarEventFilterOption.EXAMS);
        boolean includeLectureEvents = filterOptions.contains(CalendarEventFilterOption.EXERCISES);
        boolean includeExerciseEvents = filterOptions.contains(CalendarEventFilterOption.EXERCISES);

        Set<CalendarEventDTO> tutorialEventDTOs = includeTutorialEvents
                ? tutorialGroupApi.map(api -> api.getCalendarEventDTOsFromTutorialsGroups(user.getId(), courseId)).orElse(Collections.emptySet())
                : Collections.emptySet();
        Set<CalendarEventDTO> examEventDTOs = includeExamEvents
                ? examApi.map(api -> api.getCalendarEventDTOsFromExams(courseId, userIsStudent, language)).orElse(Collections.emptySet())
                : Collections.emptySet();
        Set<CalendarEventDTO> lectureEventDTOs = includeLectureEvents ? lectureApi.getCalendarEventDTOsFromLectures(courseId, userIsStudent, language) : Collections.emptySet();
        Set<CalendarEventDTO> quizExerciseEventDTOs = includeExerciseEvents ? quizExerciseService.getCalendarEventDTOsFromQuizExercises(courseId, userIsStudent, language)
                : Collections.emptySet();
        Set<CalendarEventDTO> otherExerciseEventDTOs = includeExerciseEvents ? exerciseService.getCalendarEventDTOsFromNonQuizExercises(courseId, userIsStudent, language)
                : Collections.emptySet();

        Set<CalendarEventDTO> calendarEventDTOs = Stream.of(tutorialEventDTOs, lectureEventDTOs, examEventDTOs, quizExerciseEventDTOs, otherExerciseEventDTOs).flatMap(Set::stream)
                .collect(Collectors.toSet());

        String icsFileString = calendarSubscriptionService.getICSFileAsString(calendarEventDTOs);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=calendarEvents.ics").body(icsFileString);
    }

    /**
     * GET calendar/course/:courseId/calendar-events : gets all {@link CalendarEventDTO}s associated to the given course falling into the requested month
     *
     * @param courseId  the id of the course for which the events should be fetched
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @return {@code 200 (OK)} with a map of DTOs keyed by day from client timezone perspective. All timestamps conform to ISO 8601 format.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course exists for the provided courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user does not have at least student role or if the user is not at least student in the course
     * @throws BadRequestException      {@code 400 (Bad Request)} if the monthKeys are empty or formatted incorrectly or if the timeZone is formatted incorrectly.
     */
    @GetMapping("courses/{courseId}/calendar-events")
    @EnforceAtLeastStudent
    public ResponseEntity<Map<String, List<CalendarEventDTO>>> getCalendarEventsOverlappingMonths(@PathVariable long courseId, @RequestParam List<String> monthKeys,
            @RequestParam String timeZone, @RequestParam Language language) {
        log.debug("REST request to get calendar events falling into: {}", monthKeys);

        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        authorizationCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.STUDENT, course, user);

        boolean userIsStudent = authorizationCheckService.isOnlyStudentInCourse(course, user);
        Set<YearMonth> months = CalendarUtil.deserializeMonthKeysOrElseThrow(monthKeys);
        ZoneId clientTimeZone = CalendarUtil.deserializeZoneIdOrElseThrow(timeZone);
        Long userId = user.getId();

        Set<CalendarEventDTO> tutorialEventDTOs = tutorialGroupApi.map(api -> api.getCalendarEventDTOsFromTutorialsGroups(userId, courseId)).orElse(Collections.emptySet());
        Set<CalendarEventDTO> examEventDTOs = examApi.map(api -> api.getCalendarEventDTOsFromExams(courseId, userIsStudent, language)).orElse(Collections.emptySet());
        Set<CalendarEventDTO> lectureEventDTOs = lectureApi.getCalendarEventDTOsFromLectures(courseId, userIsStudent, language);
        Set<CalendarEventDTO> quizExerciseEventDTOs = quizExerciseService.getCalendarEventDTOsFromQuizExercises(courseId, userIsStudent, language);
        Set<CalendarEventDTO> otherExerciseEventDTOs = exerciseService.getCalendarEventDTOsFromNonQuizExercises(courseId, userIsStudent, language);

        Set<CalendarEventDTO> calendarEventDTOs = Stream.of(tutorialEventDTOs, lectureEventDTOs, examEventDTOs, quizExerciseEventDTOs, otherExerciseEventDTOs).flatMap(Set::stream)
                .collect(Collectors.toSet());
        Set<CalendarEventDTO> filteredDTOs = CalendarUtil.filterForEventsOverlappingMonths(calendarEventDTOs, months, clientTimeZone);
        Set<CalendarEventDTO> splitDTOs = CalendarUtil.splitEventsSpanningMultipleDaysIfNecessary(filteredDTOs, clientTimeZone);
        Map<String, List<CalendarEventDTO>> calendarEventDTOsByDay = splitDTOs.stream()
                .collect(Collectors.groupingBy(dto -> dto.startDate().withZoneSameInstant(clientTimeZone).toLocalDate().toString()));

        return ResponseEntity.ok(calendarEventDTOsByDay);
    }
}
