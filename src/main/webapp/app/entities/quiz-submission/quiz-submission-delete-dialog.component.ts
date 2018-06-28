import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { QuizSubmission } from './quiz-submission.model';
import { QuizSubmissionPopupService } from './quiz-submission-popup.service';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission-delete-dialog',
    templateUrl: './quiz-submission-delete-dialog.component.html'
})
export class QuizSubmissionDeleteDialogComponent {

    quizSubmission: QuizSubmission;

    constructor(
        private quizSubmissionService: QuizSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.quizSubmissionService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private quizSubmissionPopupService: QuizSubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.quizSubmissionPopupService
                .open(QuizSubmissionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
