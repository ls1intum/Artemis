package de.tum.in.www1.artemis.repository;

import org.springframework.data.repository.CrudRepository;

import de.tum.in.www1.artemis.domain.Course;
import de.tum.in.www1.artemis.domain.ProgrammingExercise;
import de.tum.in.www1.artemis.domain.StudentQuestion;
import de.tum.in.www1.artemis.domain.exam.Exam;
import de.tum.in.www1.artemis.domain.exam.StudentExam;
import de.tum.in.www1.artemis.web.rest.errors.EntityNotFoundException;

public class RepositoryHelper {

    public static <T, ID> T findByIdElseThrow(CrudRepository<T, ID> repository, String entityName, ID entityId) throws EntityNotFoundException {
        return repository.findById(entityId).orElseThrow(() -> new EntityNotFoundException(entityName, (Long) entityId));
    }

    public static Course findCourseByIdElseThrow(CourseRepository repository, Long courseId) throws EntityNotFoundException {
        return findByIdElseThrow(repository, "Course", courseId);
    }

    public static Exam findExamByIdElseThrow(ExamRepository repository, Long courseId) throws EntityNotFoundException {
        return findByIdElseThrow(repository, "Exam", courseId);
    }

    public static StudentExam findStudentExamByIdElseThrow(StudentExamRepository repository, Long courseId) throws EntityNotFoundException {
        return findByIdElseThrow(repository, "Student Exam", courseId);
    }

    public static ProgrammingExercise findProgrammingExerciseByIdElseThrow(ProgrammingExerciseRepository repository, Long courseId) throws EntityNotFoundException {
        return findByIdElseThrow(repository, "Programming Exercise", courseId);
    }

    public static StudentQuestion findStudentQuestionByIdElseThrow(StudentQuestionRepository repository, Long courseId) throws EntityNotFoundException {
        return findByIdElseThrow(repository, "Student Question", courseId);
    }
}
