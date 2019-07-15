import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable, Subscription } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';
import { IExerciseHint, ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { Exercise } from 'app/entities/exercise';
import { ExerciseService } from 'app/entities/exercise';
import { MarkdownEditorHeight } from 'app/markdown-editor';

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
    styleUrls: ['./exercise-hint.scss'],
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    MarkdownEditorHeight = MarkdownEditorHeight;

    exercises: Exercise[];
    exerciseHint = new ExerciseHint();

    isSaving: boolean;
    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        protected jhiAlertService: JhiAlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
        protected activatedRoute: ActivatedRoute,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            const exerciseId = params['exerciseId'];
            this.isSaving = false;
            this.activatedRoute.data.subscribe(({ exerciseHint }) => {
                this.exerciseHint = exerciseHint;
            });
            this.exerciseService
                .find(exerciseId)
                .map(({ body }) => body)
                .subscribe(
                    (res: Exercise) => {
                        this.exercises = [res];
                        this.exerciseHint.exercise = res;
                    },
                    (res: HttpErrorResponse) => this.onError(res.message),
                );
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
