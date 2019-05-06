import { Component, OnInit } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseService } from 'app/entities/exercise';
import { ProgrammingExercise, ProgrammingExerciseService } from 'app/entities/programming-exercise';
import { CourseExerciseService } from 'app/entities/course';
import { ParticipationService, Participation } from 'app/entities/participation';
import { CodeEditorContainer } from '../base/code-editor-mode-container.component';
import { TranslateService } from '@ngx-translate/core';

enum REPOSITORY {
    ASSIGNMENT = 'ASSIGNMENT',
    TEMPLATE = 'TEMPLATE',
    SOLUTION = 'SOLUTION',
}

enum LOADING_STATE {
    NOT_LOADING = 'NOT_LOADING',
    INITIALIZING = 'INITIALIZING',
    CREATING_ASSIGNMENT_REPO = 'CREATING_ASSIGNMENT_REPO',
    DELETING_ASSIGNMENT_REPO = 'DELETING_ASSIGNMENT_REPO',
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor-container.component.html',
    providers: [],
})
export class CodeEditorInstructorContainerComponent extends CodeEditorContainer implements OnInit {
    REPOSITORY = REPOSITORY;
    LOADING_STATE = LOADING_STATE;

    exercise: ProgrammingExercise;
    selectedParticipation: Participation;
    selectedRepository: REPOSITORY;
    paramSub: Subscription;

    loadingState = LOADING_STATE.NOT_LOADING;

    constructor(
        private router: Router,
        private exerciseService: ProgrammingExerciseService,
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
            if (!this.exercise || this.exercise.id !== exerciseId) {
                this.loadingState = LOADING_STATE.INITIALIZING;
                this.loadExercise(exerciseId)
                    .pipe(
                        catchError(() => throwError('exerciseNotFound')),
                        tap((exercise: ProgrammingExercise) => {
                            exercise.participations = exercise.participations.map(p => ({ ...p, exercise }));
                            exercise.templateParticipation = { ...exercise.templateParticipation, exercise };
                            exercise.solutionParticipation = { ...exercise.solutionParticipation, exercise };
                            this.exercise = exercise;
                        }),
                        // Set selected participation
                        tap(() => {
                            this.setSelectedParticipation(participationId);
                        }),
                    )
                    .subscribe(
                        () => {
                            this.loadingState = LOADING_STATE.NOT_LOADING;
                        },
                        err => this.editor.onError(err),
                    );
            } else {
                this.setSelectedParticipation(participationId);
            }
        });
    }

    /**
     * Set the selected participation based on a its id. If the id is null, set the template participation as default.
     **/
    setSelectedParticipation(participationId: number) {
        if (!participationId || participationId === this.exercise.templateParticipation.id) {
            this.selectedRepository = REPOSITORY.TEMPLATE;
            this.selectedParticipation = this.exercise.templateParticipation;
        } else if (participationId === this.exercise.solutionParticipation.id) {
            this.selectedRepository = REPOSITORY.SOLUTION;
            this.selectedParticipation = this.exercise.solutionParticipation;
        } else if (this.exercise.participations.length && participationId === this.exercise.participations[0].id) {
            this.selectedRepository = REPOSITORY.ASSIGNMENT;
            this.selectedParticipation = this.exercise.participations[0];
        }
    }

    /**
     * Try to recover the exercise from exercise storage, otherwise load the exercise from server.
     * @param exerciseId
     */
    loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        return !this.exercise
            ? this.exerciseService.findWithParticipations(exerciseId).pipe(
                  catchError(() => Observable.of(null)),
                  map(({ body }) => body),
              )
            : Observable.of(this.exercise);
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
        return this.courseExerciseService
            .startExercise(this.exercise.course.id, this.exercise.id)
            .pipe(
                catchError(() => throwError('participationCouldNotBeCreated')),
                tap(participation => {
                    this.exercise.participations = [participation];
                    this.loadingState = LOADING_STATE.NOT_LOADING;
                }),
            )
            .subscribe(() => {}, err => this.editor.onError(err));
    }

    /**
     * Resets the assignment participation for this user for this exercise.
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
            .subscribe(() => {}, err => this.editor.onError(err));
    }
}
