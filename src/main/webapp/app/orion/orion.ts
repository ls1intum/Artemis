import { REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { BuildLogErrors } from 'app/code-editor';

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

export interface OrionCoreConnector {
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

export interface JavaConnectorFacade {
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
    orionCoreConnector: OrionCoreConnector;
    orionTestResultsConnector: OrionTestResultConnector;
    orionInstructorConnector: OrionInstructorConnector;
    artemisClientConnector: ArtemisClientConnector;
}

export const isOrion = window.navigator.userAgent.includes('Orion') || window.navigator.userAgent.includes('IntelliJ');
