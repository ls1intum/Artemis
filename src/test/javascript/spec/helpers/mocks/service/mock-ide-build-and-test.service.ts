import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export class MockIdeBuildAndTestService {
    listenOnBuildOutputAndForwardChanges = (exercise: ProgrammingExercise) => of();
}
