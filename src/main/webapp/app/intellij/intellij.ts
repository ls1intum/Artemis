export interface Intellij {
    opened: number;
    login(username: string, password: string): void;
    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number): void;
    submit(): void;
    log(message: string): void;
}

export interface Window {
    intellij: Intellij;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
