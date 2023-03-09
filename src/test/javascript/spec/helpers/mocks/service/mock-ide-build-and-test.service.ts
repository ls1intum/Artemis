import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { of } from 'rxjs';

export class MockIdeBuildAndTestService {
    listenOnBuildOutputAndForwardChanges = (exercise: ProgrammingExercise) => of();
}
