import { OrionState, ArtemisClientConnector, ArtemisOrionConnector } from 'app/shared/orion/orion';
import { Observable, of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BuildLogErrors } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { REPOSITORY } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-base-container.component';

export class MockOrionConnectorService implements ArtemisOrionConnector {
    onExerciseOpened = (exerciseId: number) => of();
    importParticipation = (repository: string, exercise: ProgrammingExercise) => of();
    submitChanges = () => of();
    state = () => of({} as OrionState);
    onBuildFinished = () => of();
    onBuildStarted = () => of();
    onTestResult = (success: boolean, message: string) => of();
    buildAndTestLocally = () => {};
    editExercise = (exercise: ProgrammingExercise) => {};
    isBuilding = (building: boolean) => {};
    isCloning = (cloning: boolean) => {};
    log = (message: string) => {};
    login = (username: string, password: string) => {};
    onBuildFailed = (buildErrors: BuildLogErrors) => {};
    selectRepository = (repository: REPOSITORY) => {};
    startedBuildInOrion = (courseId: number, exerciseId: number) => {};
}
