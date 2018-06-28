import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { MultipleChoiceQuestion } from './multiple-choice-question.model';
import { MultipleChoiceQuestionPopupService } from './multiple-choice-question-popup.service';
import { MultipleChoiceQuestionService } from './multiple-choice-question.service';

@Component({
    selector: 'jhi-multiple-choice-question-delete-dialog',
    templateUrl: './multiple-choice-question-delete-dialog.component.html'
})
export class MultipleChoiceQuestionDeleteDialogComponent {

    multipleChoiceQuestion: MultipleChoiceQuestion;

    constructor(
        private multipleChoiceQuestionService: MultipleChoiceQuestionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.multipleChoiceQuestionService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private multipleChoiceQuestionPopupService: MultipleChoiceQuestionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.multipleChoiceQuestionPopupService
                .open(MultipleChoiceQuestionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
