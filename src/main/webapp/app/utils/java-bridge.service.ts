import { Injectable } from '@angular/core';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService {
    constructor() {}

    login(username: String, password: String) {
        this.intellij().login(username, password);
    }

    clone(repository: String, exercise: String) {
        this.intellij().clone(repository, exercise);
    }

    isIntelliJ(): boolean {
        return window.navigator.userAgent.includes('IntelliJ');
    }

    private intellij() {
        // @ts-ignore
        return window.intellij;
    }
}
