import { Injectable, OnInit } from '@angular/core';
import { WindowRef } from 'app/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { IntelliJState, isIntelliJ, JavaDowncallBridge } from 'app/intellij/intellij';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService implements JavaDowncallBridge {
    private intellijState: IntelliJState;
    private intellijStateSubject: BehaviorSubject<IntelliJState>;

    constructor(private window: WindowRef) {}

    initBridge() {
        if (isIntelliJ) {
            this.window.nativeWindow.javaDowncallBridge = this;
            this.setupDefaultState();
        }
    }

    private setupDefaultState() {
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
        if (!this.intellijState) {
            this.setupDefaultState();
        }
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
