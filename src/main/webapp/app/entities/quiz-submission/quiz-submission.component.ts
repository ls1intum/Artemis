import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { IQuizSubmission } from 'app/shared/model/quiz-submission.model';
import { Principal } from 'app/core';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission',
    templateUrl: './quiz-submission.component.html'
})
export class QuizSubmissionComponent implements OnInit, OnDestroy {
    quizSubmissions: IQuizSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private quizSubmissionService: QuizSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {}

    loadAll() {
        this.quizSubmissionService.query().subscribe(
            (res: HttpResponse<IQuizSubmission[]>) => {
                this.quizSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }

    ngOnInit() {
        this.loadAll();
        this.principal.identity().then(account => {
            this.currentAccount = account;
        });
        this.registerChangeInQuizSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: IQuizSubmission) {
        return item.id;
    }

    registerChangeInQuizSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('quizSubmissionListModification', response => this.loadAll());
    }

    private onError(errorMessage: string) {
        this.jhiAlertService.error(errorMessage, null, null);
    }
}
