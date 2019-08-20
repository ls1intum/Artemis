import { Injectable, OnInit } from '@angular/core';
import { WindowRef } from 'app/core';
import { Exercise } from 'app/entities/exercise';
import { Observable } from 'rxjs';
import { isIntelliJ } from 'app/intellij/intellij';

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

    openedExercise(): Observable<number | null> {
        return Observable.of(isIntelliJ ? this.window.nativeWindow.intellij.opened : null);
    }

    log(message: string) {
        this.window.nativeWindow.intellij.log(message);
    }
}
