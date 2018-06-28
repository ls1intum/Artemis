import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { DragAndDropQuestion } from './drag-and-drop-question.model';
import { DragAndDropQuestionPopupService } from './drag-and-drop-question-popup.service';
import { DragAndDropQuestionService } from './drag-and-drop-question.service';

@Component({
    selector: 'jhi-drag-and-drop-question-delete-dialog',
    templateUrl: './drag-and-drop-question-delete-dialog.component.html'
})
export class DragAndDropQuestionDeleteDialogComponent {

    dragAndDropQuestion: DragAndDropQuestion;

    constructor(
        private dragAndDropQuestionService: DragAndDropQuestionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.dragAndDropQuestionService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private dragAndDropQuestionPopupService: DragAndDropQuestionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.dragAndDropQuestionPopupService
                .open(DragAndDropQuestionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
