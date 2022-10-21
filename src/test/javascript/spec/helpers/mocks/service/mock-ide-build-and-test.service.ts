import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

export class MockIdeBuildAndTestService {
    listenOnBuildOutputAndForwardChanges = (exercise: ProgrammingExercise) => of();
}
