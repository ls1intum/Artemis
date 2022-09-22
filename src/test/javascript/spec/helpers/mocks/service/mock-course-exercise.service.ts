import { of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class MockCourseExerciseService {
    startExercise = () => of({} as StudentParticipation);

    startPractice = () => of({} as StudentParticipation);

    findAllProgrammingExercisesForCourse = () => of([{ id: 456 } as ProgrammingExercise]);
}
