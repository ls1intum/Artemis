package de.tum.in.www1.artemis.usermanagement.util;

import java.time.ZonedDateTime;
import java.util.*;

import de.tum.in.www1.artemis.domain.Authority;
import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;

public class ModelFactory {

    public static final String USER_PASSWORD = "0000";

    public static List<User> generateActivatedUsers(String loginPrefix, String[] groups, Set<Authority> authorities, int amount) {
        List<User> generatedUsers = new ArrayList<>();
        for (int i = 1; i <= amount; i++) {
            User user = ModelFactory.generateActivatedUser(loginPrefix + i);
            if (groups != null) {
                user.setGroups(Set.of(groups));
                user.setAuthorities(authorities);
            }
            generatedUsers.add(user);
        }
        return generatedUsers;
    }

    public static User generateActivatedUser(String login, String password) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(password);
        user.setFirstName(login + "First");
        user.setLastName(login + "Last");
        user.setEmail(login + "@test.de");
        user.setActivated(true);
        user.setLangKey("en");
        user.setGroups(new HashSet<>());
        user.setAuthorities(new HashSet<>());
        return user;
    }

    public static User generateActivatedUser(String login) {
        return generateActivatedUser(login, USER_PASSWORD);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
                                        String teachingAssistantGroupName, String editorGroupName, String instructorGroupName) {
        return generateCourse(id, startDate, endDate, exercises, studentGroupName, teachingAssistantGroupName, editorGroupName, instructorGroupName, 3, 3, 7, true, 7);
    }

    public static Course generateCourse(Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises, String studentGroupName,
            String teachingAssistantGroupName, String editorGroupName, String instructorGroupName, Integer maxComplaints, Integer maxTeamComplaints, Integer maxComplaintTimeDays,
            boolean postsEnabled, int requestMoreFeedbackTimeDays) {
        Course course = new Course();
        course.setId(id);
        course.setTitle("Course title " + UUID.randomUUID().toString());

        // must start with a letter
        course.setShortName("short" + UUID.randomUUID().toString().replace("-", "0"));
        course.setMaxComplaints(maxComplaints);
        course.setMaxTeamComplaints(maxTeamComplaints);
        course.setMaxComplaintTimeDays(maxComplaintTimeDays);
        course.setPostsEnabled(postsEnabled);
        course.setMaxRequestMoreFeedbackTimeDays(requestMoreFeedbackTimeDays);
        course.setStudentGroupName(studentGroupName);
        course.setTeachingAssistantGroupName(teachingAssistantGroupName);
        course.setEditorGroupName(editorGroupName);
        course.setInstructorGroupName(instructorGroupName);
        course.setStartDate(startDate);
        course.setEndDate(endDate);
        course.setExercises(exercises);
        course.setOnlineCourse(false);
        course.setPresentationScore(2);
        return course;
    }
}
