import { OrionState, ArtemisOrionConnector } from 'app/shared/orion/orion';
import { of } from 'rxjs';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';

export class MockOrionConnectorService implements ArtemisOrionConnector {
    onExerciseOpened = (exerciseId: number) => of();
    importParticipation = (repository: string, exercise: ProgrammingExercise) => of();
    submit = () => of();
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
    onBuildFailed = (buildErrors: Array<Annotation>) => {};
    selectRepository = (repository: REPOSITORY) => {};
    startedBuildInOrion = (courseId: number, exerciseId: number) => {};
    assessExercise = (exercise: ProgrammingExercise) => {};
    downloadSubmission = (submissionId: number, correctionRound: number, base64data: String) => {};
}
