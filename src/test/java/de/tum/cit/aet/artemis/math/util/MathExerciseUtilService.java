package de.tum.cit.aet.artemis.math.util;

import static de.tum.cit.aet.artemis.core.config.ArtemisConstants.SPRING_PROFILE_TEST;

import java.time.ZonedDateTime;
import java.util.HashSet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.course.domain.Course;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.math.domain.MathExercise;
import de.tum.cit.aet.artemis.math.domain.MathSubmission;
import de.tum.cit.aet.artemis.math.repository.MathExerciseRepository;
import de.tum.cit.aet.artemis.math.repository.MathSubmissionRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class MathExerciseUtilService {

    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FAR_FUTURE = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private MathExerciseRepository mathExerciseRepository;

    @Autowired
    private MathSubmissionRepository mathSubmissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    public MathExercise addMathExerciseToCourse(Course course) {
        MathExercise exercise = MathExerciseFactory.generateMathExercise(PAST, FUTURE, FAR_FUTURE, course);
        return exerciseRepository.save(exercise);
    }

    public Course addCourseWithMathExercise() {
        Course course = CourseFactory.generateCourse(null, PAST, FAR_FUTURE, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        addMathExerciseToCourse(course);
        return course;
    }

    public MathExercise saveExercise(MathExercise exercise) {
        return mathExerciseRepository.save(exercise);
    }

    public MathSubmission createAndSaveSubmissionForExercise(MathExercise exercise, String login, boolean submitted) {
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        MathSubmission submission = MathExerciseFactory.generateMathSubmission(submitted);
        submission.setParticipation(participation);
        return mathSubmissionRepository.save(submission);
    }
}
