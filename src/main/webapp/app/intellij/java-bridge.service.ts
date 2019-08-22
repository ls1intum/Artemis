import { Injectable, OnInit } from '@angular/core';
import { WindowRef } from 'app/core';
import { BehaviorSubject } from 'rxjs';
import { IntelliJState, JavaDowncallBridge } from 'app/intellij/intellij';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService implements JavaDowncallBridge {
    private readonly intellijState: IntelliJState;
    private readonly intellijStateSubject: BehaviorSubject<IntelliJState>;

    constructor(private window: WindowRef) {
        this.window.nativeWindow.javaDowncallBridge = this;
        this.intellijState = { opened: -1 };
        this.intellijStateSubject = new BehaviorSubject<IntelliJState>(this.intellijState);
    }

    login(username: string, password: string) {
        this.window.nativeWindow.intellij.login(username, password);
    }

    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number) {
        this.window.nativeWindow.intellij.clone(repository, exerciseName, exerciseId, courseId);
    }

    submit() {
        this.window.nativeWindow.intellij.addCommitAndPushAllChanges();
    }

    get state(): BehaviorSubject<IntelliJState> {
        return this.intellijStateSubject;
    }

    log(message: string) {
        this.window.nativeWindow.intellij.log(message);
    }

    onExerciseOpened(exerciseId: number): void {
        this.intellijState.opened = exerciseId;
        this.intellijStateSubject.next(this.intellijState);
    }
}
