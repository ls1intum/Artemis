import { Component, OnDestroy, OnInit } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ActivatedRoute, Router } from '@angular/router';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, map, tap } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';
import { ExerciseHint } from 'app/entities/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { EditorMode, MarkdownEditorHeight } from 'app/shared/markdown-editor/markdown-editor.component';
import { Exercise } from 'app/entities/exercise.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { KatexCommand } from 'app/shared/markdown-editor/commands/katex.command';
import { navigateBack } from 'app/utils/navigation.utils';

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
    styleUrls: ['./exercise-hint.scss'],
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    MarkdownEditorHeight = MarkdownEditorHeight;

    courseId: number;
    exerciseId: number;
    exerciseHint = new ExerciseHint();

    isSaving: boolean;
    isLoading: boolean;
    exerciseNotFound: boolean;
    paramSub: Subscription;

    domainCommands = [new KatexCommand()];
    editorMode = EditorMode.LATEX;

    constructor(
        private route: ActivatedRoute,
        private router: Router,
        protected jhiAlertService: JhiAlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
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
                        catchError((res: HttpErrorResponse) => {
                            this.exerciseNotFound = true;
                            this.onError(res.message);
                            return of(null);
                        }),
                    )
                    .subscribe(() => {
                        this.isLoading = false;
                    });
            } else {
                this.isLoading = false;
            }
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
        if (this.exerciseHint.id) {
            navigateBack(this.router, [
                'course-management',
                this.courseId.toString(),
                'programming-exercises',
                this.exerciseId.toString(),
                'hints',
                this.exerciseHint.id!.toString(),
            ]);
        } else {
            navigateBack(this.router, ['course-management', this.courseId.toString(), 'programming-exercises', this.exerciseId.toString(), 'hints']);
        }
    }

    /**
     * Saves the exercise hint by creating or updating it on the server
     */
    save() {
        this.isSaving = true;
        if (this.exerciseHint.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseHintService.update(this.exerciseHint));
        } else {
            this.subscribeToSaveResponse(this.exerciseHintService.create(this.exerciseHint));
        }
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<ExerciseHint>>) {
        result.subscribe(
            () => this.onSaveSuccess(),
            () => this.onSaveError(),
        );
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }
    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage);
    }
}
