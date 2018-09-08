import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IDragAndDropQuestion } from 'app/shared/model/drag-and-drop-question.model';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question-delete-dialog',
    templateUrl: './drag-and-drop-question-delete-dialog.component.html'
})
export class DragAndDropQuestionDeleteDialogComponent {
    dragAndDropQuestion: IDragAndDropQuestion;

    constructor(
        private dragAndDropQuestionService: DragAndDropQuestionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropQuestionService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'dragAndDropQuestionListModification',
                content: 'Deleted an dragAndDropQuestion'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-drag-and-drop-question-delete-popup',
    template: ''
})
export class DragAndDropQuestionDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ dragAndDropQuestion }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(DragAndDropQuestionDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.dragAndDropQuestion = dragAndDropQuestion;
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
