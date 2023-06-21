package de.tum.in.www1.artemis.course;

import java.time.ZonedDateTime;
import java.util.Set;
import java.util.UUID;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.OnlineCourseConfiguration;
import de.tum.in.www1.artemis.domain.enumeration.CourseInformationSharingConfiguration;

/**
 * Factory for creating Courses and related objects.
 */
public class CourseFactory {

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
        return generateCourse(id, startDate, endDate, exercises, null, null, null, null);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName) {
        return generateCourse(id, startDate, endDate, exercises, studentGroupName, teachingAssistantGroupName, editorGroupName, instructorGroupName, 3, 3, 7, 2000, 2000, true,
                false, 7);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName, boolean messagingEnabled) {
        return generateCourse(id, startDate, endDate, exercises, studentGroupName, teachingAssistantGroupName, editorGroupName, instructorGroupName, 3, 3, 7, 2000, 2000, true,
                messagingEnabled, 7);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName, Integer maxComplaints, Integer maxTeamComplaints, Integer maxComplaintTimeDays,
            int maxComplaintTextLimit, int maxComplaintResponseTextLimit, boolean communicationEnabled, boolean messagingEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Course title " + UUID.randomUUID());

        // must start with a letter
        course.setShortName("short" + UUID.randomUUID().toString().replace("-", "0"));
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setMaxComplaintTextLimit(maxComplaintTextLimit);
        course.setMaxComplaintResponseTextLimit(maxComplaintResponseTextLimit);
        if (communicationEnabled && messagingEnabled) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_AND_MESSAGING);
        }
        else if (communicationEnabled && !messagingEnabled) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.COMMUNICATION_ONLY);
        }
        else if (!communicationEnabled && messagingEnabled) {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.MESSAGING_ONLY);
        }
        else {
            course.setCourseInformationSharingConfiguration(CourseInformationSharingConfiguration.DISABLED);
        }
        course.setMaxRequestMoreFeedbackTimeDays(requestMoreFeedbackTimeDays);
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setEditorGroupName(editorGroupName);
        course.setInstructorGroupName(instructorGroupName);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setExercises(exercises);
        course.setOnlineCourse(false);
        course.setEnrollmentEnabled(false);
        course.setPresentationScore(2);
        course.setAccuracyOfScores(1);
        return course;
    }

    public static OnlineCourseConfiguration generateOnlineCourseConfiguration(Course course, String key, String secret, String userPrefix, String originalUrl) {
        OnlineCourseConfiguration onlineCourseConfiguration = new OnlineCourseConfiguration();
        updateOnlineCourseConfiguration(onlineCourseConfiguration, key, secret, userPrefix, originalUrl, UUID.randomUUID().toString());
        course.setOnlineCourseConfiguration(onlineCourseConfiguration);
        return onlineCourseConfiguration;
    }

    public static void updateOnlineCourseConfiguration(OnlineCourseConfiguration onlineCourseConfiguration, String key, String secret, String userPrefix, String originalUrl,
            String registrationId) {
        onlineCourseConfiguration.setLtiKey(key);
        onlineCourseConfiguration.setLtiSecret(secret);
        onlineCourseConfiguration.setUserPrefix(userPrefix);
        onlineCourseConfiguration.setOriginalUrl(originalUrl);
        onlineCourseConfiguration.setRegistrationId(registrationId);
    }
}
