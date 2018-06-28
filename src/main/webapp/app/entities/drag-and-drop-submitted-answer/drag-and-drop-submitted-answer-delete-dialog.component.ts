import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropSubmittedAnswer } from './drag-and-drop-submitted-answer.model';
import { DragAndDropSubmittedAnswerPopupService } from './drag-and-drop-submitted-answer-popup.service';
import { DragAndDropSubmittedAnswerService } from './drag-and-drop-submitted-answer.service';

@Component({
    selector: 'jhi-drag-and-drop-submitted-answer-delete-dialog',
    templateUrl: './drag-and-drop-submitted-answer-delete-dialog.component.html'
})
export class DragAndDropSubmittedAnswerDeleteDialogComponent {

    dragAndDropSubmittedAnswer: DragAndDropSubmittedAnswer;

    constructor(
        private dragAndDropSubmittedAnswerService: DragAndDropSubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropSubmittedAnswerService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropSubmittedAnswerPopupService: DragAndDropSubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.dragAndDropSubmittedAnswerPopupService
                .open(DragAndDropSubmittedAnswerDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
