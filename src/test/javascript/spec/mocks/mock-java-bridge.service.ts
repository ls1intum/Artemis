import { IntelliJState, JavaDowncallBridge } from 'app/intellij/intellij';
import { Observable, of } from 'rxjs';

export class MockJavaBridgeService implements JavaDowncallBridge {
    onExerciseOpened = (exerciseId: number) => of();
    clone = (repository: string, exerciseName: string, exerciseId: number, courseId: number) => of();
    submit = () => of();
    state = () => Observable.of({} as IntelliJState);
}
