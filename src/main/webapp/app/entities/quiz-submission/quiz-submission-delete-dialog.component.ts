import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission-delete-dialog',
    templateUrl: './quiz-submission-delete-dialog.component.html'
})
export class QuizSubmissionDeleteDialogComponent {
    quizSubmission: IQuizSubmission;

    constructor(
        private quizSubmissionService: QuizSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.quizSubmissionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'quizSubmissionListModification',
                content: 'Deleted an quizSubmission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-quiz-submission-delete-popup',
    template: ''
})
export class QuizSubmissionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizSubmission }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuizSubmissionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.quizSubmission = quizSubmission;
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
