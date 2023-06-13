package de.tum.in.www1.artemis.plagiarism;

import java.util.HashSet;
import java.util.Set;

import org.springframework.stereotype.Service;

import de.tum.in.www1.artemis.domain.*;
import de.tum.in.www1.artemis.domain.enumeration.DiagramType;
import de.tum.in.www1.artemis.domain.enumeration.InitializationState;
import de.tum.in.www1.artemis.domain.enumeration.Language;
import de.tum.in.www1.artemis.domain.modeling.ModelingExercise;
import de.tum.in.www1.artemis.domain.modeling.ModelingSubmission;
import de.tum.in.www1.artemis.domain.participation.StudentParticipation;
import de.tum.in.www1.artemis.util.ModelFactory;

@Service
public class PlagiarismTestService {

    public Course addCourseWithOneFinishedTextExerciseAndSimilarSubmissions(String userPrefix, String similarSubmissionText, int studentsAmount) {
        Course course = ModelFactory.generateCourse(null, pastTimestamp, futureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        addUsers(userPrefix, studentsAmount, 1, 1, 1);

        // Add text exercise to the course
        TextExercise textExercise = ModelFactory.generateTextExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, course);
        textExercise.setTitle("Finished");
        textExercise.getCategories().add("Text");
        course.addExercises(textExercise);
        courseRepo.save(course);
        exerciseRepo.save(textExercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, textExercise, participant);
            participation.setParticipant(participant);
            TextSubmission submission = ModelFactory.generateTextSubmission(similarSubmissionText, Language.ENGLISH, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        textExercise.participations(participations);
        exerciseRepo.save(textExercise);

        return course;
    }

    public Course addOneFinishedModelingExerciseAndSimilarSubmissionsToTheCourse(String userPrefix, String similarSubmissionModel, int studentsAmount, Course course) {
        // Add text exercise to the course
        ModelingExercise exercise = ModelFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        exercise.setTitle("finished");
        exercise.getCategories().add("Model");
        course.addExercises(exercise);

        courseRepo.save(course);
        exerciseRepo.save(exercise);

        Set<StudentParticipation> participations = new HashSet<>();

        for (int i = 0; i < studentsAmount; i++) {
            User participant = getUserByLogin(userPrefix + "student" + (i + 1));
            StudentParticipation participation = ModelFactory.generateStudentParticipation(InitializationState.FINISHED, exercise, participant);
            participation.setParticipant(participant);
            ModelingSubmission submission = ModelFactory.generateModelingSubmission(similarSubmissionModel, true);
            participation = studentParticipationRepo.save(participation);
            submission.setParticipation(participation);
            submissionRepository.save(submission);

            participation.setSubmissions(Set.of(submission));
            participations.add(participation);
        }

        exercise.participations(participations);
        exerciseRepo.save(exercise);

        return course;
    }
}
