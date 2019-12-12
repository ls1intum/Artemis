import { REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { BuildLogErrors } from 'app/code-editor';

export interface IntelliJState {
    opened: number;
    cloning: boolean;
    building: boolean;
    inInstructorView: boolean;
}

export enum ExerciseView {
    STUDENT = 'STUDENT',
    INSTRUCTOR = 'INSTRUCTOR',
}

export interface OrionCoreBridge {
    login(username: string, password: string): void;
    log(message: string): void;
    editExercise(exerciseJson: string): void;
    workOnExercise(repository: string, exerciseJson: string): void;
    submitChanges(): void;
}

export interface OrionInstructorBridge {
    selectRepository(repository: REPOSITORY): void;
    buildAndTestLocally(): void;
}

export interface OrionTestResultBridge {
    onBuildStarted(problemStatement: string): void;
    onBuildFinished(): void;
    onBuildFailed(buildLogsJsonString: string): void;
    onTestResult(success: boolean, testName: string, message: string): void;
}

export interface JavaUpcallBridgeFacade {
    login(username: string, password: string): void;
    log(message: string): void;
    editExercise(exercise: ProgrammingExercise): void;
    workOnExercise(repositoryUrl: string, exercise: ProgrammingExercise): void;
    submitChanges(): void;

    selectRepository(repository: REPOSITORY): void;
    buildAndTestLocally(): void;

    onBuildStarted(problemStatement: string): void;
    onBuildFinished(): void;
    onBuildFailed(buildErrors: BuildLogErrors): void;
    onTestResult(success: boolean, testName: string, message: string): void;
}

export interface JavaDowncallBridge {
    onExerciseOpened(opened: number, view: string): void;
    isCloning(cloning: boolean): void;
    isBuilding(building: boolean): void;
    startedBuildInIntelliJ(courseId: number, exerciseId: number): void;
}

export interface Window {
    orionCoreBridge: OrionCoreBridge;
    orionTestResultsBridge: OrionTestResultBridge;
    orionInstructorBridge: OrionInstructorBridge;
    javaDowncallBridge: JavaDowncallBridge;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
