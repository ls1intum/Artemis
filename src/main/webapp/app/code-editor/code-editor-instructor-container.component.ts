import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, filter, map, switchMap, tap } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { ProgrammingExercise, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { CourseExerciseService } from 'app/entities/course';
import { Participation, ParticipationService } from 'app/entities/participation';
import { CodeEditorContainer } from './code-editor-mode-container.component';
import { TranslateService } from '@ngx-translate/core';
import { CodeEditorFileService, DomainService, DomainType } from 'app/code-editor/service';
import { JhiAlertService } from 'ng-jhipster';
import {
    CodeEditorAceComponent,
    CodeEditorActionsComponent,
    CodeEditorBuildOutputComponent,
    CodeEditorFileBrowserComponent,
    CodeEditorInstructionsComponent,
    CodeEditorSessionService,
} from 'app/code-editor';
import { ResultService, UpdatingResultComponent } from 'app/entities/result';

enum REPOSITORY {
    ASSIGNMENT = 'ASSIGNMENT',
    TEMPLATE = 'TEMPLATE',
    SOLUTION = 'SOLUTION',
    TEST = 'TEST',
}

enum LOADING_STATE {
    CLEAR = 'CLEAR',
    INITIALIZING = 'INITIALIZING',
    FETCHING_FAILED = 'FETCHING_FAILED',
    CREATING_ASSIGNMENT_REPO = 'CREATING_ASSIGNMENT_REPO',
    DELETING_ASSIGNMENT_REPO = 'DELETING_ASSIGNMENT_REPO',
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-container.component.html',
})
export class CodeEditorInstructorContainerComponent extends CodeEditorContainer implements OnInit, OnDestroy {
    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor: CodeEditorAceComponent;
    @ViewChild(UpdatingResultComponent, { static: false }) resultComp: UpdatingResultComponent;

    REPOSITORY = REPOSITORY;
    LOADING_STATE = LOADING_STATE;

    // This component responds to multiple route schemas:
    // :exerciseId -> Load exercise and select template repository
    // :exerciseId/:participationId -> Load exercise and select repository according to participationId
    // :exerciseId/test -> Load exercise and select test repository
    paramSub: Subscription;

    // Contains all participations (template, solution, assignment)
    exercise: ProgrammingExercise;
    // Can only be null when the test repository is selected.
    selectedParticipation: Participation | null;
    // Stores which repository is selected atm.
    // Needs to be set additionaly to selectedParticipation as the test repository does not have a participation
    selectedRepository: REPOSITORY;

    // Fires when the selected domain changes.
    // This can either be a participation (solution, template, assignment) or the test repository.
    domainChangeSubscription: Subscription;

    // State variables
    loadingState = LOADING_STATE.CLEAR;

    constructor(
        private router: Router,
        private exerciseService: ProgrammingExerciseService,
        private resultService: ResultService,
        private courseExerciseService: CourseExerciseService,
        private domainService: DomainService,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        jhiAlertService: JhiAlertService,
        sessionService: CodeEditorSessionService,
        fileService: CodeEditorFileService,
    ) {
        super(participationService, translateService, route, jhiAlertService, sessionService, fileService);
    }

    /**
     * Initialize the route params subscription.
     * On route param change load the exercise and the selected participation OR the test repository.
     */
    ngOnInit(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.paramSub = this.route.params.subscribe(params => {
            const exerciseId = Number(params['exerciseId']);
            const participationId = Number(params['participationId']);
            this.loadingState = LOADING_STATE.INITIALIZING;
            this.loadExercise(exerciseId)
                .pipe(
                    catchError(() => throwError('exerciseNotFound')),
                    tap(exercise => (this.exercise = exercise)),
                    // Set selected participation
                    tap(() => {
                        if (this.router.url.endsWith('/test')) {
                            this.domainService.setDomain([DomainType.TEST_REPOSITORY, this.exercise]);
                        } else {
                            this.selectParticipationDomainById(participationId);
                        }
                    }),
                    tap(() => {
                        if (!this.domainChangeSubscription) {
                            this.domainChangeSubscription = this.subscribeToDomainChange();
                        }
                    }),
                )
                .subscribe(
                    () => (this.loadingState = LOADING_STATE.CLEAR),
                    err => {
                        this.loadingState = LOADING_STATE.FETCHING_FAILED;
                        this.onError(err);
                    },
                );
        });
    }

    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Subscribe for domain changes caused by url route changes.
     * Distinguishes between participation based domains (template, solution, assignment) and the test repository.
     */
    subscribeToDomainChange() {
        return this.domainService
            .subscribeDomainChange()
            .pipe(
                filter(domain => !!domain),
                tap(([domainType, domainValue]) => {
                    this.initializeProperties();
                    if (domainType === DomainType.PARTICIPATION) {
                        this.setSelectedParticipation(domainValue.id);
                    } else {
                        this.selectedParticipation = null;
                        this.selectedRepository = REPOSITORY.TEST;
                    }
                }),
            )
            .subscribe();
    }

    /**
     * Set the selected participation based on a its id.
     * Shows an error if the participationId does not match the template, solution or assignment participation.
     **/
    setSelectedParticipation(participationId: number) {
        // The result component needs a circular structure of participation -> exercise.
        const exercise = this.exercise;
        if (participationId === this.exercise.templateParticipation.id) {
            this.selectedRepository = REPOSITORY.TEMPLATE;
            this.selectedParticipation = { ...this.exercise.templateParticipation, exercise };
        } else if (participationId === this.exercise.solutionParticipation.id) {
            this.selectedRepository = REPOSITORY.SOLUTION;
            this.selectedParticipation = { ...this.exercise.solutionParticipation, exercise };
        } else if (this.exercise.participations.length && participationId === this.exercise.participations[0].id) {
            this.selectedRepository = REPOSITORY.ASSIGNMENT;
            this.selectedParticipation = this.exercise.participations[0];
            this.selectedParticipation = { ...this.exercise.participations[0], exercise };
        } else {
            this.onError('participationNotFound');
        }
    }

    /**
     * Doesn't reload the exercise from server if it was already loaded.
     * This check is done to avoid load on the server when the user is switching participations.
     * Has the side effect, that if the exercise changes unrelated to the participations, the user has to reload the page to see the updates.
     */
    loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return this.exercise && this.exercise.id === exerciseId
            ? Observable.of(this.exercise)
            : this.exerciseService.findWithTemplateAndSolutionParticipation(exerciseId).pipe(
                  map(({ body }) => body),
                  // TODO: This is a hotfix for the findWithTemplateAndSolutionParticipation endpoint that should include the templateParticipation result feedbacks but doesn't
                  switchMap(exercise =>
                      this.resultService.getLatestResultWithFeedbacks(exercise.templateParticipation.id).pipe(
                          map(({ body }) => body),
                          map(result => ({ ...exercise, templateParticipation: { ...exercise.templateParticipation, results: [result] } })),
                          catchError(() => of(exercise)),
                      ),
                  ),
              );
    }

    /**
     * Set the selected participation domain based on a its id.
     * Shows an error if the participationId does not match the template, solution or assignment participation.
     **/
    selectParticipationDomainById(participationId: number) {
        if (participationId === this.exercise.templateParticipation.id) {
            this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.templateParticipation]);
        } else if (participationId === this.exercise.solutionParticipation.id) {
            this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.solutionParticipation]);
        } else if (this.exercise.participations.length && participationId === this.exercise.participations[0].id) {
            this.domainService.setDomain([DomainType.PARTICIPATION, this.exercise.participations[0]]);
        } else {
            this.onError('participationNotFound');
        }
    }

    selectSolutionParticipation() {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/${this.exercise.solutionParticipation.id}`);
    }

    selectTemplateParticipation() {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/${this.exercise.templateParticipation.id}`);
    }

    selectAssignmentParticipation() {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/${this.exercise.participations[0].id}`);
    }

    selectTestRepository() {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/test`);
    }

    /**
     * Creates an assignment participation for this user for this exercise.
     */
    createAssignmentParticipation() {
        this.loadingState = LOADING_STATE.CREATING_ASSIGNMENT_REPO;
        return this.courseExerciseService
            .startExercise(this.exercise.course.id, this.exercise.id)
            .pipe(
                catchError(() => throwError('participationCouldNotBeCreated')),
                tap(participation => {
                    this.exercise.participations = [participation];
                    this.loadingState = LOADING_STATE.CLEAR;
                }),
            )
            .subscribe(() => {}, err => this.onError(err));
    }

    /**
     * Resets the assignment participation for this user for this exercise.
     * This deletes all build plans, database information, etc. and copies the current version of the template repository.
     */
    resetAssignmentParticipation() {
        this.loadingState = LOADING_STATE.DELETING_ASSIGNMENT_REPO;
        if (this.selectedRepository === REPOSITORY.ASSIGNMENT) {
            this.selectTemplateParticipation();
        }
        const assignmentParticipationId = this.exercise.participations[0].id;
        this.exercise.participations = [];
        this.participationService
            .delete(assignmentParticipationId, { deleteBuildPlan: true, deleteRepository: true })
            .pipe(
                catchError(() => throwError('participationCouldNotBeDeleted')),
                tap(() => this.createAssignmentParticipation()),
            )
            .subscribe(() => {}, err => this.onError(err));
    }
}
