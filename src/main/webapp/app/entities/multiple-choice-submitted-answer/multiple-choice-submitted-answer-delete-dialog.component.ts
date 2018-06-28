import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { MultipleChoiceSubmittedAnswer } from './multiple-choice-submitted-answer.model';
import { MultipleChoiceSubmittedAnswerPopupService } from './multiple-choice-submitted-answer-popup.service';
import { MultipleChoiceSubmittedAnswerService } from './multiple-choice-submitted-answer.service';

@Component({
    selector: 'jhi-multiple-choice-submitted-answer-delete-dialog',
    templateUrl: './multiple-choice-submitted-answer-delete-dialog.component.html'
})
export class MultipleChoiceSubmittedAnswerDeleteDialogComponent {

    multipleChoiceSubmittedAnswer: MultipleChoiceSubmittedAnswer;

    constructor(
        private multipleChoiceSubmittedAnswerService: MultipleChoiceSubmittedAnswerService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.multipleChoiceSubmittedAnswerService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private multipleChoiceSubmittedAnswerPopupService: MultipleChoiceSubmittedAnswerPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.multipleChoiceSubmittedAnswerPopupService
                .open(MultipleChoiceSubmittedAnswerDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
