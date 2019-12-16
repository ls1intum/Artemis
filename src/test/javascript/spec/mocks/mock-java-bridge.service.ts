import { OrionState, ArtemisClientConnector } from 'app/intellij/orion';
import { Observable, of } from 'rxjs';

export class MockJavaBridgeService implements ArtemisClientConnector {
    onExerciseOpened = (exerciseId: number) => of();
    clone = (repository: string, exerciseName: string, exerciseId: number, courseId: number) => of();
    submit = () => of();
    state = () => Observable.of({} as OrionState);
    onBuildFinished = () => of();
    onBuildStarted = () => of();
    onBuildFailed = (message: string) => of();
    onTestResult = (success: boolean, message: string) => of();
}
