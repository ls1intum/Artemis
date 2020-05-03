import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { of } from 'rxjs';

export class MockIdeBuildAndTestService {
    listenOnBuildOutputAndForwardChanges = (exercise: ProgrammingExercise) => of();
}
