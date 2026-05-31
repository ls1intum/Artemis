package de.tum.cit.aet.artemis.proof.util;

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
import de.tum.cit.aet.artemis.proof.domain.ProofExercise;
import de.tum.cit.aet.artemis.proof.domain.ProofSubmission;
import de.tum.cit.aet.artemis.proof.repository.ProofExerciseRepository;
import de.tum.cit.aet.artemis.proof.repository.ProofSubmissionRepository;

@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProofExerciseUtilService {

    private static final ZonedDateTime PAST = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime FAR_FUTURE = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    @Autowired
    private ProofExerciseRepository proofExerciseRepository;

    @Autowired
    private ProofSubmissionRepository proofSubmissionRepository;

    @Autowired
    private ParticipationUtilService participationUtilService;

    public ProofExercise addProofExerciseToCourse(Course course) {
        ProofExercise exercise = ProofExerciseFactory.generateProofExercise(PAST, FUTURE, FAR_FUTURE, course);
        return exerciseRepository.save(exercise);
    }

    public Course addCourseWithProofExercise() {
        Course course = CourseFactory.generateCourse(null, PAST, FAR_FUTURE, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        addProofExerciseToCourse(course);
        return course;
    }

    public ProofExercise saveExercise(ProofExercise exercise) {
        return proofExerciseRepository.save(exercise);
    }

    public ProofSubmission createAndSaveSubmissionForExercise(ProofExercise exercise, String login, boolean submitted) {
        var participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        ProofSubmission submission = ProofExerciseFactory.generateProofSubmission(submitted);
        submission.setParticipation(participation);
        return proofSubmissionRepository.save(submission);
    }
}
