import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IModelingSubmission } from 'app/shared/model/modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-delete-dialog',
    templateUrl: './modeling-submission-delete-dialog.component.html'
})
export class ModelingSubmissionDeleteDialogComponent {
    modelingSubmission: IModelingSubmission;

    constructor(
        private modelingSubmissionService: ModelingSubmissionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.modelingSubmissionService.delete(id).subscribe(response => {
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
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ modelingSubmission }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(ModelingSubmissionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.modelingSubmission = modelingSubmission;
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
