import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { ISubmittedAnswer } from 'app/shared/model/submitted-answer.model';
import { SubmittedAnswerService } from './submitted-answer.service';

@Component({
    selector: 'jhi-submitted-answer-delete-dialog',
    templateUrl: './submitted-answer-delete-dialog.component.html'
})
export class SubmittedAnswerDeleteDialogComponent {
    submittedAnswer: ISubmittedAnswer;

    constructor(
        private submittedAnswerService: SubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.submittedAnswerService.delete(id).subscribe(response => {
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
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ submittedAnswer }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(SubmittedAnswerDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.submittedAnswer = submittedAnswer;
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
