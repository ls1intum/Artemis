package de.tum.in.www1.artemis.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.time.ZonedDateTime;
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
import de.tum.in.www1.artemis.domain.notification.GroupNotification;
import de.tum.in.www1.artemis.repository.*;
import de.tum.in.www1.artemis.security.SecurityUtils;
import de.tum.in.www1.artemis.service.exam.ExamService;
import de.tum.in.www1.artemis.service.user.UserService;
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

    public CourseService(CourseRepository courseRepository, ExerciseService exerciseService, AuthorizationCheckService authCheckService, UserRepository userRepository,
            LectureService lectureService, GroupNotificationRepository groupNotificationRepository, ExerciseGroupRepository exerciseGroupRepository,
            AuditEventRepository auditEventRepository, UserService userService, LearningGoalRepository learningGoalRepository, GroupNotificationService groupNotificationService,
            ExamService examService, ExamRepository examRepository, CourseExamExportService courseExamExportService) {
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
            throw new AccessForbiddenException("You are not allowed to access this resource");
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
     *     <li>All Exercises including:
     *      submissions, participations, results, repositories and build plans, see {@link ExerciseService#delete}</li>
     *     <li>All Lectures and their Attachments, see {@link LectureService#delete}</li>
     *     <li>All GroupNotifications of the course, see {@link GroupNotificationRepository#delete}</li>
     *     <li>All default groups created by Artemis, see {@link UserService#deleteGroup}</li>
     *     <li>All Exams, see {@link ExamService#delete}</li>
     * </ul>
     *
     * @param course the course to be deleted
     */
    public void delete(Course course) {
        log.debug("Request to delete Course : {}", course.getTitle());

        deleteLearningGoalsOfCourse(course);
        deleteExercisesOfCourse(course);
        deleteLecturesOfCourse(course);
        deleteNotificationsOfCourse(course);
        deleteDefaultGroups(course);
        deleteExamsOfCourse(course);
        courseRepository.deleteById(course.getId());
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
        if (course.getStudentGroupName().equals(course.getDefaultStudentGroupName())) {
            userService.deleteGroup(course.getStudentGroupName());
        }
        if (course.getTeachingAssistantGroupName().equals(course.getDefaultTeachingAssistantGroupName())) {
            userService.deleteGroup(course.getTeachingAssistantGroupName());
        }
        if (course.getInstructorGroupName().equals(course.getDefaultInstructorGroupName())) {
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
        userService.addUserToGroup(user, course.getStudentGroupName());
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
     * @return An Integer array containing active students for each index. An index corresponds to a week
     */
    public Integer[] getActiveStudents(List<Long> exerciseIds) {
        ZonedDateTime now = ZonedDateTime.now();
        LocalDateTime localStartDate = now.toLocalDateTime().with(DayOfWeek.MONDAY);
        LocalDateTime localEndDate = now.toLocalDateTime().with(DayOfWeek.SUNDAY);
        ZoneId zone = now.getZone();
        ZonedDateTime startDate = localStartDate.atZone(zone).minusWeeks(3).withHour(0).withMinute(0).withSecond(0).withNano(0);
        ZonedDateTime endDate = localEndDate.atZone(zone).withHour(23).withMinute(59).withSecond(59);

        List<Map<String, Object>> outcome = courseRepository.getActiveStudents(exerciseIds, startDate, endDate);
        List<Map<String, Object>> distinctOutcome = removeDuplicateActiveUserRows(outcome, startDate);
        return sortUserIntoWeeks(distinctOutcome, endDate);
    }

    /**
     * The List of maps contains duplicated entries. This method compares the values and returns a List<Map<String, Object>>
     * without duplicated entries
     *
     * @param activeUserRows a list with a map for every submission of an user containing date and the username
     * @param startDate the startDate of the period
     * @return a List<Map<String, Object>> containing date and amount of active users in this period
     */
    private List<Map<String, Object>> removeDuplicateActiveUserRows(List<Map<String, Object>> activeUserRows, ZonedDateTime startDate) {
        Map<Object, List<String>> usersByDate = new HashMap<>();
        for (Map<String, Object> listElement : activeUserRows) {
            ZonedDateTime date = (ZonedDateTime) listElement.get("day");
            int index = getWeekOfDate(date);
            String username = listElement.get("username").toString();
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
        List<Map<String, Object>> returnList = new ArrayList<>();
        usersByDate.forEach((date, users) -> {
            int year = (Integer) date < getWeekOfDate(startDate) ? startDate.getYear() + 1 : startDate.getYear();
            ZonedDateTime firstDateOfYear = ZonedDateTime.of(year, 1, 1, 0, 0, 0, 0, startDate.getZone());
            ZonedDateTime start = getWeekOfDate(firstDateOfYear) == 1 ? firstDateOfYear.plusWeeks(((Integer) date) - 1) : firstDateOfYear.plusWeeks((Integer) date);
            Map<String, Object> listElement = new HashMap<>();
            listElement.put("day", start);
            listElement.put("amount", (long) users.size());
            returnList.add(listElement);
        });
        return returnList;
    }

    /**
     * Gets a list of maps as parameter, each map describing an entry in the database. The map has the two keys "day" and "amount",
     * which map to the date and the amount of the findings. This Map-List is taken and converted into an Integer array,
     * containing the values for each point of the graph. In the course management overview, we want to display the last
     * 4 weeks, each week represented by one point in the graph. (Beginning with the current week.)
     *
     * @param outcome A List<Map<String, Object>>, containing the content which should be refactored into an array
     * @param endDate the endDate
     * @return an array, containing the amount of active users. One entry corresponds to one week
     */
    private Integer[] sortUserIntoWeeks(List<Map<String, Object>> outcome, ZonedDateTime endDate) {
        Integer[] result = new Integer[4];
        Arrays.fill(result, 0);
        int week;
        for (Map<String, Object> map : outcome) {
            ZonedDateTime date = (ZonedDateTime) map.get("day");
            int amount = map.get("amount") != null ? ((Long) map.get("amount")).intValue() : 0;
            week = getWeekOfDate(date);
            for (int i = 0; i < result.length; i++) {
                if (week == getWeekOfDate(endDate.minusWeeks(i))) {
                    result[result.length - 1 - i] += amount;
                }
            }
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
                course.setCourseArchivePath(archivedCoursePath.get().toString());
                courseRepository.save(course);
            }
            else {
                groupNotificationService.notifyInstructorGroupAboutCourseArchiveState(course, NotificationType.COURSE_ARCHIVE_FAILED, exportErrors);
                return;
            }
        }
        catch (IOException e) {
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
        var course = courseRepository.findWithEagerExercisesAndLecturesById(courseId);
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

    public void addUserToGroup(User user, String group) {
        userService.addUserToGroup(user, group);
    }

    public void removeUserFromGroup(User user, String group) {
        userService.removeUserFromGroup(user, group);
    }
}
