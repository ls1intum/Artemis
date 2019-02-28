package de.tum.in.www1.artemis.util;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.DifficultyLevel;

import java.time.ZonedDateTime;
import java.util.*;

public class ModelGenrator {

  public static ModelingExercise generateModelingExercise(
      ZonedDateTime releaseDate,
      ZonedDateTime dueDate,
      ZonedDateTime assessmentDueDate,
      Course course) {
    return new ModelingExercise(
        UUID.randomUUID().toString(),
        "t" + UUID.randomUUID().toString().substring(0, 3),
        releaseDate,
        dueDate,
        assessmentDueDate,
        5.0,
        "",
        "",
        new LinkedList<String>(),
        DifficultyLevel.MEDIUM,
        new HashSet<Participation>(),
        new HashSet<TutorParticipation>(),
        course,
        new HashSet<ExampleSubmission>(),
        DiagramType.CLASS,
        "",
        "");
  }

  public static LinkedList<User> generateActivatedUsers(
      String loginPrefix, String[] groups, int amount) {
    LinkedList<User> generatedUsers = new LinkedList<>();
    for (int i = 1; i <= amount; i++) {
      User student = ModelGenrator.generateActivatedUser(loginPrefix + i);
      student.setGroups(Arrays.asList(groups));
      generatedUsers.add(student);
    }
    return generatedUsers;
  }

  public static User generateActivatedUser(String login) {
    return new User(
        login,
        "0000",
        login + "First",
        login + "Last",
        login + "@test.de",
        true,
        "de",
        "",
        null,
        null,
        null,
        new LinkedList<String>(),
        new HashSet<Authority>(),
        new HashSet<PersistentToken>());
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
    return new Course(
        id,
        UUID.randomUUID().toString(),
        UUID.randomUUID().toString(),
        "t" + UUID.randomUUID().toString().substring(0, 3),
        studentGroupName,
        teachingAssistantGroupName,
        instructorGroupName,
        startDate,
        endDate,
        false,
        5,
        exercises);
  }
}
