import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { NgbActiveModal, NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { JhiEventManager } from 'ng-jhipster';

import { IQuizPointStatistic } from 'app/shared/model/quiz-point-statistic.model';
import { QuizPointStatisticService } from './quiz-point-statistic.service';

@Component({
    selector: 'jhi-quiz-point-statistic-delete-dialog',
    templateUrl: './quiz-point-statistic-delete-dialog.component.html'
})
export class QuizPointStatisticDeleteDialogComponent {
    quizPointStatistic: IQuizPointStatistic;

    constructor(
        private quizPointStatisticService: QuizPointStatisticService,
        public activeModal: NgbActiveModal,
        private eventManager: JhiEventManager
    ) {}

    clear() {
        this.activeModal.dismiss('cancel');
    }

    confirmDelete(id: number) {
        this.quizPointStatisticService.delete(id).subscribe(response => {
            this.eventManager.broadcast({
                name: 'quizPointStatisticListModification',
                content: 'Deleted an quizPointStatistic'
            });
            this.activeModal.dismiss(true);
        });
    }
}

@Component({
    selector: 'jhi-quiz-point-statistic-delete-popup',
    template: ''
})
export class QuizPointStatisticDeletePopupComponent implements OnInit, OnDestroy {
    private ngbModalRef: NgbModalRef;

    constructor(private activatedRoute: ActivatedRoute, private router: Router, private modalService: NgbModal) {}

    ngOnInit() {
        this.activatedRoute.data.subscribe(({ quizPointStatistic }) => {
            setTimeout(() => {
                this.ngbModalRef = this.modalService.open(QuizPointStatisticDeleteDialogComponent as Component, {
                    size: 'lg',
                    backdrop: 'static'
                });
                this.ngbModalRef.componentInstance.quizPointStatistic = quizPointStatistic;
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
