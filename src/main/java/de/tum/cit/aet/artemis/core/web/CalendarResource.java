package de.tum.cit.aet.artemis.core.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
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
import de.tum.cit.aet.artemis.core.repository.CalendarSubscriptionTokenStoreRepository;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.CalendarSubscriptionService;
import de.tum.cit.aet.artemis.core.util.CalendarSubscriptionFilterOption;
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

    private final CalendarSubscriptionTokenStoreRepository calendarSubscriptionTokenStoreRepository;

    private final UserRepository userRepository;

    private final Optional<TutorialGroupApi> tutorialGroupApi;

    private final Optional<ExamApi> examApi;

    private final LectureApi lectureApi;

    private final QuizExerciseService quizExerciseService;

    private final ExerciseService exerciseService;

    private final CourseRepository courseRepository;

    private final AuthorizationCheckService authorizationCheckService;

    private final CalendarSubscriptionService calendarSubscriptionService;

    public CalendarResource(CalendarSubscriptionTokenStoreRepository calendarSubscriptionTokenStoreRepository, UserRepository userRepository,
            Optional<TutorialGroupApi> tutorialGroupApi, Optional<ExamApi> examApi, LectureApi lectureApi, ExerciseService exerciseService, QuizExerciseService quizExerciseService,
            CourseRepository courseRepository, AuthorizationCheckService authorizationCheckService, CalendarSubscriptionService calendarSubscriptionService) {
        this.calendarSubscriptionTokenStoreRepository = calendarSubscriptionTokenStoreRepository;
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

    /**
     * GET api/core/calendar/subscription-token : retrieves the subscription token associated to the logged-in user or creates one if not already present.
     * The token is a unique, shared secret between the user and the server and is embedded into the URLs of the iCalendar subscriptions to enable
     * authentication and authorization.
     *
     * @return {@code 200 (OK)} with the token.
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user is not at least a student
     */
    @GetMapping("subscription-token")
    @EnforceAtLeastStudent
    public ResponseEntity<String> getCalendarEventSubscriptionToken() {
        String userLogin = userRepository.getCurrentUserLogin();
        Optional<String> token = calendarSubscriptionTokenStoreRepository.findTokenByUserLogin(userLogin);
        return token.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.ok(calendarSubscriptionService.createSubscriptionTokenForUser(userLogin)));
    }

    /**
     * GET api/core/calendar/course/:courseId/calendar-events-ics : gets all {@link CalendarEventDTO}s associated to the given course
     * that are visible to the user and returns them as an .ics file.
     *
     * @param courseId      the id of the course for which the events should be fetched
     * @param token         a shared secret between user and server that enables authentication/authorization
     * @param filterOptions a list of options that determines what DTOs are included in the .ics file
     * @param language      the language that is used to localize Vevent summaries
     * @return {@code 200 (OK)} with an .ics file containing Vevents representing the DTOs.
     * @throws EntityNotFoundException  {@code 404 (Not Found)} if no course exists for the provided courseId
     * @throws AccessForbiddenException {@code 403 (Forbidden)} if the user associated to the token is not at least student in the course or if no user is associated to the token
     */
    @GetMapping("courses/{courseId}/calendar-events-ics")
    public ResponseEntity<String> getCalendarEventSubscriptionFile(@PathVariable long courseId, @RequestParam("token") String token,
            @RequestParam("filterOptions") Set<CalendarSubscriptionFilterOption> filterOptions, @RequestParam("language") Language language) {
        User user = userRepository.findOneWithGroupsAndAuthoritiesByCalendarSubscriptionToken(token).orElseThrow(() -> new AccessForbiddenException("Invalid token!"));
        Course course = courseRepository.findByIdElseThrow(courseId);
        boolean userIsStudent = authorizationCheckService.isOnlyStudentInCourse(course, user);
        boolean userIsCourseStaff = authorizationCheckService.isAtLeastTeachingAssistantInCourse(course, user);
        if (!userIsStudent && !userIsCourseStaff) {
            throw new AccessForbiddenException("You are not allowed to access this course's resources!");
        }

        boolean includeTutorialEvents = filterOptions.contains(CalendarSubscriptionFilterOption.TUTORIALS);
        boolean includeExamEvents = filterOptions.contains(CalendarSubscriptionFilterOption.EXAMS);
        boolean includeLectureEvents = filterOptions.contains(CalendarSubscriptionFilterOption.LECTURES);
        boolean includeExerciseEvents = filterOptions.contains(CalendarSubscriptionFilterOption.EXERCISES);

        Function<TutorialGroupApi, Set<CalendarEventDTO>> tutorialEventSupplier = api -> api.getCalendarEventDTOsFromTutorialsGroups(user.getId(), courseId);
        Function<ExamApi, Set<CalendarEventDTO>> examEventSupplier = api -> api.getCalendarEventDTOsFromExams(courseId, userIsStudent, language);
        Supplier<Set<CalendarEventDTO>> lectureEventSupplier = () -> lectureApi.getCalendarEventDTOsFromLectures(courseId, userIsStudent, language);
        Supplier<Set<CalendarEventDTO>> quizExerciseEventSupplier = () -> quizExerciseService.getCalendarEventDTOsFromQuizExercises(courseId, userIsStudent, language);
        Supplier<Set<CalendarEventDTO>> otherExerciseEventSupplier = () -> exerciseService.getCalendarEventDTOsFromNonQuizExercises(courseId, userIsStudent, language);

        Set<CalendarEventDTO> tutorialEventDTOs = getEventsIfShouldBeIncludedAndApiAvailable(includeTutorialEvents, tutorialGroupApi, tutorialEventSupplier);
        Set<CalendarEventDTO> examEventDTOs = getEventsIfShouldBeIncludedAndApiAvailable(includeExamEvents, examApi, examEventSupplier);
        Set<CalendarEventDTO> lectureEventDTOs = getEventsIfShouldBeIncluded(includeLectureEvents, lectureEventSupplier);
        Set<CalendarEventDTO> quizExerciseEventDTOs = getEventsIfShouldBeIncluded(includeExerciseEvents, quizExerciseEventSupplier);
        Set<CalendarEventDTO> otherExerciseEventDTOs = getEventsIfShouldBeIncluded(includeExerciseEvents, otherExerciseEventSupplier);

        Set<CalendarEventDTO> calendarEventDTOs = Stream.of(tutorialEventDTOs, lectureEventDTOs, examEventDTOs, quizExerciseEventDTOs, otherExerciseEventDTOs).flatMap(Set::stream)
                .collect(Collectors.toSet());

        String icsFileString = calendarSubscriptionService.getICSFileAsString(course.getShortName(), language, calendarEventDTOs);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/calendar; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"calendar-events.ics\"; filename*=UTF-8''calendar-events.ics").body(icsFileString);
    }

    /**
     * Uses the supplier to retrieve a set of {@link CalendarEventDTO}s if include is true.
     *
     * @param include  a flag indicating whether the supplier should be used
     * @param supplier a function that returns a set of DTOs
     * @return the set retrieved by the supplier if include is true, otherwise and empty set
     */
    private Set<CalendarEventDTO> getEventsIfShouldBeIncluded(boolean include, Supplier<Set<CalendarEventDTO>> supplier) {
        return include ? supplier.get() : Collections.emptySet();
    }

    /**
     * Uses the supplier to retrieve a set of {@link CalendarEventDTO}s if include is true and the api is available.
     *
     * @param include     a flag indicating whether the supplier should be used
     * @param apiOptional an optional that encapsulates the api on which the supplier should be called
     * @param supplier    a function that returns a set of DTOs
     * @return the set retrieved by the supplier if include is true and the api is available, otherwise and empty set
     */
    private <A> Set<CalendarEventDTO> getEventsIfShouldBeIncludedAndApiAvailable(boolean include, Optional<A> apiOptional, Function<A, Set<CalendarEventDTO>> supplier) {
        if (!include) {
            return Collections.emptySet();
        }
        return apiOptional.map(supplier).orElseGet(Collections::emptySet);
    }

    /**
     * GET api/core/calendar/course/:courseId/calendar-events : gets all {@link CalendarEventDTO}s associated to the given course falling into the requested month
     * that are visible to the logged-in user.
     *
     * @param courseId  the id of the course for which the events should be fetched
     * @param monthKeys a list of ISO 8601 formatted strings representing months
     * @param timeZone  the clients time zone as IANA time zone ID
     * @param language  the language that is used to localize DTO titles
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
        List<CalendarEventDTO> sortedDTOs = splitDTOs.stream().sorted(Comparator.comparing(CalendarEventDTO::startDate)).toList();
        Map<String, List<CalendarEventDTO>> calendarEventDTOsByDay = sortedDTOs.stream()
                .collect(Collectors.groupingBy(dto -> dto.startDate().withZoneSameInstant(clientTimeZone).toLocalDate().toString()));

        return ResponseEntity.ok(calendarEventDTOsByDay);
    }
}
