import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { Submission } from './submission.model';
import { SubmissionService } from './submission.service';

@Component({
    selector: 'jhi-submission-detail',
    templateUrl: './submission-detail.component.html'
})
export class SubmissionDetailComponent implements OnInit, OnDestroy {

    submission: Submission;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private submissionService: SubmissionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInSubmissions();
    }

    load(id) {
        this.submissionService.find(id)
            .subscribe((submissionResponse: HttpResponse<Submission>) => {
                this.submission = submissionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'submissionListModification',
            (response) => this.load(this.submission.id)
        );
    }
}
