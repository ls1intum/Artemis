import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingSubmission } from './modeling-submission.model';
import { ModelingSubmissionPopupService } from './modeling-submission-popup.service';
import { ModelingSubmissionService } from './modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-delete-dialog',
    templateUrl: './modeling-submission-delete-dialog.component.html'
})
export class ModelingSubmissionDeleteDialogComponent {

    modelingSubmission: ModelingSubmission;

    constructor(
        private modelingSubmissionService: ModelingSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.modelingSubmissionService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'modelingSubmissionListModification',
                content: 'Deleted an modelingSubmission'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-modeling-submission-delete-popup',
    template: ''
})
export class ModelingSubmissionDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private modelingSubmissionPopupService: ModelingSubmissionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.modelingSubmissionPopupService
                .open(ModelingSubmissionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
