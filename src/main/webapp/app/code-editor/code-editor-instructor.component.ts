import { Component, OnInit } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/entities/exercise';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { CourseExerciseService } from 'app/entities/course';
import { ParticipationService, Participation } from 'app/entities/participation';
import { CodeEditorContainer } from './code-editor-container.component';
import { TranslateService } from '@ngx-translate/core';

enum REPOSITORY {
    ASSIGNMENT = 'ASSIGNMENT',
    TEMPLATE = 'TEMPLATE',
    SOLUTION = 'SOLUTION',
}

enum LOADING_STATE {
    NOT_LOADING = 'NOT_LOADING',
    LOADING_EXERCISE = 'LOADING_EXERCISE',
    CREATING_ASSIGNMENT_REPO = 'CREATING_ASSIGNMENT_REPO',
    RESETTING_ASSIGNMENT_REPO = 'RESETTING_ASSIGNMENT_REPO',
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor.component.html',
    providers: [],
})
export class CodeEditorInstructorComponent extends CodeEditorContainer implements OnInit {
    REPOSITORY = REPOSITORY;
    LOADING_STATE = LOADING_STATE;

    exercise: ProgrammingExercise;
    selectedParticipation: Participation;
    selectedRepository: REPOSITORY;
    paramSub: Subscription;

    loadingState = LOADING_STATE.NOT_LOADING;

    constructor(
        private router: Router,
        private exerciseService: ExerciseService,
        private courseExerciseService: CourseExerciseService,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
    ) {
        super(participationService, translateService, route);
    }

    /**
     * On init load the exercise and the selected participation.
     * Checks what kind of participation is selected (template, solution, assignment) to show this information in the ui.
     */
    ngOnInit(): void {
        this.paramSub = this.route.params.subscribe(params => {
            const exerciseId = Number(params['exerciseId']);
            const participationId = Number(params['participationId']);
            this.loadExercise(exerciseId)
                .pipe(
                    tap(exercise => (this.exercise = exercise)),
                    switchMap(exercise => {
                        if (participationId) {
                            if (participationId === this.exercise.templateParticipation.id) {
                                this.selectedRepository = REPOSITORY.TEMPLATE;
                            } else if (participationId === this.exercise.solutionParticipation.id) {
                                this.selectedRepository = REPOSITORY.SOLUTION;
                            } else {
                                this.selectedRepository = REPOSITORY.ASSIGNMENT;
                            }
                            return this.loadParticipation(participationId);
                        } else {
                            this.selectedRepository = REPOSITORY.TEMPLATE;
                            return this.loadParticipation(exercise.templateParticipation.id);
                        }
                    }),
                    tap(participation => {
                        const newParticipation = { ...participation, exercise: this.exercise };
                        this.selectedParticipation = newParticipation;
                    }),
                    switchMap(participation => (participation ? Observable.of(participation) : throwError('participationNotFound'))),
                    switchMap(() => {
                        if (!this.exercise.participations || !this.exercise.participations.length) {
                            return this.loadAssignmentParticipation();
                        } else {
                            return Observable.of(null);
                        }
                    }),
                    tap(participation => {
                        this.exercise.participations = participation ? [participation] : [];
                    }),
                )
                .subscribe();
        });
    }

    /**
     * Try to recover the exercise from exercise storage, otherwise load the exercise from server.
     * @param exerciseId
     */
    loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        this.loadingState = LOADING_STATE.LOADING_EXERCISE;
        return this.exerciseService.find(exerciseId).pipe(
            catchError(() => Observable.of(null)),
            map(({ body }) => body),
            tap(exercise => {
                this.loadingState = LOADING_STATE.NOT_LOADING;
            }),
        ) as Observable<ProgrammingExercise>;
    }

    /**
     * Load the assignment participation and assign it to the exercise.
     */
    loadAssignmentParticipation() {
        return this.participationService.findParticipation(this.exercise.course.id, this.exercise.id).pipe(
            catchError(() => Observable.of(null)),
            map(({ body }) => body),
            catchError(() => {
                return Observable.of(null);
            }),
        );
    }

    /**
     * Navigate to selected participation. This triggers the paramSub to load the corresponding data / update the ui.
     * @param participationId
     */
    selectParticipation(participationId: number) {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/${participationId}`);
    }

    selectSolutionParticipation() {
        this.selectedRepository = REPOSITORY.SOLUTION;
        this.selectParticipation(this.exercise.solutionParticipation.id);
    }

    selectTemplateParticipation() {
        this.selectedRepository = REPOSITORY.TEMPLATE;
        this.selectParticipation(this.exercise.templateParticipation.id);
    }

    selectAssignmentParticipation() {
        this.selectedRepository = REPOSITORY.ASSIGNMENT;
        this.selectParticipation(this.exercise.participations[0].id);
    }

    /**
     * Creates an assignment participation for this user for this exercise.
     */
    createAssignmentParticipation() {
        this.loadingState = LOADING_STATE.CREATING_ASSIGNMENT_REPO;
        this.courseExerciseService
            .startExercise(this.exercise.course.id, this.exercise.id)
            .pipe(
                tap(participation => {
                    this.exercise.participations = [participation];
                    this.loadingState = LOADING_STATE.NOT_LOADING;
                }),
            )
            .subscribe();
    }

    /**
     * Resets (deletes) the assignment participation for this user for this exercise.
     */
    resetAssignmentParticipation() {
        this.loadingState = LOADING_STATE.RESETTING_ASSIGNMENT_REPO;
        this.participationService
            .delete(this.exercise.participations[0].id, { deleteBuildPlan: true, deleteRepository: true })
            .pipe(
                tap(() => {
                    if (this.selectedRepository === REPOSITORY.ASSIGNMENT) {
                        this.selectTemplateParticipation();
                    }
                    this.exercise.participations = [];
                    this.loadingState = LOADING_STATE.NOT_LOADING;
                }),
            )
            .subscribe();
    }
}
