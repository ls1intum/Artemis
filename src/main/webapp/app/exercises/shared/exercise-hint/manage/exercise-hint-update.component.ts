import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseHintService } from '../shared/exercise-hint.service';
import { EditorMode, MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { faBan, faCircleNotch, faSave } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHint, HintType } from 'app/entities/hestia/exercise-hint.model';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { ProgrammingExerciseSolutionEntry } from 'app/entities/hestia/programming-exercise-solution-entry.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseServerSideTask } from 'app/entities/hestia/programming-exercise-task.model';

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
    styleUrls: ['./exercise-hint.scss'],
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    MarkdownEditorHeight = MarkdownEditorHeight;

    courseId: number;
    exerciseId: number;
    readonly HintType = HintType;
    exerciseHint = new ExerciseHint();
    solutionEntries: ProgrammingExerciseSolutionEntry[];

    programmingExercise: ProgrammingExercise;
    selectedTask: ProgrammingExerciseServerSideTask;
    tasks: ProgrammingExerciseServerSideTask[];

    isSaving: boolean;
    isLoading: boolean;
    exerciseNotFound: boolean;
    paramSub: Subscription;

    domainCommands = [new KatexCommand()];
    editorMode = EditorMode.LATEX;

    // Icons
    faCircleNotch = faCircleNotch;
    faBan = faBan;
    faSave = faSave;

    constructor(
        private route: ActivatedRoute,
        protected alertService: AlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
        private programmingExerciseService: ProgrammingExerciseService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {}

    /**
     * Fetches the exercise from the server and assigns it on the exercise hint
     */
    ngOnInit() {
        this.isLoading = true;
        this.paramSub = this.route.params.subscribe((params) => {
            this.courseId = params['courseId'];
            this.exerciseId = params['exerciseId'];
            this.isSaving = false;
            this.exerciseNotFound = false;
        });
        this.route.data.subscribe(({ exerciseHint }) => {
            this.exerciseHint = exerciseHint;
            // If the exercise was not yet created, load the exercise from the current route to set it as its exercise.
            if (!this.exerciseHint.id) {
                this.exerciseService
                    .find(this.exerciseId)
                    .pipe(
                        map(({ body }) => body),
                        tap((res: Exercise) => {
                            this.exerciseHint.exercise = res;
                        }),
                        catchError((error: HttpErrorResponse) => {
                            this.exerciseNotFound = true;
                            onError(this.alertService, error);
                            return of(null);
                        }),
                    )
                    .subscribe(() => {
                        this.isLoading = false;
                    });
            } else {
                this.isLoading = false;
            }

            this.programmingExerciseService.getTasksAndTestsExtractedFromProblemStatement(this.exerciseId).subscribe((tasks) => {
                this.tasks = tasks;

                const index = this.tasks.findIndex((task) => task.id === this.exerciseHint.programmingExerciseTask?.id);
                if (index !== -1) {
                    this.exerciseHint.programmingExerciseTask = this.tasks[index];
                } else if (tasks.length > 0) {
                    this.exerciseHint.programmingExerciseTask = this.tasks[0];
                }
            });
        });
    }

    /**
     * Unsubscribes from the param subscription
     */
    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Setter to update the exercise hint content
     * @param newContent New value to set
     */
    updateHintContent(newContent: string) {
        this.exerciseHint.content = newContent;
    }

    /**
     * Navigate to the previous page when the user cancels the update process
     * Returns to the detail page if there is no previous state and we edited an existing hint
     * Returns to the overview page if there is no previous state and we created a new hint
     */
    previousState() {
        this.navigationUtilService.navigateBackWithOptional(
            ['course-management', this.courseId.toString(), 'programming-exercises', this.exerciseId.toString(), 'hints'],
            this.exerciseHint.id?.toString(),
        );
    }

    /**
     * Saves the exercise hint by creating or updating it on the server
     */
    save() {
        this.isSaving = true;
        if (this.exerciseHint.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseHintService.update(this.exerciseId, this.exerciseHint));
        } else {
            this.subscribeToSaveResponse(this.exerciseHintService.create(this.exerciseId, this.exerciseHint));
        }
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<ExerciseHint>>) {
        result.subscribe({
            next: () => this.onSaveSuccess(),
            error: () => this.onSaveError(),
        });
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }
}
