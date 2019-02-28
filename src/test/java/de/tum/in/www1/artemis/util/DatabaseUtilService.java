package de.tum.in.www1.artemis.util;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.Participation;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.repository.CourseRepository;
import de.tum.in.www1.artemis.repository.ExerciseRepository;
import de.tum.in.www1.artemis.repository.ParticipationRepository;
import de.tum.in.www1.artemis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Service
public class DatabaseUtilService {
  @Autowired CourseRepository courseRepo;
  @Autowired ExerciseRepository exerciseRepo;
  @Autowired UserRepository userRepo;
  @Autowired ParticipationRepository participationRepo;

  private static ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);
  private static ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);
  private static ZonedDateTime futureFututreTimestamp = ZonedDateTime.now().plusDays(2);

  public void reset() {
    participationRepo.deleteAll();
    exerciseRepo.deleteAll();
    courseRepo.deleteAll();
    userRepo.deleteAll();
    assertThat(courseRepo.findAll()).as("course data has been cleared").isEmpty();
    assertThat(exerciseRepo.findAll()).as("exercise data has been cleared").isEmpty();
    assertThat(userRepo.findAll()).as("user data has been cleared").isEmpty();
  }

  public void addParticipationForExercise(Exercise exercise, String login) {
    User user =
        userRepo
            .findOneByLogin(login)
            .orElseThrow(
                () -> new IllegalArgumentException("Provided login does not exist in database"));
    Participation participation = new Participation();
    participation.setStudent(user);
    participation.setExercise(exercise);
    participationRepo.save(participation);
    Participation storedParticipation =
        participationRepo.findOneByExerciseIdAndStudentLogin(exercise.getId(), login);
    assertThat(storedParticipation).isNotNull();
  }

  public void addUsers() {
    User student1 = ModelGenrator.generateActivatedUser("student1");
    User student2 = ModelGenrator.generateActivatedUser("student2");
    String[] groups = {"tumuser"};
    student1.setGroups(Arrays.asList(groups));
    student2.setGroups(Arrays.asList(groups));
    groups = new String[] {"tutor"};
    User tutor1 = ModelGenrator.generateActivatedUser("tutor1");
    tutor1.setGroups(Arrays.asList(groups));
    User[] users = {student1, student2, tutor1};
    userRepo.saveAll(Arrays.asList(users));
    assertThat(userRepo.findAll())
        .as("users are correctly stored")
        .containsExactlyInAnyOrder(users);
  }

  public void addCourseWithModelingExercise() {
    Course course =
        ModelGenrator.generateCourse(
            null,
            pastTimestamp,
            futureFututreTimestamp,
            new HashSet<>(),
            "tumuser",
            "tutor",
            "tutor");
    Exercise exercise =
        ModelGenrator.generateModelingExercise(
            pastTimestamp, futureTimestamp, futureFututreTimestamp, course);
    course.addExercises(exercise);
    courseRepo.save(course);
    exerciseRepo.save(exercise);
    List<Course> courseRepoContent = courseRepo.findAllWithEagerExercises();
    List<Exercise> exerciseRepoContent = exerciseRepo.findAll();
    assertThat(exerciseRepoContent.size()).as("a exercise got stored").isEqualTo(1);
    assertThat(courseRepoContent.size()).as("a course got stored").isEqualTo(1);
    assertThat(courseRepoContent.get(0).getExercises().size())
        .as("Course contains exercise")
        .isEqualTo(1);
    assertThat(courseRepoContent.get(0).getExercises().contains(exerciseRepoContent.get(0)))
        .as("course contains the right exercise")
        .isTrue();
  }
}
