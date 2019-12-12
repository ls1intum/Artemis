import { Injectable, Injector } from '@angular/core';
import { WindowRef } from 'app/core/websocket/window.service';
import { BehaviorSubject, Observable } from 'rxjs';
import { ExerciseView, IntelliJState, JavaDowncallBridge, JavaUpcallBridgeFacade } from 'app/intellij/intellij';
import { Router } from '@angular/router';
import { REPOSITORY } from 'app/code-editor/instructor/code-editor-instructor-base-container.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { stringifyCircular } from 'app/shared/util/utils';
import { BuildLogErrors } from 'app/code-editor';

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
export class JavaBridgeService implements JavaDowncallBridge, JavaUpcallBridgeFacade {
    private intellijState: IntelliJState;
    private intellijStateSubject: BehaviorSubject<IntelliJState>;

    constructor(private window: WindowRef, private injector: Injector) {}

    static initBridge(bridge: JavaBridgeService, win: WindowRef) {
        win.nativeWindow.javaDowncallBridge = bridge;
        bridge.intellijState = { opened: -1, inInstructorView: false, cloning: false, building: false };
        bridge.intellijStateSubject = new BehaviorSubject<IntelliJState>(bridge.intellijState);
    }

    /**
     * Yes, this is not best practice. But since this bridge service is an APP_INITIALIZER and has to be set on
     * the window object right in the beginning (so that the IDE can interact with Artemis as soon as the page has been
     * loaded), it should be fine to actually load the router only when it is needed later in the process.
     * Otherwise we would have a cyclic dependency problem on app startup
     */
    get router(): Router {
        return this.injector.get(Router);
    }

    /**
     * Login to the Git client within the IDE with the same credentials as used for Artemis
     *
     * @param username The username of the current user
     * @param password The password of the current user. This is stored safely in the IDE's password safe
     */
    login(username: string, password: string) {
        this.window.nativeWindow.orionCoreBridge.login(username, password);
    }

    /**
     * "Imports" a project/exercise by cloning th repository on the local machine of the user and opening the new project.
     *
     * @param repository The full URL of the repository of a programming exercise
     * @param exercise The exercise for which the repository should get cloned.
     */
    workOnExercise(repository: string, exercise: ProgrammingExercise) {
        this.window.nativeWindow.orionCoreBridge.workOnExercise(repository, stringifyCircular(exercise));
    }

    /**
     * Submits all changes on the local machine of the user by staging and committing every file. Afterwards, all commits
     * get pushed to the remote master branch
     */
    submitChanges() {
        this.window.nativeWindow.orionCoreBridge.submitChanges();
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
        this.window.nativeWindow.orionCoreBridge.log(message);
    }

    /**
     * Gets called by the IDE. Informs the Angular app about a newly opened exercise.
     *
     * @param opened The ID of the exercise that was opened by the user.
     * @param view The ExerciseView which is currently open in IntelliJ (instructor vs. student)
     */
    onExerciseOpened(opened: number, view: string): void {
        const inInstructorView = view === ExerciseView.INSTRUCTOR;
        this.setIDEStateParameter({ inInstructorView });
        this.setIDEStateParameter({ opened });
    }

    /**
     * Notify the IDE that a new build has started
     */
    onBuildStarted(problemStatement: string) {
        this.window.nativeWindow.orionTestResultsBridge.onBuildStarted(problemStatement);
    }

    /**
     * Notify the IDE that a build finished and all results have been sent
     */
    onBuildFinished() {
        this.window.nativeWindow.orionTestResultsBridge.onBuildFinished();
    }

    /**
     * Notify the IDE that a build failed. Alternative to onBuildFinished
     *
     * @param buildErrors All compile errors for the current build
     */
    onBuildFailed(buildErrors: BuildLogErrors) {
        this.window.nativeWindow.orionTestResultsBridge.onBuildFailed(JSON.stringify(buildErrors));
    }

    /**
     * Notifies the IDE about a completed test (both positive or negative). In the case of an error,
     * you can also send a message containing some information about why the test failed.
     *
     * @param success True if the test was successful, false otherwise
     * @param message A detail message explaining the test result
     * @param testName The name of finished test
     */
    onTestResult(success: boolean, testName: string, message: string) {
        this.window.nativeWindow.orionTestResultsBridge.onTestResult(success, testName, message);
    }

    /**
     * Notifies Artemis if the IDE is currently building (and testing) the checked out exercise
     *
     * @param building True, a building process is currently open, false otherwise
     */
    isBuilding(building: boolean): void {
        this.setIDEStateParameter({ building });
    }

    /**
     * Notifies Artemis if the IDE is in the process of importing (i.e. cloning) an exercise)
     *
     * @param cloning True, if there is a open clone process, false otherwise
     */
    isCloning(cloning: boolean): void {
        this.setIDEStateParameter({ cloning });
    }

    private setIDEStateParameter(patch: Partial<IntelliJState>) {
        Object.assign(this.intellijState, patch);
        this.intellijStateSubject.next(this.intellijState);
    }

    /**
     * Gets triggered if a build/test run was started from inside the IDE. This means that we have to navigate
     * to the related exercise page in order to listen for any new results
     *
     * @param courseId
     * @param exerciseId
     */
    startedBuildInIntelliJ(courseId: number, exerciseId: number) {
        this.router.navigateByUrl(`/overview/${courseId}/exercises/${exerciseId}`, { queryParams: { withIdeSubmit: true } });
    }

    /**
     * Edit the given exercise in IntelliJ as an instructor. This will trigger the import of the exercise
     * (if it is not already imported) and opens the created project afterwards.
     *
     * @param exercise The exercise to be imported
     */
    editExercise(exercise: ProgrammingExercise): void {
        this.setIDEStateParameter({ cloning: true });
        this.window.nativeWindow.orionCoreBridge.editExercise(stringifyCircular(exercise));
    }

    /**
     * Selects an instructor repository in IntelliJ. The selected repository will be used for all future actions
     * that reference an instructor repo s.a. submitting the code.
     *
     * @param repository The repository to be selected for all future interactions
     */
    selectRepository(repository: REPOSITORY): void {
        this.window.nativeWindow.orionInstructorBridge.selectRepository(repository);
    }

    buildAndTestLocally(): void {
        this.window.nativeWindow.orionInstructorBridge.buildAndTestLocally();
    }
}
