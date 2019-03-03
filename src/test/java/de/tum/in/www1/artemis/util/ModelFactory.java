package de.tum.in.www1.artemis.util;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.ModelingExercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;

import java.time.ZonedDateTime;
import java.util.*;

public class ModelFactory {

  public static ModelingExercise generateModelingExercise(
      ZonedDateTime releaseDate,
      ZonedDateTime dueDate,
      ZonedDateTime assessmentDueDate,
      Course course) {
    ModelingExercise exercise = new ModelingExercise();
    exercise.setTitle(UUID.randomUUID().toString());
    exercise.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
    exercise.setMaxScore(5.0);
    exercise.setReleaseDate(releaseDate);
    exercise.setDueDate(dueDate);
    exercise.assessmentDueDate(assessmentDueDate);
    exercise.setDiagramType(DiagramType.CLASS);
    exercise.setCourse(course);
    exercise.setParticipations(new HashSet<>());
    exercise.setExampleSubmissions(new HashSet<>());
    exercise.setTutorParticipations(new HashSet<>());
    exercise.setDifficulty(DifficultyLevel.MEDIUM);
    exercise.setCategories(new LinkedList<>());
    return exercise;
  }

  public static LinkedList<User> generateActivatedUsers(
      String loginPrefix, String[] groups, int amount) {
    LinkedList<User> generatedUsers = new LinkedList<>();
    for (int i = 1; i <= amount; i++) {
      User student = ModelFactory.generateActivatedUser(loginPrefix + i);
      student.setGroups(Arrays.asList(groups));
      generatedUsers.add(student);
    }
    return generatedUsers;
  }

  public static User generateActivatedUser(String login) {
    User user = new User();
    user.setLogin(login);
    user.setPassword("0000");
    user.setFirstName(login + "First");
    user.setLastName(login + "Last");
    user.setEmail(login + "@test.de");
    user.setActivated(true);
    user.setLangKey("en");
    user.setGroups(new LinkedList<>());
    user.setAuthorities(new HashSet<>());
    user.setPersistentTokens(new HashSet<>());
    return user;
  }

  public static Course generateCourse(
      Long id, ZonedDateTime startDate, ZonedDateTime endDate, Set<Exercise> exercises) {
    return generateCourse(
        id,
        startDate,
        endDate,
        exercises,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString());
  }

  public static Course generateCourse(
      Long id,
      ZonedDateTime startDate,
      ZonedDateTime endDate,
      Set<Exercise> exercises,
      String studentGroupName,
      String teachingAssistantGroupName,
      String instructorGroupName) {
    Course course = new Course();
    course.setId(id);
    course.setTitle(UUID.randomUUID().toString());
    course.setDescription(UUID.randomUUID().toString());
    course.setShortName("t" + UUID.randomUUID().toString().substring(0, 3));
    course.setStudentGroupName(studentGroupName);
    course.setTeachingAssistantGroupName(teachingAssistantGroupName);
    course.setInstructorGroupName(instructorGroupName);
    course.setStartDate(startDate);
    course.setEndDate(endDate);
    course.setMaxComplaints(5);
    course.setExercises(exercises);
    course.setOnlineCourse(false);
    return course;
  }
}
