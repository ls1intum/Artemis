import { Component, OnInit } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, Validators } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';
import { filter, map } from 'rxjs/operators';
import { JhiAlertService } from 'ng-jhipster';
import { IExerciseHint, ExerciseHint } from 'app/entities/exercise-hint/exercise-hint.model';
import { ExerciseHintService } from './exercise-hint.service';
import { IExercise } from 'app/shared/model/exercise.model';
import { ExerciseService } from 'app/entities/exercise';

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
})
export class ExerciseHintUpdateComponent implements OnInit {
    isSaving: boolean;

    exercises: IExercise[];

    editForm = this.fb.group({
        id: [],
        title: [],
        content: [],
        exercise: [],
    });

    constructor(
        protected jhiAlertService: JhiAlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
        protected activatedRoute: ActivatedRoute,
        private fb: FormBuilder,
    ) {}

    ngOnInit() {
        this.isSaving = false;
        this.activatedRoute.data.subscribe(({ exerciseHint }) => {
            this.updateForm(exerciseHint);
        });
        this.exerciseService
            .query()
            .pipe(
                filter((mayBeOk: HttpResponse<IExercise[]>) => mayBeOk.ok),
                map((response: HttpResponse<IExercise[]>) => response.body),
            )
            .subscribe((res: IExercise[]) => (this.exercises = res), (res: HttpErrorResponse) => this.onError(res.message));
    }

    updateForm(exerciseHint: IExerciseHint) {
        this.editForm.patchValue({
            id: exerciseHint.id,
            title: exerciseHint.title,
            content: exerciseHint.content,
            exercise: exerciseHint.exercise,
        });
    }

    previousState() {
        window.history.back();
    }

    save() {
        this.isSaving = true;
        const exerciseHint = this.createFromForm();
        if (exerciseHint.id !== undefined) {
            this.subscribeToSaveResponse(this.exerciseHintService.update(exerciseHint));
        } else {
            this.subscribeToSaveResponse(this.exerciseHintService.create(exerciseHint));
        }
    }

    private createFromForm(): IExerciseHint {
        return {
            ...new ExerciseHint(),
            id: this.editForm.get(['id']).value,
            title: this.editForm.get(['title']).value,
            content: this.editForm.get(['content']).value,
            exercise: this.editForm.get(['exercise']).value,
        };
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
        this.jhiAlertService.error(errorMessage, null, null);
    }

    trackExerciseById(index: number, item: IExercise) {
        return item.id;
    }
}
