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

@Component({
    selector: 'jhi-exercise-hint-update',
    templateUrl: './exercise-hint-update.component.html',
})
export class ExerciseHintUpdateComponent implements OnInit, OnDestroy {
    exercises: Exercise[];

    isSaving: boolean;

    editForm = this.fb.group({
        id: [],
        title: [],
        content: [],
        exercise: [],
    });

    paramSub: Subscription;

    constructor(
        private route: ActivatedRoute,
        protected jhiAlertService: JhiAlertService,
        protected exerciseHintService: ExerciseHintService,
        protected exerciseService: ExerciseService,
        protected activatedRoute: ActivatedRoute,
        private fb: FormBuilder,
    ) {}

    ngOnInit() {
        this.paramSub = this.route.params.subscribe(params => {
            const exerciseId = params['exerciseId'];
            this.isSaving = false;
            this.activatedRoute.data.subscribe(({ exerciseHint }) => {
                this.updateForm(exerciseHint);
            });
            this.exerciseService
                .find(exerciseId)
                .map(({ body }) => body)
                .subscribe(
                    (res: Exercise) => {
                        this.exercises = [res];
                        this.editForm.patchValue({ exercise: res });
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
            id: (this.editForm.get(['id']) || { value: null }).value,
            title: (this.editForm.get(['title']) || { value: null }).value,
            content: (this.editForm.get(['content']) || { value: null }).value,
            exercise: (this.editForm.get(['exercise']) || { value: null }).value,
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
        this.jhiAlertService.error(errorMessage, null, undefined);
    }

    trackExerciseById(index: number, item: Exercise) {
        return item.id;
    }
}
