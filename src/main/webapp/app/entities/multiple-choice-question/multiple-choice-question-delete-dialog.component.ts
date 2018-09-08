import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IMultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Component({
    selector: 'jhi-multiple-choice-question-delete-dialog',
    templateUrl: './multiple-choice-question-delete-dialog.component.html'
})
export class MultipleChoiceQuestionDeleteDialogComponent {
    multipleChoiceQuestion: IMultipleChoiceQuestion;

    constructor(
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.multipleChoiceQuestionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'multipleChoiceQuestionListModification',
                content: 'Deleted an multipleChoiceQuestion'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-multiple-choice-question-delete-popup',
    template: ''
})
export class MultipleChoiceQuestionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ multipleChoiceQuestion }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(MultipleChoiceQuestionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.multipleChoiceQuestion = multipleChoiceQuestion;
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
