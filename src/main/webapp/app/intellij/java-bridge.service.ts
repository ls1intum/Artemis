import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { IntelliJState, JavaDowncallBridge } from 'app/intellij/intellij';

@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService implements JavaDowncallBridge {
    private intellijState: IntelliJState;
    private intellijStateSubject: BehaviorSubject<IntelliJState>;

    constructor(private window: WindowRef) {}

    static initBridge(bridge: JavaBridgeService, win: WindowRef) {
        win.nativeWindow.javaDowncallBridge = bridge;
        bridge.intellijState = { opened: -1 };
        bridge.intellijStateSubject = new BehaviorSubject<IntelliJState>(bridge.intellijState);
    }

    /**
     * Login to the Git client within the IDE with the same credentials as used for Artemis
     *
     * @param username The username of the current user
     * @param password The password of the current user. This is stored safely in the IDE's password safe
     */
    login(username: string, password: string) {
        this.window.nativeWindow.intellij.login(username, password);
    }

    /**
     * "Imports" a project/exercise by cloning th repository on the local machine of the user and opening the new project.
     *
     * @param repository The full URL of the repository of a programming exercise
     * @param exerciseName The name of the programming exercise
     * @param exerciseId The ID of the exercise
     * @param courseId THe ID of the course of the exercise
     */
    clone(repository: string, exerciseName: string, exerciseId: number, courseId: number) {
        this.window.nativeWindow.intellij.clone(repository, exerciseName, exerciseId, courseId);
    }

    /**
     * Submits all changes on the local machine of the user by staging and committing every file. Afterwards, all commits
     * get pushed to the remote master branch
     */
    submit() {
        this.window.nativeWindow.intellij.addCommitAndPushAllChanges();
    }

    /**
     * Get the state object of the IDE. The IDE sets different internal states in this object, e.g. the ID of the currently
     * opened exercise
     *
     * @return An observable containing the internal state of the IDE
     */
    state(): Observable<IntelliJState> {
        return this.intellijStateSubject;
    }

    /**
     * Logs a message to the debug console of the opened IDE
     *
     * @param message The message to log in the development environment
     */
    log(message: string) {
        this.window.nativeWindow.intellij.log(message);
    }

    /**
     * Gets called by the IDE. Informs the Angular app about a newly opened exercise.
     *
     * @param exerciseId The ID of the exercise that was opened by the user.
     */
    onExerciseOpened(exerciseId: number): void {
        this.intellijState.opened = exerciseId;
        this.intellijStateSubject.next(this.intellijState);
    }
}
