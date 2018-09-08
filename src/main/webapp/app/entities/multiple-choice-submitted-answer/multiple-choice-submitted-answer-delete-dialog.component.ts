import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IMultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-delete-dialog',
    templateUrl: './multiple-choice-submitted-answer-delete-dialog.component.html'
})
export class MultipleChoiceSubmittedAnswerDeleteDialogComponent {
    multipleChoiceSubmittedAnswer: IMultipleChoiceSubmittedAnswer;

    constructor(
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.multipleChoiceSubmittedAnswerService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'multipleChoiceSubmittedAnswerListModification',
                content: 'Deleted an multipleChoiceSubmittedAnswer'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-delete-popup',
    template: ''
})
export class MultipleChoiceSubmittedAnswerDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ multipleChoiceSubmittedAnswer }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(MultipleChoiceSubmittedAnswerDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.multipleChoiceSubmittedAnswer = multipleChoiceSubmittedAnswer;
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
