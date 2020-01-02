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

export interface JavaDowncallBridge {
    onExerciseOpened(opened: number, view: string): void;
    isCloning(cloning: boolean): void;
    isBuilding(building: boolean): void;
    startedBuildInIntelliJ(courseId: number, exerciseId: number): void;
}

export interface JavaUpcallBridge {
    login(username: string, password: string): void;
    clone(repository: string, exerciseJson: string): void;
    submit(): void;
    log(message: string): void;
    onBuildStarted(): void;
    onBuildFinished(): void;
    onBuildFailed(message: string): void;
    onTestResult(success: boolean, message: string): void;
    editExercise(exerciseJson: string): void;
    selectInstructorRepository(repository: REPOSITORY): void;
    submitInstructorRepository(): void;
    buildAndTestInstructorRepository(): void;
}

export interface Window {
    intellij: JavaUpcallBridge;
    javaDowncallBridge: JavaDowncallBridge;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
