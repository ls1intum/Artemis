import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Feedback } from './feedback.model';
import { FeedbackPopupService } from './feedback-popup.service';
import { FeedbackService } from './feedback.service';

@Component({
    selector: 'jhi-feedback-delete-dialog',
    templateUrl: './feedback-delete-dialog.component.html'
})
export class FeedbackDeleteDialogComponent {

    feedback: Feedback;

    constructor(
        private feedbackService: FeedbackService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.feedbackService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'feedbackListModification',
                content: 'Deleted an feedback'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-feedback-delete-popup',
    template: ''
})
export class FeedbackDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private feedbackPopupService: FeedbackPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.feedbackPopupService
                .open(FeedbackDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
