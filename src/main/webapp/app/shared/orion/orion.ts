import { REPOSITORY } from 'app/exercises/programming/shared/code-editor/instructor/code-editor-instructor-base-container.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { BuildLogErrors } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { Observable } from 'rxjs';

export interface OrionState {
    opened: number;
    cloning: boolean;
    building: boolean;
    inInstructorView: boolean;
}

export enum ExerciseView {
    STUDENT = 'STUDENT',
    INSTRUCTOR = 'INSTRUCTOR',
}

export interface ArtemisOrionConnector extends ArtemisClientConnector, OrionConnectorFacade {
    state(): Observable<OrionState>;
}

export interface OrionExerciseConnector {
    login(username: string, password: string): void;
    log(message: string): void;
    editExercise(exerciseJson: string): void;
    importParticipation(repository: string, exerciseJson: string): void;
    submitChanges(): void;
}

export interface OrionInstructorConnector {
    selectRepository(repository: REPOSITORY): void;
    buildAndTestLocally(): void;
}

export interface OrionTestResultConnector {
    onBuildStarted(problemStatement: string): void;
    onBuildFinished(): void;
    onBuildFailed(buildLogsJsonString: string): void;
    onTestResult(success: boolean, testName: string, message: string): void;
}

export interface OrionConnectorFacade {
    login(username: string, password: string): void;
    log(message: string): void;
    editExercise(exercise: ProgrammingExercise): void;
    importParticipation(repositoryUrl: string, exercise: ProgrammingExercise): void;
    submitChanges(): void;

    selectRepository(repository: REPOSITORY): void;
    buildAndTestLocally(): void;

    onBuildStarted(problemStatement: string): void;
    onBuildFinished(): void;
    onBuildFailed(buildErrors: BuildLogErrors): void;
    onTestResult(success: boolean, testName: string, message: string): void;
}

export interface ArtemisClientConnector {
    onExerciseOpened(opened: number, view: string): void;
    isCloning(cloning: boolean): void;
    isBuilding(building: boolean): void;
    startedBuildInOrion(courseId: number, exerciseId: number): void;
}

export interface Window {
    orionCoreConnector: OrionExerciseConnector;
    orionTestResultsConnector: OrionTestResultConnector;
    orionInstructorConnector: OrionInstructorConnector;
    artemisClientConnector: ArtemisClientConnector;
}

export const isOrion = window.navigator.userAgent.includes('Orion') || window.navigator.userAgent.includes('IntelliJ');
