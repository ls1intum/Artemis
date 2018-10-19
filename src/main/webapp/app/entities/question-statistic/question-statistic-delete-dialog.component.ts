import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IQuestionStatistic } from 'app/shared/model/question-statistic.model';
import { QuestionStatisticService } from './question-statistic.service';

@Component({
    selector: 'jhi-question-statistic-delete-dialog',
    templateUrl: './question-statistic-delete-dialog.component.html'
})
export class QuestionStatisticDeleteDialogComponent {
    questionStatistic: IQuestionStatistic;

    constructor(
        private questionStatisticService: QuestionStatisticService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.questionStatisticService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'questionStatisticListModification',
                content: 'Deleted an questionStatistic'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-question-statistic-delete-popup',
    template: ''
})
export class QuestionStatisticDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ questionStatistic }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuestionStatisticDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.questionStatistic = questionStatistic;
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
