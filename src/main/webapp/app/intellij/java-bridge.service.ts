import { Injectable } from '@angular/core';
import { WindowRef } from 'app/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { IntelliJState, JavaDowncallBridge, JavaUpcallBridge } from 'app/intellij/intellij';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

/**
 * This is the main interface between an IDE (e.g. IntelliJ) and this webapp. If a student has the Orion plugin
 * installed (https://github.com/ls1intum/Orion), this service will be used for communication between the
 * IDE Artemis itself.
 *
 * The communication itself is bidirectional, meaning that the IDE can call Typescript code
 * using the by the JavaUpcallBridge interface defined methods. On the other side, the Angular app can execute
 * Kotlin/Java coode inside the IDE by calling the JavaDowncallBridge interface.
 *
 * In order to be always available, it is essential that this service gets instantiated right after loading the app
 * in the browser. This service has to always be available in the native window object, so that if an IDE is connected,
 * it can find the object during the integrated IDE browser instantiation.
 */
@Injectable({
    providedIn: 'root',
})
export class JavaBridgeService implements JavaDowncallBridge, JavaUpcallBridge {
    private intellijState: IntelliJState;
    private intellijStateSubject: BehaviorSubject<IntelliJState>;

    constructor(private window: WindowRef) {}

    static initBridge(bridge: JavaBridgeService, win: WindowRef) {
        win.nativeWindow.javaDowncallBridge = bridge;
        bridge.intellijState = { opened: -1, inInstructorView: false };
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

    onExerciseOpenedAsInstructor(exerciseId: number): void {
        this.intellijState.opened = exerciseId;
        this.intellijState.inInstructorView = true;
        this.intellijStateSubject.next(this.intellijState);
    }

    editExercise(exerciseJson: string): void {
        this.window.nativeWindow.intellij.editExercise(exerciseJson);
    }
}
