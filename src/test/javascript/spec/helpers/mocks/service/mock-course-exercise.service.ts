import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { of } from 'rxjs';

export class MockCourseExerciseService {
    startExercise = () => of({} as StudentParticipation);

    startPractice = () => of({} as StudentParticipation);

    resumeProgrammingExercise = () => of({} as StudentParticipation);

    findAllProgrammingExercisesForCourse = () => of([{ id: 456 } as ProgrammingExercise]);
}
