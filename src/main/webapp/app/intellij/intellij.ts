export interface Intellij {
    login(username: string, password: string): void;
    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number): void;
    submit(): void;
    log(message: string): void;
}

export interface IntelliJState {
    opened: number;
}

export interface JavaDowncallBridge {
    onExerciseOpened(exerciseId: number): void;
}

export interface Window {
    intellij: Intellij;
    javaDowncallBridge: JavaDowncallBridge;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
