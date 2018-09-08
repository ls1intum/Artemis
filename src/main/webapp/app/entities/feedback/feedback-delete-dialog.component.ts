import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IFeedback } from 'app/shared/model/feedback.model';
import { FeedbackService } from './feedback.service';

@Component({
    selector: 'jhi-feedback-delete-dialog',
    templateUrl: './feedback-delete-dialog.component.html'
})
export class FeedbackDeleteDialogComponent {
    feedback: IFeedback;

    constructor(private feedbackService: FeedbackService, public activeModal: NgbActiveModal, private eventManager: JhiEventManager) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.feedbackService.delete(id).subscribe(response => {
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
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ feedback }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(FeedbackDeleteDialogComponent as Component, { size: 'lg', backdrop: 'static' });
                this.ngbModalRef.componentInstance.feedback = feedback;
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
