import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDragAndDropSubmittedAnswer } from 'app/shared/model/drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-delete-dialog',
    templateUrl: './drag-and-drop-submitted-answer-delete-dialog.component.html'
})
export class DragAndDropSubmittedAnswerDeleteDialogComponent {
    dragAndDropSubmittedAnswer: IDragAndDropSubmittedAnswer;

    constructor(
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropSubmittedAnswerService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dragAndDropSubmittedAnswerListModification',
                content: 'Deleted an dragAndDropSubmittedAnswer'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-delete-popup',
    template: ''
})
export class DragAndDropSubmittedAnswerDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropSubmittedAnswer }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DragAndDropSubmittedAnswerDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dragAndDropSubmittedAnswer = dragAndDropSubmittedAnswer;
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
