import { Component, OnInit } from '@angular/core';
import { Observable, throwError } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ActivatedRoute, Router } from '@angular/router';
import { ExerciseDataProvider } from './exercise-data-provider';
import { ExerciseService } from 'app/entities/exercise';
import { ProgrammingExercise } from 'app/entities/programming-exercise';
import { CourseExerciseService } from 'app/entities/course';
import { ParticipationService, Participation } from 'app/entities/participation';
import { ParticipationDataProvider } from 'app/course-list';
import { CodeEditorContainer } from './code-editor-container.component';
import { TranslateService } from '@ngx-translate/core';

enum REPOSITORY {
    ASSIGNMENT = 'ASSIGNMENT',
    TEMPLATE = 'TEMPLATE',
    SOLUTION = 'SOLUTION',
}

@Component({
    selector: 'jhi-code-editor-instructor',
    templateUrl: './code-editor-instructor.component.html',
    providers: [],
})
export class CodeEditorInstructorComponent extends CodeEditorContainer implements OnInit {
    exercise: ProgrammingExercise;
    selectedParticipation: Participation;
    selectedRepository: REPOSITORY;
    paramSub: Subscription;

    constructor(
        private router: Router,
        private exerciseDataProvider: ExerciseDataProvider,
        private exerciseService: ExerciseService,
        private courseExerciseService: CourseExerciseService,
        participationService: ParticipationService,
        translateService: TranslateService,
        participationDataProvider: ParticipationDataProvider,
        route: ActivatedRoute,
    ) {
        super(participationService, participationDataProvider, translateService, route);
    }

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
                    tap(participation => (this.selectedParticipation = participation)),
                    switchMap(participation => (participation ? Observable.of(participation) : throwError('participationNotFound'))),
                    switchMap(() => {
                        if (!this.exercise.assignmentParticipation) {
                            return this.participationService.findParticipation(this.exercise.course.id, this.exercise.id).pipe(
                                tap(({ body: participation }) => {
                                    this.exercise.assignmentParticipation = participation;
                                    this.exerciseDataProvider.exerciseDataStorage = this.exercise;
                                }),
                                catchError(() => {
                                    return Observable.of(null);
                                }),
                            );
                        } else {
                            return Observable.of(null);
                        }
                    }),
                )
                .subscribe();
        });
    }

    loadExercise(exerciseId: number): Observable<ProgrammingExercise> {
        if (this.exerciseDataProvider.exerciseDataStorage && this.exerciseDataProvider.exerciseDataStorage.id === exerciseId) {
            return Observable.of(this.exerciseDataProvider.exerciseDataStorage);
        } else {
            return this.exerciseService.find(exerciseId).pipe(
                map(({ body }) => body),
                tap(exercise => (this.exerciseDataProvider.exerciseDataStorage = exercise as ProgrammingExercise)),
            ) as Observable<ProgrammingExercise>;
        }
    }

    createAssignmentParticipation() {
        this.courseExerciseService
            .startExercise(this.exercise.course.id, this.exercise.id)
            .pipe(
                tap(participation => {
                    this.exercise.assignmentParticipation = participation;
                    this.exerciseDataProvider.exerciseDataStorage = this.exercise;
                    this.selectParticipation(this.exercise.assignmentParticipation.id);
                }),
            )
            .subscribe();
    }

    resetAssignmentParticipation() {
        this.participationService
            .delete(this.exercise.assignmentParticipation.id)
            .pipe(
                tap(() => {
                    if (this.selectedRepository === REPOSITORY.ASSIGNMENT) {
                        this.selectTemplateParticipation();
                    }
                    this.exercise.assignmentParticipation = undefined;
                    this.exerciseDataProvider.exerciseDataStorage = this.exercise;
                }),
            )
            .subscribe();
    }

    selectParticipation(participationId: number) {
        this.router.navigateByUrl(`/code-editor-admin/${this.exercise.id}/${participationId}`);
    }

    selectSolutionParticipation() {
        this.selectedRepository = REPOSITORY.SOLUTION;
        this.selectParticipation(this.exercise.solutionParticipation.id);
    }

    selectTemplateParticipation() {
        this.selectedRepository = REPOSITORY.TEMPLATE;
        this.selectParticipation(this.exercise.solutionParticipation.id);
    }

    selectAssignmentParticipation() {
        this.selectedRepository = REPOSITORY.ASSIGNMENT;
        if (this.exercise.assignmentParticipation) {
            this.selectParticipation(this.exercise.assignmentParticipation.id);
        } else {
            this.participationService
                .findParticipation(this.exercise.id, this.exercise.course.id)
                .pipe(
                    tap(({ body: assignmentParticipation }) => (this.exercise.assignmentParticipation = assignmentParticipation)),
                    tap(() => this.selectParticipation(this.exercise.assignmentParticipation.id)),
                )
                .subscribe();
        }
    }
}
