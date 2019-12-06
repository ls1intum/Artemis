import { REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';

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
    onTestResult(success: boolean, message: string): void;
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
