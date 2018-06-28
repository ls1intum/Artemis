import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { SubmittedAnswer } from './submitted-answer.model';
import { SubmittedAnswerPopupService } from './submitted-answer-popup.service';
import { SubmittedAnswerService } from './submitted-answer.service';

@Component({
    selector: 'jhi-submitted-answer-delete-dialog',
    templateUrl: './submitted-answer-delete-dialog.component.html'
})
export class SubmittedAnswerDeleteDialogComponent {

    submittedAnswer: SubmittedAnswer;

    constructor(
        private submittedAnswerService: SubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.submittedAnswerService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'submittedAnswerListModification',
                content: 'Deleted an submittedAnswer'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-submitted-answer-delete-popup',
    template: ''
})
export class SubmittedAnswerDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private submittedAnswerPopupService: SubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.submittedAnswerPopupService
                .open(SubmittedAnswerDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
