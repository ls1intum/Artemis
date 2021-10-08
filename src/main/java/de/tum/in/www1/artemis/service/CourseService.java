package de.tum.in.www1.artemis.service;

import static de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException.NOT_ALLOWED;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalField;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.config.Constants;
import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.NotificationType;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.ExerciseGroup;
import de.tum.in.www1.artemis.domain.metis.Post;
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.domain.statistics.StatisticsEntry;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.repository.metis.PostRepository;
import de.tum.in.www1.artemis.security.Role;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.user.UserService;
import de.tum.in.www1.artemis.web.rest.dto.CourseManagementDetailViewDTO;
import de.tum.in.www1.artemis.web.rest.errors.AccessForbiddenException;

/**
 * Service Implementation for managing Course.
 */
@Service
public class CourseService {

    @Value("${artemis.course-archives-path}")
    private String courseArchivesDirPath;

    private final Logger log = LoggerFactory.getLogger(CourseService.class);

    private final ExerciseService exerciseService;

    private final AuthorizationCheckService authCheckService;

    private final LectureService lectureService;

    private final GroupNotificationRepository groupNotificationRepository;

    private final UserService userService;

    private final ExerciseGroupRepository exerciseGroupRepository;

    private final CourseExamExportService courseExamExportService;

    private final ExamService examService;

    private final ExamRepository examRepository;

    private final GroupNotificationService groupNotificationService;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final LearningGoalRepository learningGoalRepository;

    private final GradingScaleRepository gradingScaleRepository;

    private final PostRepository postRepository;

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService, UserRepository userRepository,
            LectureService lectureService, GroupNotificationRepository groupNotificationRepository, ExerciseGroupRepository exerciseGroupRepository,
            AuditEventRepository auditEventRepository, UserService userService, LearningGoalRepository learningGoalRepository, GroupNotificationService groupNotificationService,
            ExamService examService, ExamRepository examRepository, CourseExamExportService courseExamExportService, GradingScaleRepository gradingScaleRepository,
            PostRepository postRepository) {
        this.courseRepository = courseRepository;
        this.exerciseService = exerciseService;
        this.authCheckService = authCheckService;
        this.userRepository = userRepository;
        this.lectureService = lectureService;
        this.groupNotificationRepository = groupNotificationRepository;
        this.exerciseGroupRepository = exerciseGroupRepository;
        this.auditEventRepository = auditEventRepository;
        this.userService = userService;
        this.learningGoalRepository = learningGoalRepository;
        this.groupNotificationService = groupNotificationService;
        this.examService = examService;
        this.examRepository = examRepository;
        this.courseExamExportService = courseExamExportService;
        this.gradingScaleRepository = gradingScaleRepository;
        this.postRepository = postRepository;
    }

    /**
     * Get one course with exercises, lectures and exams (filtered for given user)
     *
     * @param courseId the course to fetch
     * @param user     the user entity
     * @return the course including exercises, lectures and exams for the user
     */
    public Course findOneWithExercisesAndLecturesAndExamsForUser(Long courseId, User user) {
        Course course = courseRepository.findByIdWithLecturesAndExamsElseThrow(courseId);
        if (!authCheckService.isAtLeastStudentInCourse(course, user)) {
            throw new AccessForbiddenException(NOT_ALLOWED);
        }
        course.setExercises(exerciseService.findAllForCourse(course, user));
        course.setLectures(lectureService.filterActiveAttachments(course.getLectures(), user));
        if (authCheckService.isOnlyStudentInCourse(course, user)) {
            course.setExams(examRepository.filterVisibleExams(course.getExams()));
        }
        return course;
    }

    /**
     * Get all courses for the given user
     *
     * @param user the user entity
     * @return the list of all courses for the user
     */
    public List<Course> findAllActiveForUser(User user) {
        return courseRepository.findAllActive(ZonedDateTime.now()).stream().filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now()))
                .filter(course -> isActiveCourseVisibleForUser(user, course)).collect(Collectors.toList());
    }

    /**
     * Get all courses with exercises, lectures  and exams (filtered for given user)
     *
     * @param user the user entity
     * @return the list of all courses including exercises, lectures and exams for the user
     */
    public List<Course> findAllActiveWithExercisesAndLecturesAndExamsForUser(User user) {
        return courseRepository.findAllActiveWithLecturesAndExams().stream()
                // filter old courses and courses the user should not be able to see
                // skip old courses that have already finished
                .filter(course -> course.getEndDate() == null || course.getEndDate().isAfter(ZonedDateTime.now())).filter(course -> isActiveCourseVisibleForUser(user, course))
                .peek(course -> {
                    course.setExercises(exerciseService.findAllForCourse(course, user));
                    course.setLectures(lectureService.filterActiveAttachments(course.getLectures(), user));
                    if (authCheckService.isOnlyStudentInCourse(course, user)) {
                        course.setExams(examRepository.filterVisibleExams(course.getExams()));
                    }
                }).collect(Collectors.toList());
    }

    private boolean isActiveCourseVisibleForUser(User user, Course course) {
        // Instructors and TAs see all courses that have not yet finished
        if (authCheckService.isAtLeastTeachingAssistantInCourse(course, user)) {
            return true;
        }
        // Students see all courses that have already started (and not yet finished)
        if (user.getGroups().contains(course.getStudentGroupName())) {
            return course.getStartDate() == null || course.getStartDate().isBefore(ZonedDateTime.now());
        }

        return false;
    }

    /**
     * Deletes all elements associated with the course including:
     * <ul>
     *     <li>The Course</li>
     *     <li>All Posts in that course{@link PostRepository#delete}</li>
     *     <li>All Exercises including:
     *      submissions, participations, results, repositories and build plans, see {@link ExerciseService#delete}</li>
     *     <li>All Lectures and their Attachments, see {@link LectureService#delete}</li>
     *     <li>All GroupNotifications of the course, see {@link GroupNotificationRepository#delete}</li>
     *     <li>All default groups created by Artemis, see {@link UserService#deleteGroup}</li>
     *     <li>All Exams, see {@link ExamService#delete}</li>
     *     <li>The Grading Scale if such exists, see {@link GradingScaleRepository#delete}</li>
     * </ul>
     *
     * @param course the course to be deleted
     */
    public void delete(Course course) {
        log.debug("Request to delete Course : {}", course.getTitle());

        deleteAllPostsOfCourse(course);
        deleteLearningGoalsOfCourse(course);
        deleteExercisesOfCourse(course);
        deleteLecturesOfCourse(course);
        deleteNotificationsOfCourse(course);
        deleteDefaultGroups(course);
        deleteExamsOfCourse(course);
        deleteGradingScaleOfCourse(course);
        courseRepository.deleteById(course.getId());
    }

    private void deleteAllPostsOfCourse(Course course) {
        List<Post> postsInCourse = postRepository.findPostsForCourse(course.getId());
        postRepository.deleteAllInBatch(postsInCourse);
    }

    private void deleteGradingScaleOfCourse(Course course) {
        // delete course grading scale if it exists
        Optional<GradingScale> gradingScale = gradingScaleRepository.findByCourseId(course.getId());
        gradingScale.ifPresent(gradingScaleRepository::delete);
    }

    private void deleteExamsOfCourse(Course course) {
        // delete the Exams
        List<Exam> exams = examRepository.findByCourseId(course.getId());
        for (Exam exam : exams) {
            examService.delete(exam.getId());
        }
    }

    private void deleteDefaultGroups(Course course) {
        // only delete (default) groups which have been created by Artemis before
        if (Objects.equals(course.getStudentGroupName(), course.getDefaultStudentGroupName())) {
            userService.deleteGroup(course.getStudentGroupName());
        }
        if (Objects.equals(course.getTeachingAssistantGroupName(), course.getDefaultTeachingAssistantGroupName())) {
            userService.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (Objects.equals(course.getEditorGroupName(), course.getDefaultEditorGroupName())) {
            userService.deleteGroup(course.getEditorGroupName());
        }
        if (Objects.equals(course.getInstructorGroupName(), course.getDefaultInstructorGroupName())) {
            userService.deleteGroup(course.getInstructorGroupName());
        }
    }

    private void deleteNotificationsOfCourse(Course course) {
        List<GroupNotification> notifications = groupNotificationRepository.findAllByCourseId(course.getId());
        for (GroupNotification notification : notifications) {
            groupNotificationRepository.delete(notification);
        }
    }

    private void deleteLecturesOfCourse(Course course) {
        for (Lecture lecture : course.getLectures()) {
            lectureService.delete(lecture);
        }
    }

    private void deleteExercisesOfCourse(Course course) {
        for (Exercise exercise : course.getExercises()) {
            exerciseService.delete(exercise.getId(), true, true);
        }
    }

    private void deleteLearningGoalsOfCourse(Course course) {
        for (LearningGoal learningGoal : course.getLearningGoals()) {
            learningGoalRepository.deleteById(learningGoal.getId());
        }
    }

    /**
     * Given a Course object, it returns the number of users enrolled in the course
     *
     * @param course - the course object we are interested in
     * @return the number of students for that course
     */
    public long countNumberOfStudentsForCourse(Course course) {
        String groupName = course.getStudentGroupName();
        return userRepository.countByGroupsIsContaining(groupName);
    }

    /**
     * If the exercise is part of an exam, retrieve the course through ExerciseGroup -> Exam -> Course.
     * Otherwise the course is already set and the id can be used to retrieve the course from the database.
     *
     * @param exercise the Exercise for which the course is retrieved
     * @return the Course of the Exercise
     */
    public Course retrieveCourseOverExerciseGroupOrCourseId(Exercise exercise) {

        if (exercise.isExamExercise()) {
            ExerciseGroup exerciseGroup = exerciseGroupRepository.findByIdElseThrow(exercise.getExerciseGroup().getId());
            exercise.setExerciseGroup(exerciseGroup);
            return exerciseGroup.getExam().getCourse();
        }
        else {
            Course course = courseRepository.findByIdElseThrow(exercise.getCourseViaExerciseGroupOrCourseMember().getId());
            exercise.setCourse(course);
            return course;
        }
    }

    /**
     * Registers a user in a course by adding him to the student group of the course
     *
     * @param user   The user that should get added to the course
     * @param course The course to which the user should get added to
     */
    public void registerUserForCourse(User user, Course course) {
        userService.addUserToGroup(user, course.getStudentGroupName(), Role.STUDENT);
        final var auditEvent = new AuditEvent(user.getLogin(), Constants.REGISTER_FOR_COURSE, "course=" + course.getTitle());
        auditEventRepository.add(auditEvent);
        log.info("User {} has successfully registered for course {}", user.getLogin(), course.getTitle());
    }

    /**
     * Fetches a list of Courses
     *
     * @param onlyActive Whether or not to include courses with a past endDate
     * @return A list of Courses for the course management overview
     */
    public List<Course> getAllCoursesForManagementOverview(boolean onlyActive) {
        var dateTimeNow = onlyActive ? ZonedDateTime.now() : null;
        var user = userRepository.getUserWithGroupsAndAuthorities();
        var userGroups = new ArrayList<>(user.getGroups());
        return courseRepository.getAllCoursesForManagementOverview(dateTimeNow, authCheckService.isAdmin(user), userGroups);
    }

    /**
     * Get the active students for these particular exercise ids
     *
     * @param exerciseIds the ids to get the active students for
     * @param periodIndex the deviation from the current time
     * @param length the length of the chart which we want to fill. This can either be 4 for the course overview or 16 for the courde detail view
     * @return An Integer array containing active students for each index. An index corresponds to a week
     */
    public Integer[] getActiveStudents(Set<Long> exerciseIds, Integer periodIndex, int length) {
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime localStartDate = now.toLocalDateTime().with(DayOfWeek.MONDAY);
        LocalDateTime localEndDate = now.toLocalDateTime().with(DayOfWeek.SUNDAY);
        ZoneId zone = now.getZone();
        // startDate is the starting point of the data collection which is the Monday 3 weeks ago +/- the deviation from the current timeframe
        ZonedDateTime startDate = localStartDate.atZone(zone).minusWeeks((length - 1) + (length * (-periodIndex))).withHour(0).withMinute(0).withSecond(0).withNano(0);
        // the endDate depends on whether the current week is shown. If it is, the endDate is the Sunday of the current week at 23:59.
        // If the timeframe was adapted (periodIndex != 0), the endDate needs to be adapted according to the deviation
        ZonedDateTime endDate = periodIndex != 0 ? localEndDate.atZone(zone).minusWeeks(length * (-periodIndex)).withHour(23).withMinute(59).withSecond(59)
                : localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);
        List<StatisticsEntry> outcome = courseRepository.getActiveStudents(exerciseIds, startDate, endDate);
        List<StatisticsEntry> distinctOutcome = removeDuplicateActiveUserRows(outcome, startDate);
        return sortUserIntoWeeks(distinctOutcome, startDate, length);
    }

    /**
     * The List of StatisticsEntries can contain duplicated entries, which means that a user has two entries in the same week.
     * This method compares the values and returns a List<StatisticsEntry> without duplicated entries.
     *
     * @param activeUserRows a list of entries
     * @param startDate the startDate of the period
     * @return a List<StatisticsEntry> containing date and amount of active users in this period
     */

    private List<StatisticsEntry> removeDuplicateActiveUserRows(List<StatisticsEntry> activeUserRows, ZonedDateTime startDate) {
        int startIndex = getWeekOfDate(startDate);
        Map<Object, List<String>> usersByDate = new HashMap<>();
        for (StatisticsEntry listElement : activeUserRows) {
            // listElement.date has the form "2021-05-04", to convert it to ZonedDateTime, it needs a time
            String dateOfElement = listElement.getDate() + " 10:00";
            var zone = startDate.getZone();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            ZonedDateTime date = LocalDateTime.parse(dateOfElement, formatter).atZone(zone);
            int index = getWeekOfDate(date);
            // the database stores entries in UTC, so it can happen that entries have a date one date before the startDate
            index = index == startIndex - 1 ? startIndex : index;
            String username = listElement.getUsername();
            List<String> usersInSameSlot = usersByDate.get(index);
            // if this index is not yet existing in users
            if (usersInSameSlot == null) {
                usersInSameSlot = new ArrayList<>();
                usersInSameSlot.add(username);
                usersByDate.put(index, usersInSameSlot);
            }   // if the value of the map for this index does not contain this username
            else if (!usersInSameSlot.contains(username)) {
                usersInSameSlot.add(username);
            }
        }
        List<StatisticsEntry> returnList = new ArrayList<>();
        usersByDate.forEach((date, users) -> {
            int year = (Integer) date < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
            ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
            ZonedDateTime start = getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(((Integer) date) - 1) : firstDateOfYear.plusWeeks((Integer) date);
            StatisticsEntry listElement = new StatisticsEntry(start, users.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    /**
     * Gets a list of maps as parameter, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This Map-List is taken and converted into an Integer array,
     * containing the values for each point of the graph. In the course management overview, we want to display the last
     * 4 weeks, each week represented by one point in the graph. (Beginning with the current week.) In the course detail view,
     * we display 16 weeks at once.
     *
     * @param outcome A List<StatisticsEntry>, containing the content which should be refactored into an array
     * @param startDate the startDate
     * @param length the length of the chart which we want to fill. This can either be 4 for the course overview or 16 for the courde detail view
     * @return an array, containing the amount of active users. One entry corresponds to one week
     */
    private Integer[] sortUserIntoWeeks(List<StatisticsEntry> outcome, ZonedDateTime startDate, int length) {
        Integer[] result = new Integer[length];
        Arrays.fill(result, 0);
        for (StatisticsEntry map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.getDay();
            int amount = Math.toIntExact(map.getAmount());
            int dateWeek = getWeekOfDate(date);
            int startDateWeek = getWeekOfDate(startDate);
            int weeksDifference;
            weeksDifference = dateWeek < startDateWeek ? dateWeek == startDateWeek - 1 ? 0 : dateWeek + 53 - startDateWeek : dateWeek - startDateWeek;
            result[weeksDifference] += amount;
        }
        return result;
    }

    /**
     * Gets the week of the given date
     *
     * @param date the date to get the week for
     * @return the calendar week of the given date
     */
    private Integer getWeekOfDate(ZonedDateTime date) {
        LocalDate localDate = date.toLocalDate();
        TemporalField weekOfYear = WeekFields.of(DayOfWeek.MONDAY, 4).weekOfWeekBasedYear();
        return localDate.get(weekOfYear);
    }

    /**
     * Fetches Course Management Detail View data from repository and returns a DTO
     *
     * @param courseId id of the course
     * @param exerciseIds the ids of the exercises the course contains
     * @return The DTO for the course management detail view
     */
    public CourseManagementDetailViewDTO getStatsForDetailView(Long courseId, Set<Long> exerciseIds) {
        var dto = new CourseManagementDetailViewDTO();
        var course = this.courseRepository.findByIdElseThrow(courseId);

        dto.setNumberOfStudentsInCourse(Math.toIntExact(userRepository.countUserInGroup(course.getStudentGroupName())));
        dto.setNumberOfTeachingAssistantsInCourse(Math.toIntExact(userRepository.countUserInGroup(course.getTeachingAssistantGroupName())));
        dto.setNumberOfEditorsInCourse(Math.toIntExact(userRepository.countUserInGroup(course.getEditorGroupName())));
        dto.setNumberOfInstructorsInCourse(Math.toIntExact(userRepository.countUserInGroup(course.getInstructorGroupName())));

        dto.setActiveStudents(getActiveStudents(exerciseIds, 0, 16));
        return dto;
    }

    /**
     * Archives the course by creating a zip file will student submissions for
     * both the course exercises and exams.
     *
     * @param course the course to archive
     */
    @Async
    public void archiveCourse(Course course) {
        SecurityUtils.setAuthorizationObject();

        // Archiving a course is only possible after the course is over
        if (ZonedDateTime.now().isBefore(course.getEndDate())) {
            return;
        }

        // This contains possible errors encountered during the archive process
        ArrayList<String> exportErrors = new ArrayList<>();

        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_STARTED, exportErrors);

        try {
            // Create course archives directory if it doesn't exist
            Files.createDirectories(Path.of(courseArchivesDirPath));
            log.info("Created the course archives directory at {} because it didn't exist.", courseArchivesDirPath);

            // Export the course to the archives directory.
            var archivedCoursePath = courseExamExportService.exportCourse(course, courseArchivesDirPath, exportErrors);

            // Attach the path to the archive to the course and save it in the database
            if (archivedCoursePath.isPresent()) {
                course.setCourseArchivePath(archivedCoursePath.get().getFileName().toString());
                courseRepository.save(course);
            }
            else {
                groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FAILED, exportErrors);
                return;
            }
        }
        catch (Exception e) {
            var error = "Failed to create course archives directory " + courseArchivesDirPath + ": " + e.getMessage();
            exportErrors.add(error);
            log.info(error);
        }

        groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FINISHED, exportErrors);
    }

    /**
     * Cleans up a course by cleaning up all exercises from that course. This deletes all student
     * submissions. Note that a course has to be archived first before being cleaned up.
     *
     * @param courseId The id of the course to clean up
     */
    public void cleanupCourse(Long courseId) {
        // Get the course with all exercises
        var course = courseRepository.findByIdWithExercisesAndLecturesElseThrow(courseId);
        if (!course.hasCourseArchive()) {
            log.info("Cannot clean up course {} because it hasn't been archived.", courseId);
            return;
        }

        // The Objects::nonNull is needed here because the relationship exam -> exercise groups is ordered and
        // hibernate sometimes adds nulls to in the list of exercise groups to keep the order
        Set<Exercise> examExercises = examRepository.findByCourseIdWithExerciseGroupsAndExercises(courseId).stream().map(Exam::getExerciseGroups).flatMap(Collection::stream)
                .filter(Objects::nonNull).map(ExerciseGroup::getExercises).flatMap(Collection::stream).collect(Collectors.toSet());

        var exercisesToCleanup = Stream.concat(course.getExercises().stream(), examExercises.stream()).collect(Collectors.toSet());
        exercisesToCleanup.forEach(exercise -> {
            if (exercise instanceof ProgrammingExercise) {
                exerciseService.cleanup(exercise.getId(), true);
            }

            // TODO: extend exerciseService.cleanup to clean up all exercise types
        });

        log.info("The course {} has been cleaned up!", courseId);
    }

    public void addUserToGroup(User user, String group, Role role) {
        userService.addUserToGroup(user, group, role);
    }

    public void removeUserFromGroup(User user, String group) {
        userService.removeUserFromGroup(user, group);
    }
}
