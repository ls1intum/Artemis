import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { QuizSubmission } from './quiz-submission.model';
import { QuizSubmissionService } from './quiz-submission.service';

@Component({
    selector: 'jhi-quiz-submission-detail',
    templateUrl: './quiz-submission-detail.component.html'
})
export class QuizSubmissionDetailComponent implements OnInit, OnDestroy {

    quizSubmission: QuizSubmission;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private quizSubmissionService: QuizSubmissionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInQuizSubmissions();
    }

    load(id) {
        this.quizSubmissionService.find(id)
            .subscribe((quizSubmissionResponse: HttpResponse<QuizSubmission>) => {
                this.quizSubmission = quizSubmissionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInQuizSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'quizSubmissionListModification',
            (response) => this.load(this.quizSubmission.id)
        );
    }
}
