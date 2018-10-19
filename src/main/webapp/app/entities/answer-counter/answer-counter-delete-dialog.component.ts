import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IAnswerCounter } from 'app/shared/model/answer-counter.model';
import { AnswerCounterService } from './answer-counter.service';

@Component({
    selector: 'jhi-answer-counter-delete-dialog',
    templateUrl: './answer-counter-delete-dialog.component.html'
})
export class AnswerCounterDeleteDialogComponent {
    answerCounter: IAnswerCounter;

    constructor(
        private answerCounterService: AnswerCounterService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.answerCounterService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'answerCounterListModification',
                content: 'Deleted an answerCounter'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-answer-counter-delete-popup',
    template: ''
})
export class AnswerCounterDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ answerCounter }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(AnswerCounterDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.answerCounter = answerCounter;
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
