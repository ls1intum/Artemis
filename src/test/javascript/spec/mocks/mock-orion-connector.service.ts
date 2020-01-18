import { OrionState, ArtemisClientConnector } from 'app/orion/orion';
import { Observable, of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

export class MockOrionConnectorService implements ArtemisClientConnector {
    onExerciseOpened = (exerciseId: number) => of();
    importParticipation = (repository: string, exercise: ProgrammingExercise) => of();
    submitChanges = () => of();
    state = () => Observable.of({} as OrionState);
    onBuildFinished = () => of();
    onBuildStarted = () => of();
    onBuildFailed = (message: string) => of();
    onTestResult = (success: boolean, message: string) => of();
}
