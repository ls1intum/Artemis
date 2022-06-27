import { Injectable, Injector } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { ExerciseView, OrionState } from 'app/shared/orion/orion';
import { Router } from '@angular/router';
import { REPOSITORY } from 'app/exercises/programming/manage/code-editor/code-editor-instructor-base-container.component';
import { stringifyCircular } from 'app/shared/util/utils';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Annotation } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { Feedback } from 'app/entities/feedback.model';
import { OrionTutorAssessmentComponent } from 'app/orion/assessment/orion-tutor-assessment.component';
import { AlertService } from 'app/core/util/alert.service';

/**
 * Return the global native browser window object with any type to prevent type errors
 */
function theWindow(): any {
    return window;
}

/**
 * This is the main interface between an IDE (e.g. IntelliJ) and this webapp. If a student has the Orion plugin
 * installed (https://github.com/ls1intum/Orion), this service will be used for communication between the
 * IDE Artemis itself.
 *
 * The communication itself is bidirectional, meaning that the IDE can call Typescript code
 * using the by the JavaUpcallBridge interface defined methods. On the other side, the Angular app can execute
 * Kotlin/Java code inside the IDE by calling the JavaDowncallBridge interface.
 *
 * In order to be always available, it is essential that this service gets instantiated right after loading the app
 * in the browser. This service has to always be available in the native window object, so that if an IDE is connected,
 * it can find the object during the integrated IDE browser instantiation.
 */
@Injectable({
    providedIn: 'root',
})
export class OrionConnectorService {
    private orionState: OrionState;
    private orionStateSubject: BehaviorSubject<OrionState>;

    // When loaded, the AssessmentComponent registers here to receive updates from the plugin
    activeAssessmentComponent: OrionTutorAssessmentComponent | undefined = undefined;

    constructor(private injector: Injector, private alertService: AlertService) {}

    static initConnector(connector: OrionConnectorService) {
        theWindow().artemisClientConnector = connector;
        connector.orionState = { opened: -1, view: ExerciseView.STUDENT, cloning: false, building: false };
        connector.orionStateSubject = new BehaviorSubject<OrionState>(connector.orionState);
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
        theWindow().orionSharedUtilConnector.login(username, password);
    }

    /**
     * "Imports" a project/exercise by cloning th repository on the local machine of the user and opening the new project.
     *
     * @param repository The full URL of the repository of a programming exercise
     * @param exercise The exercise for which the repository should get cloned.
     */
    importParticipation(repository: string, exercise: ProgrammingExercise) {
        theWindow().orionExerciseConnector.importParticipation(repository, stringifyCircular(exercise));
    }

    /**
     * Submits all changes on the local machine of the user by staging and committing every file. Afterwards, all commits
     * get pushed to the remote master branch
     */
    submit() {
        theWindow().orionVCSConnector.submit();
    }

    /**
     * Get the state object of the IDE. The IDE sets different internal states in this object, e.g. the ID of the currently
     * opened exercise
     *
     * @return An observable containing the internal state of the IDE
     */
    state(): Observable<OrionState> {
        return this.orionStateSubject;
    }

    /**
     * Logs a message to the debug console of the opened IDE
     *
     * @param message The message to log in the development environment
     */
    log(message: string) {
        theWindow().orionSharedUtilConnector.log(message);
    }

    /**
     * Gets called by the IDE. Informs the Angular app about a newly opened exercise.
     *
     * @param opened The ID of the exercise that was opened by the user.
     * @param viewString ExerciseView which is currently open in the IDE as string
     */
    onExerciseOpened(opened: number, viewString: string): void {
        const view = ExerciseView[viewString];
        this.setIDEStateParameter({ view });
        this.setIDEStateParameter({ opened });
    }

    /**
     * Notify the IDE that a new build has started
     */
    onBuildStarted(problemStatement: string) {
        theWindow().orionBuildConnector.onBuildStarted(problemStatement);
    }

    /**
     * Notify the IDE that a build finished and all results have been sent
     */
    onBuildFinished() {
        theWindow().orionBuildConnector.onBuildFinished();
    }

    /**
     * Notify the IDE that a build failed. Alternative to onBuildFinished
     * Transforms the annotations to the format used by orion:
     * { errors: { [fileName: string]: Annotation[] }; timestamp: number }
     *
     * @param buildErrors All compile errors for the current build
     */
    onBuildFailed(buildErrors: Array<Annotation>) {
        theWindow().orionBuildConnector.onBuildFailed(
            JSON.stringify({
                errors: buildErrors.reduce(
                    // Group annotations by filename
                    (buildLogErrors, { fileName, timestamp, ...rest }) => ({
                        ...buildLogErrors,
                        [fileName]: [...(buildLogErrors[fileName] || []), { ...rest, ts: timestamp }],
                    }),
                    {},
                ),
                timestamp: buildErrors.length > 0 ? buildErrors[0].timestamp : Date.now(),
            }),
        );
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
        theWindow().orionBuildConnector.onTestResult(success, testName, message);
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
     * Notifies Artemis if the IDE is in the process of importing (i.e. cloning) an exercise.
     *
     * @param cloning True, if there is an open clone process, false otherwise
     */
    isCloning(cloning: boolean): void {
        this.setIDEStateParameter({ cloning });
    }

    private setIDEStateParameter(patch: Partial<OrionState>) {
        Object.assign(this.orionState, patch);
        this.orionStateSubject.next(this.orionState);
    }

    /**
     * Gets triggered if a build/test run was started from inside the IDE. This means that we have to navigate
     * to the related exercise page in order to listen for any new results
     *
     * @param courseId
     * @param exerciseId
     */
    startedBuildInOrion(courseId: number, exerciseId: number) {
        this.router.navigateByUrl(`/courses/${courseId}/exercises/${exerciseId}?withIdeSubmit=true`);
    }

    /**
     * Updates the assessment of the currently open submission
     * @param submissionId Id of the open submission, for validation
     * @param feedback all inline feedback, as JSON
     */
    updateAssessment(submissionId: number, feedback: string) {
        if (this.activeAssessmentComponent) {
            const feedbackAsArray = JSON.parse(feedback) as Feedback[];
            this.activeAssessmentComponent!.updateFeedback(submissionId, feedbackAsArray);
        } else {
            this.alertService.error('artemisApp.orion.assessment.updateFailed');
        }
    }

    /**
     * Edit the given exercise in the IDE as an instructor. This will trigger the import of the exercise
     * (if it is not already imported) and opens the created project afterwards.
     *
     * @param exercise The exercise to be imported
     */
    editExercise(exercise: ProgrammingExercise): void {
        this.isCloning(true);
        theWindow().orionExerciseConnector.editExercise(stringifyCircular(exercise));
    }

    /**
     * Selects an instructor repository in the IDE. The selected repository will be used for all future actions
     * that reference an instructor repo s.a. submitting the code.
     *
     * @param repository The repository to be selected for all future interactions
     */
    selectRepository(repository: REPOSITORY): void {
        theWindow().orionVCSConnector.selectRepository(repository);
    }

    /**
     * Orders the plugin to run the maven test command locally
     */
    buildAndTestLocally(): void {
        theWindow().orionBuildConnector.buildAndTestLocally();
    }

    /**
     * Assess the exercise as a tutor. Triggers downloading/opening the exercise as tutor
     *
     * @param exercise The exercise to be imported
     */
    assessExercise(exercise: ProgrammingExercise): void {
        this.isCloning(true);
        theWindow().orionExerciseConnector.assessExercise(stringifyCircular(exercise));
    }

    /**
     * Downloads a submission into the opened tutor project
     *
     * @param submissionId id of the submission, used to navigate to the corresponding URL
     * @param correctionRound correction round, also needed to navigate to the correct URL
     * @param testRun test run flag, also needed for navigation
     * @param base64data the student's submission as base64
     */
    downloadSubmission(submissionId: number, correctionRound: number, testRun: boolean, base64data: string) {
        // Uncomment this line to also transfer the testRun flag.
        // THIS IS A BREAKING CHANGE that will require all users to upgrade their Orion to a compatible version!
        // Also change in orion.ts
        // theWindow().orionExerciseConnector.downloadSubmission(String(submissionId), String(correctionRound), testRun, base64data);
        theWindow().orionExerciseConnector.downloadSubmission(String(submissionId), String(correctionRound), base64data);
    }

    /**
     * Initializes the feedback comments.
     *
     * @param submissionId if of the submission, for validation purposes
     * @param feedback current feedback
     */
    initializeAssessment(submissionId: number, feedback: Array<Feedback>) {
        theWindow().orionExerciseConnector.initializeAssessment(String(submissionId), stringifyCircular(feedback));
    }
}
