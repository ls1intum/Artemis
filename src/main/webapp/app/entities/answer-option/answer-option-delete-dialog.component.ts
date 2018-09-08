import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IAnswerOption } from 'app/shared/model/answer-option.model';
import { AnswerOptionService } from './answer-option.service';

@Component({
    selector: 'jhi-answer-option-delete-dialog',
    templateUrl: './answer-option-delete-dialog.component.html'
})
export class AnswerOptionDeleteDialogComponent {
    answerOption: IAnswerOption;

    constructor(
        private answerOptionService: AnswerOptionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.answerOptionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'answerOptionListModification',
                content: 'Deleted an answerOption'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-answer-option-delete-popup',
    template: ''
})
export class AnswerOptionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ answerOption }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(AnswerOptionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.answerOption = answerOption;
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
