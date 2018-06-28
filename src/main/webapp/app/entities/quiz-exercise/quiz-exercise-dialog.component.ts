import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';

import { Observable } from 'rxjs/Observable';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExercisePopupService } from './quiz-exercise-popup.service';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise-dialog',
    templateUrl: './quiz-exercise-dialog.component.html'
})
export class QuizExerciseDialogComponent implements OnInit {

    quizExercise: QuizExercise;
    isSaving: boolean;

    constructor(
        public activeModal: NgbActiveModal,
        private quizExerciseService: QuizExerciseService,
        private eventManager: JhiEventManager
    ) {
    }

    ngOnInit() {
        this.isSaving = false;
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.isSaving = true;
        if (this.quizExercise.id !== undefined) {
            this.subscribeToSaveResponse(
                this.quizExerciseService.update(this.quizExercise));
        } else {
            this.subscribeToSaveResponse(
                this.quizExerciseService.create(this.quizExercise));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<QuizExercise>>) {
        result.subscribe((res: HttpResponse<QuizExercise>) =>
            this.onSaveSuccess(res.body), (res: HttpErrorResponse) => this.onSaveError());
    }

    private onSaveSuccess(result: QuizExercise) {
        this.eventManager.broadcast({ name: 'quizExerciseListModification', content: 'OK'});
        this.isSaving = false;
        this.activeModal.dismiss(result);
    }

    private onSaveError() {
        this.isSaving = false;
    }
}

@Component({
    selector: 'jhi-quiz-exercise-popup',
    template: ''
})
export class QuizExercisePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private quizExercisePopupService: QuizExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            if ( params['id'] ) {
                this.quizExercisePopupService
                    .open(QuizExerciseDialogComponent as Component, params['id']);
            } else {
                this.quizExercisePopupService
                    .open(QuizExerciseDialogComponent as Component);
            }
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
