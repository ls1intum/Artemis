import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Submission } from './submission.model';
import { SubmissionPopupService } from './submission-popup.service';
import { SubmissionService } from './submission.service';

@Component({
    selector: 'jhi-submission-delete-dialog',
    templateUrl: './submission-delete-dialog.component.html'
})
export class SubmissionDeleteDialogComponent {

    submission: Submission;

    constructor(
        private submissionService: SubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.submissionService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'submissionListModification',
                content: 'Deleted an submission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-submission-delete-popup',
    template: ''
})
export class SubmissionDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private submissionPopupService: SubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.submissionPopupService
                .open(SubmissionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
