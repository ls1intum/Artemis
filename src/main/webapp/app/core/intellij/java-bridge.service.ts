import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService {
    constructor() {}

    login(username: String, password: String) {
        // @ts-ignore
        window.intellij.login(username, password);
    }

    clone(repository: String, exerciseName: String) {
        // @ts-ignore
        window.intellij.clone(repository, exerciseName);
    }

    isIntelliJ(): boolean {
        return window.navigator.userAgent.includes('IntelliJ');
    }
}
