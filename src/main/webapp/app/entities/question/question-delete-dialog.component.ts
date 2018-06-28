import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { Question } from './question.model';
import { QuestionPopupService } from './question-popup.service';
import { QuestionService } from './question.service';

@Component({
    selector: 'jhi-question-delete-dialog',
    templateUrl: './question-delete-dialog.component.html'
})
export class QuestionDeleteDialogComponent {

    question: Question;

    constructor(
        private questionService: QuestionService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.questionService.delete(id).subscribe((response) => {
            this.eventManager.broadcast({
                name: 'questionListModification',
                content: 'Deleted an question'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-question-delete-popup',
    template: ''
})
export class QuestionDeletePopupComponent implements OnInit, OnDestroy {

    routeSub: any;

    constructor(
        private route: ActivatedRoute,
        private questionPopupService: QuestionPopupService
    ) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe((params) => {
            this.questionPopupService
                .open(QuestionDeleteDialogComponent as Component, params['id']);
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
