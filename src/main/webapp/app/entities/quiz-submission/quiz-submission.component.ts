import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager, JhiAlertService } from 'ng-jhipster';

import { QuizSubmission } from './quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';
import { Principal } from '../../shared';

@Component({
    selector: 'jhi-quiz-submission',
    templateUrl: './quiz-submission.component.html'
})
export class QuizSubmissionComponent implements OnInit, OnDestroy {
quizSubmissions: QuizSubmission[];
    currentAccount: any;
    eventSubscriber: Subscription;

    constructor(
        private quizSubmissionService: QuizSubmissionService,
        private jhiAlertService: JhiAlertService,
        private eventManager: JhiEventManager,
        private principal: Principal
    ) {
    }

    loadAll() {
        this.quizSubmissionService.query().subscribe(
            (res: HttpResponse<QuizSubmission[]>) => {
                this.quizSubmissions = res.body;
            },
            (res: HttpErrorResponse) => this.onError(res.message)
        );
    }
    ngOnInit() {
        this.loadAll();
        this.principal.identity().then((account) => {
            this.currentAccount = account;
        });
        this.registerChangeInQuizSubmissions();
    }

    ngOnDestroy() {
        this.eventManager.destroy(this.eventSubscriber);
    }

    trackId(index: number, item: QuizSubmission) {
        return item.id;
    }
    registerChangeInQuizSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe('quizSubmissionListModification', (response) => this.loadAll());
    }

    private onError(error) {
        this.jhiAlertService.error(error.message, null, null);
    }
}
