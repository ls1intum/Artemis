import { IJavaBridgeService, IntelliJState } from 'app/intellij/intellij';
import { Observable, of } from 'rxjs';
import { REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';

export class MockJavaBridgeService implements IJavaBridgeService {
    onExerciseOpened = (exerciseId: number) => of();
    clone = (repository: string, exerciseJson: string) => of();
    submit = () => of();
    state = () => Observable.of({} as IntelliJState);
    onBuildFinished = () => of();
    onBuildStarted = () => of();
    onBuildFailed = (message: string) => of();
    onTestResult = (success: boolean, message: string) => of();
    isBuilding = (building: boolean) => of();
    isCloning = (cloning: boolean) => of();
    startedBuildInIntelliJ = (courseId: number, exerciseId: number) => of();
    buildAndTestInstructorRepository = () => of();
    editExercise = (exerciseJson: string) => of();
    log = (message: string) => of();
    login = (username: string, password: string) => of();
    selectInstructorRepository = (repository: REPOSITORY) => of();
    submitInstructorRepository = () => of();
}
