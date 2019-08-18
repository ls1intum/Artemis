import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService {
    constructor() {}

    login(username: string, password: string) {
        // @ts-ignore
        window.intellij.login(username, password);
    }

    clone(repository: string, exerciseName: string) {
        // @ts-ignore
        window.intellij.clone(repository, exerciseName);
    }

    submit() {
        // @ts-ignore
        window.intellij.addCommitAndPushAllChanges();
    }

    isIntelliJ(): boolean {
        return window.navigator.userAgent.includes('IntelliJ');
    }
}
