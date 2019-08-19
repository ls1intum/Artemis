import { Injectable, OnInit } from '@angular/core';
import { WindowRef } from 'app/core';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService {
    constructor(private window: WindowRef) {}

    login(username: string, password: string) {
        this.window.nativeWindow.intellij.login(username, password);
    }

    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number) {
        this.window.nativeWindow.intellij.clone(repository, exerciseName, exerciseId, courseId);
    }

    submit() {
        this.window.nativeWindow.intellij.addCommitAndPushAllChanges();
    }

    isIntelliJ(): boolean {
        return window.navigator.userAgent.includes('IntelliJ');
    }
}
