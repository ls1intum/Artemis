import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class MockCourseExerciseService {
    startExercise = (_: number) => of({} as StudentParticipation);

    findAllProgrammingExercisesForCourse = (_: number) => of([{ id: 456 } as ProgrammingExercise]);
}
