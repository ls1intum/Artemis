import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { QuizExercise } from './quiz-exercise.model';
import { QuizExercisePopupService } from './quiz-exercise-popup.service';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise-delete-dialog',
    templateUrl: './quiz-exercise-delete-dialog.component.html'
})
export class QuizExerciseDeleteDialogComponent {

    quizExercise: QuizExercise;

    constructor(
        private quizExerciseService: QuizExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.quizExerciseService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'quizExerciseListModification',
                content: 'Deleted an quizExercise'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-quiz-exercise-delete-popup',
    template: ''
})
export class QuizExerciseDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private quizExercisePopupService: QuizExercisePopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.quizExercisePopupService
                .open(QuizExerciseDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
