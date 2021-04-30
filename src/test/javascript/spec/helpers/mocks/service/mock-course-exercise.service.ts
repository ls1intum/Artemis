import { Observable } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class MockCourseExerciseService {
    startExercise = (courseId: number, exerciseId: number) => of({} as StudentParticipation);

    findAllProgrammingExercisesForCourse = (courseId: number) => of([{ id: 456 } as ProgrammingExercise]);
}
