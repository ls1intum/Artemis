import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, of, Subscription } from 'rxjs';
import { catchError, filter, map, tap } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';
import { IExerciseHint, ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { Exercise } from 'app/entities/exercise';
import { ExerciseService } from 'app/entities/exercise';
import { EditorMode, MarkdownEditorHeight } from 'app/markdown-editor';
import { KatexCommand } from 'app/markdown-editor/commands';

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
    styleUrls: ['./exercise-hint.scss'],
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    MarkdownEditorHeight = MarkdownEditorHeight;

    exerciseId: number;
    // This is a leftover from the jhipster boilerplate, it lets you chose the exercise of the hint in the same interface.
    // We don't needs this atm, so we just disable the select, but it might be useful in future.
    exercises: Exercise[] = [];
    exerciseHint = new ExerciseHint();

    isSaving: boolean;
    isLoading: boolean;
    paramSub: Subscription;

    domainCommands = [new KatexCommand()];
    editorMode = EditorMode.LATEX;

    constructor(
        private route: ActivatedRoute,
        protected jhiAlertService: JhiAlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
    ) {}

    ngOnInit() {
        this.isLoading = true;
        this.paramSub = this.route.params.subscribe(params => {
            this.exerciseId = params['exerciseId'];
            this.isSaving = false;
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
                            this.exercises = [res];
                            this.exerciseHint.exercise = res;
                        }),
                        catchError((res: HttpErrorResponse) => {
                            this.onError(res.message);
                            return of();
                        }),
                    )
                    .subscribe((res: Exercise) => {
                        this.isLoading = false;
                    });
            } else {
                // If the exercise exists, use its exercise for the exercise select.
                this.exercises = this.exerciseHint.exercise ? [this.exerciseHint.exercise] : [];
                this.isLoading = false;
            }
        });
    }

    ngOnDestroy(): void {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    updateHintContent(newContent: string) {
        this.exerciseHint.content = newContent;
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        if (this.exerciseHint.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseHintService.update(this.exerciseHint));
        } else {
            this.subscribeToSaveResponse(this.exerciseHintService.create(this.exerciseHint));
        }
    }

    protected subscribeToSaveResponse(result: Observable<HttpResponse<IExerciseHint>>) {
        result.subscribe(() => this.onSaveSuccess(), () => this.onSaveError());
    }

    protected onSaveSuccess() {
        this.isSaving = false;
        this.previousState();
    }

    protected onSaveError() {
        this.isSaving = false;
    }
    protected onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, undefined);
    }

    trackExerciseById(index: number, item: Exercise) {
        return item.id;
    }
}
