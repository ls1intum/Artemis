export interface Intellij {
    login(username: string, password: string): void;
    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number): void;
    submit(): void;
}

export interface Window {
    intellij: Intellij;
}

export const isIntelliJ = window.navigator.userAgent.includes('IntelliJ');
