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

    clone(repository: String, exercise: String) {
        // @ts-ignore
        window.intellij.clone(repository, exercise);
    }

    isIntelliJ(): boolean {
        return window.navigator.userAgent.includes('IntelliJ');
    }
}
