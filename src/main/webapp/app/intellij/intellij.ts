export interface IntelliJState {
    opened: number;
    cloning: boolean;
    building: boolean;
}

export interface JavaDowncallBridge {
    onExerciseOpened(opened: number): void;
    isCloning(cloning: boolean): void;
    isBuilding(building: boolean): void;
}

export interface JavaUpcallBridge {
    login(username: string, password: string): void;
    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number): void;
    submit(): void;
    log(message: string): void;
    onBuildStarted(): void;
    onBuildFinished(): void;
    onBuildFailed(message: string): void;
    onTestResult(success: boolean, message: string): void;
}

export interface Window {
    intellij: JavaUpcallBridge;
    javaDowncallBridge: JavaDowncallBridge;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
