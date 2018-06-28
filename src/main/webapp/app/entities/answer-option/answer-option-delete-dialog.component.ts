import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { AnswerOption } from './answer-option.model';
import { AnswerOptionPopupService } from './answer-option-popup.service';
import { AnswerOptionService } from './answer-option.service';

@Component({
    selector: 'jhi-answer-option-delete-dialog',
    templateUrl: './answer-option-delete-dialog.component.html'
})
export class AnswerOptionDeleteDialogComponent {

    answerOption: AnswerOption;

    constructor(
        private answerOptionService: AnswerOptionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.answerOptionService.delete(id).subscribe((response) => {
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

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private answerOptionPopupService: AnswerOptionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.answerOptionPopupService
                .open(AnswerOptionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
