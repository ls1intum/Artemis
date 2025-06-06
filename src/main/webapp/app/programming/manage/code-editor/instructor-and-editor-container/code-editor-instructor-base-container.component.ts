import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { Observable, Subscription, of, throwError } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { AlertService } from 'app/shared/service/alert.service';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { DomainChange, DomainType, RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
/**
 * Enumeration specifying the loading state
 */
export enum LOADING_STATE {
    CLEAR = 'CLEAR',
    INITIALIZING = 'INITIALIZING',
    FETCHING_FAILED = 'FETCHING_FAILED',
    CREATING_ASSIGNMENT_REPO = 'CREATING_ASSIGNMENT_REPO',
    DELETING_ASSIGNMENT_REPO = 'DELETING_ASSIGNMENT_REPO',
}

@Component({
    template: '',
})
export abstract class CodeEditorInstructorBaseContainerComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;

    private router = inject(Router);
    private exerciseService = inject(ProgrammingExerciseService);
    private courseExerciseService = inject(CourseExerciseService);
    private domainService = inject(DomainService);
    private location = inject(Location);
    private participationService = inject(ParticipationService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);

    ButtonSize = ButtonSize;
    LOADING_STATE = LOADING_STATE;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    // This component responds to multiple route schemas:
    // :exerciseId -> Load exercise and select template repository
    // :exerciseId/:participationId -> Load exercise and select repository according to participationId
    // :exerciseId/test -> Load exercise and select test repository
    paramSub: Subscription;

    // Contains all participations (template, solution, assignment)
    exercise: ProgrammingExercise;
    course: Course;
    // Can only be undefined when the test repository is selected.
    selectedParticipation?: TemplateProgrammingExerciseParticipation | SolutionProgrammingExerciseParticipation | ProgrammingExerciseStudentParticipation;
    // Stores which repository is selected atm.
    // Needs to be set additionally to selectedParticipation as the test repository does not have a participation
    selectedRepository: RepositoryType;
    selectedRepositoryId: number;
    selectedAuxiliaryRepositoryName?: string;

    // Fires when the selected domain changes.
    // This can either be a participation (solution, template, assignment) or the test repository.
    domainChangeSubscription: Subscription;

    // State variables
    loadingState = LOADING_STATE.CLEAR;

    /**
     * Initialize the route params subscription.
     * On route param change load the exercise and the selected participation OR the test repository.
     */
    ngOnInit(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
        this.paramSub = this.route!.params.subscribe((params) => {
            const exerciseId = Number(params['exerciseId']);
            const repositoryType = params['repositoryType'];
            const repositoryId = Number(params['repositoryId']);
            this.loadingState = LOADING_STATE.INITIALIZING;
            this.loadExercise(exerciseId)
                .pipe(
                    catchError(() => throwError(() => new Error('exerciseNotFound'))),
                    tap((exercise) => {
                        this.exercise = exercise;
                        this.course = exercise.course! ?? exercise.exerciseGroup!.exam!.course!;
                    }),
                    // Set selected participation
                    tap(() => {
                        if (repositoryType === RepositoryType.TESTS) {
                            this.saveChangesAndSelectDomain([DomainType.TEST_REPOSITORY, this.exercise]);
                        } else if (repositoryType === RepositoryType.AUXILIARY) {
                            const auxiliaryRepo = this.exercise.auxiliaryRepositories?.find((repo) => repo.id === repositoryId);
                            if (auxiliaryRepo) {
                                this.selectedAuxiliaryRepositoryName = auxiliaryRepo.name;
                                this.saveChangesAndSelectDomain([DomainType.AUXILIARY_REPOSITORY, auxiliaryRepo]);
                            }
                        } else {
                            const nextAvailableParticipation = this.getNextAvailableParticipation(repositoryId);
                            if (nextAvailableParticipation) {
                                this.selectParticipationDomainById(nextAvailableParticipation.id!);

                                // Show a consistent route in the browser
                                if (nextAvailableParticipation.id !== repositoryId) {
                                    const parentUrl = this.router.url.substring(0, this.router.url.lastIndexOf('/'));
                                    this.location.replaceState(parentUrl + `/${nextAvailableParticipation.id}`);
                                }
                            } else {
                                throwError(() => new Error('participationNotFound'));
                            }
                        }
                    }),
                    tap(() => {
                        if (!this.domainChangeSubscription) {
                            this.domainChangeSubscription = this.subscribeToDomainChange();
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.loadingState = LOADING_STATE.CLEAR;
                    },
                    error: (err: Error) => {
                        this.loadingState = LOADING_STATE.FETCHING_FAILED;
                        this.onError(err.message);
                    },
                });
        });
    }

    /**
     * Unsubscribe from paramSub and domainChangeSubscription if they are present, on component destruction
     */
    ngOnDestroy() {
        if (this.domainChangeSubscription) {
            this.domainChangeSubscription.unsubscribe();
        }
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Get the next available participation, highest priority has the participation given to the method.
     * Removes participations without a repositoryUri (could be invalid).
     * Returns undefined if no valid participation can be found.
     *
     * @param preferredParticipationId
     */
    private getNextAvailableParticipation(preferredParticipationId: number): Participation | undefined {
        const availableParticipations = [
            this.exercise.templateParticipation,
            this.exercise.solutionParticipation,
            this.exercise.studentParticipations && this.exercise.studentParticipations.length ? this.exercise.studentParticipations[0] : undefined,
        ].filter(Boolean);
        const selectedParticipation = availableParticipations.find(({ id }: ProgrammingExerciseStudentParticipation) => id === preferredParticipationId);
        return [selectedParticipation, ...availableParticipations].filter(Boolean).find(({ repositoryUri }: ProgrammingExerciseStudentParticipation) => !!repositoryUri);
    }

    /**
     * Subscribe for domain changes caused by url route changes.
     * Distinguishes between participation based domains (template, solution, assignment) and the test repository.
     */
    subscribeToDomainChange() {
        return this.domainService
            .subscribeDomainChange()
            .pipe(
                filter((domain) => !!domain),
                map((domain) => domain as DomainChange),
                tap(([domainType, domainValue]) => {
                    this.applyDomainChange(domainType, domainValue);
                }),
            )
            .subscribe();
    }

    protected applyDomainChange(domainType: any, domainValue: any) {
        if (this.codeEditorContainer != undefined) {
            this.codeEditorContainer.initializeProperties();
        }
        if (domainType === DomainType.AUXILIARY_REPOSITORY) {
            this.selectedRepository = RepositoryType.AUXILIARY;
            this.selectedRepositoryId = domainValue.id;
        } else if (domainType === DomainType.PARTICIPATION) {
            this.setSelectedParticipation(domainValue.id);
        } else {
            this.selectedParticipation = this.exercise.templateParticipation!;
            this.selectedRepository = RepositoryType.TESTS;
        }
    }

    /**
     * Set the selected participation based on its id.
     * Shows an error if the participationId does not match the template, solution or assignment participation.
     **/
    setSelectedParticipation(participationId: number) {
        // The result component needs a circular structure of participation -> exercise.
        const exercise = this.exercise;
        if (participationId === this.exercise.templateParticipation!.id) {
            this.selectedRepository = RepositoryType.TEMPLATE;
            this.selectedParticipation = this.exercise.templateParticipation;
            (this.selectedParticipation as TemplateProgrammingExerciseParticipation).programmingExercise = exercise;
        } else if (participationId === this.exercise.solutionParticipation!.id) {
            this.selectedRepository = RepositoryType.SOLUTION;
            this.selectedParticipation = this.exercise.solutionParticipation;
            (this.selectedParticipation as SolutionProgrammingExerciseParticipation).programmingExercise = exercise;
        } else if (this.exercise.studentParticipations?.length && participationId === this.exercise.studentParticipations[0].id) {
            this.selectedRepository = RepositoryType.ASSIGNMENT;
            this.selectedParticipation = this.exercise.studentParticipations[0] as ProgrammingExerciseStudentParticipation;
            this.selectedParticipation.exercise = exercise;
        } else {
            this.onError('participationNotFound');
        }
    }

    repositoryUri(participation?: Participation) {
        return (participation as ProgrammingExerciseStudentParticipation)?.repositoryUri;
    }

    /**
     * Doesn't reload the exercise from server if it was already loaded.
     * This check is done to avoid load on the server when the user is switching participations.
     * Has the side effect, that if the exercise changes unrelated to the participations, the user has to reload the page to see the updates.
     */
    loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return this.exercise && this.exercise.id === exerciseId
            ? of(this.exercise)
            : this.exerciseService.findWithTemplateAndSolutionParticipationAndResults(exerciseId).pipe(map(({ body }) => body!));
    }

    /**
     * Set the selected participation domain based on its id.
     * Shows an error if the participationId does not match the template, solution or assignment participation.
     **/
    selectParticipationDomainById(participationId: number) {
        if (participationId === this.exercise.templateParticipation!.id) {
            this.exercise.templateParticipation!.programmingExercise = this.exercise;
            this.saveChangesAndSelectDomain([DomainType.PARTICIPATION, this.exercise.templateParticipation!]);
        } else if (participationId === this.exercise.solutionParticipation!.id) {
            this.exercise.solutionParticipation!.programmingExercise = this.exercise;
            this.saveChangesAndSelectDomain([DomainType.PARTICIPATION, this.exercise.solutionParticipation!]);
        } else if (this.exercise.studentParticipations?.length && participationId === this.exercise.studentParticipations[0].id) {
            this.exercise.studentParticipations[0].exercise = this.exercise;
            this.saveChangesAndSelectDomain([DomainType.PARTICIPATION, this.exercise.studentParticipations[0]]);
        } else {
            this.onError('participationNotFound');
        }
    }

    /**
     * Saves unsaved changes and then selects a domain.
     *
     * Always use this method for changing the editor content to save file modifications.
     */
    saveChangesAndSelectDomain(domain: DomainChange) {
        if (this.codeEditorContainer != undefined) {
            this.codeEditorContainer.actions.onSave();
        }
        this.domainService.setDomain(domain);
    }

    /**
     * Select the template participation repository and navigate to it
     */
    selectTemplateParticipation() {
        this.router.navigate(['../..', RepositoryType.TEMPLATE, this.exercise.templateParticipation!.id], { relativeTo: this.route });
    }

    /**
     * Select the solution participation repository and navigate to it
     */
    selectSolutionParticipation() {
        this.router.navigate(['../..', RepositoryType.SOLUTION, this.exercise.solutionParticipation!.id], { relativeTo: this.route });
    }

    /**
     * Select the assignment participation repository and navigate to it
     */
    selectAssignmentParticipation() {
        this.router.navigate(['../..', RepositoryType.USER, this.exercise.studentParticipations![0].id], { relativeTo: this.route });
    }

    /**
     * Select the test repository and navigate to it
     */
    selectTestRepository() {
        // as test repositories do not have any participation nor repository Id associated, we use a 'test' placeholder
        this.router.navigate(['../..', RepositoryType.TESTS, 'test'], { relativeTo: this.route });
    }

    /**
     * Select the auxiliary repository and navigate to it
     */
    selectAuxiliaryRepository(repositoryId: number) {
        this.router.navigate(['../..', RepositoryType.AUXILIARY, repositoryId], { relativeTo: this.route });
    }

    /**
     * Creates an assignment participation for this user for this exercise.
     */
    createAssignmentParticipation() {
        this.loadingState = LOADING_STATE.CREATING_ASSIGNMENT_REPO;
        return this.courseExerciseService
            .startExercise(this.exercise.id!)
            .pipe(
                catchError(() => throwError(() => new Error('participationCouldNotBeCreated'))),
                tap((participation) => {
                    this.exercise.studentParticipations = [participation];
                    this.loadingState = LOADING_STATE.CLEAR;
                }),
            )
            .subscribe({
                error: (err: Error) => this.onError(err.message),
            });
    }

    /**
     * Delete the assignment participation for this user for this exercise.
     * This deletes all build plans, database information, etc. and copies the current version of the template repository.
     */
    deleteAssignmentParticipation() {
        this.loadingState = LOADING_STATE.DELETING_ASSIGNMENT_REPO;
        if (this.selectedRepository === RepositoryType.ASSIGNMENT) {
            this.selectTemplateParticipation();
        }
        const assignmentParticipationId = this.exercise.studentParticipations![0].id!;
        this.exercise.studentParticipations = [];
        this.participationService!.delete(assignmentParticipationId, { deleteBuildPlan: true, deleteRepository: true })
            .pipe(
                catchError(() => throwError(() => new Error('participationCouldNotBeDeleted'))),
                tap(() => {
                    this.loadingState = LOADING_STATE.CLEAR;
                }),
            )
            .subscribe({
                error: (err: Error) => this.onError(err.message),
            });
    }

    /**
     * Show an error as an alert in the top of the editor html.
     * Used by other components to display errors.
     * The error must already be provided translated by the emitting component.
     */
    onError(error: string) {
        this.alertService.error(`artemisApp.editor.errors.${error}`);
    }
}
