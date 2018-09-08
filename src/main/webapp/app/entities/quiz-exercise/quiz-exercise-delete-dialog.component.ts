import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IQuizExercise } from 'app/shared/model/quiz-exercise.model';
import { QuizExerciseService } from './quiz-exercise.service';

@Component({
    selector: 'jhi-quiz-exercise-delete-dialog',
    templateUrl: './quiz-exercise-delete-dialog.component.html'
})
export class QuizExerciseDeleteDialogComponent {
    quizExercise: IQuizExercise;

    constructor(
        private quizExerciseService: QuizExerciseService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.quizExerciseService.delete(id).subscribe(response => {
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
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizExercise }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuizExerciseDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.quizExercise = quizExercise;
                this.ngbModalRef.result.then(
                    result => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    },
                    reason => {
                        this.router.navigate([{ outlets: { popup: null } }], { replaceUrl: true, queryParamsHandling: 'merge' });
                        this.ngbModalRef = null;
                    }
                );
            }, 0);
        });
    }

    ngOnDestroy() {
        this.ngbModalRef = null;
    }
}
