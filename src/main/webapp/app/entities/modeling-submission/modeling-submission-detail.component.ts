import { Component, OnInit, OnDestroy } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { Subscription } from 'rxjs/Subscription';
import { JhiEventManager } from 'ng-jhipster';

import { ModelingSubmission } from './modeling-submission.model';
import { ModelingSubmissionService } from './modeling-submission.service';

@Component({
    selector: 'jhi-modeling-submission-detail',
    templateUrl: './modeling-submission-detail.component.html'
})
export class ModelingSubmissionDetailComponent implements OnInit, OnDestroy {

    modelingSubmission: ModelingSubmission;
    private subscription: Subscription;
    private eventSubscriber: Subscription;

    constructor(
        private eventManager: JhiEventManager,
        private modelingSubmissionService: ModelingSubmissionService,
        private route: ActivatedRoute
    ) {
    }

    ngOnInit() {
        this.subscription = this.route.params.subscribe((params) => {
            this.load(params['id']);
        });
        this.registerChangeInModelingSubmissions();
    }

    load(id) {
        this.modelingSubmissionService.find(id)
            .subscribe((modelingSubmissionResponse: HttpResponse<ModelingSubmission>) => {
                this.modelingSubmission = modelingSubmissionResponse.body;
            });
    }
    previousState() {
        window.history.back();
    }

    ngOnDestroy() {
        this.subscription.unsubscribe();
        this.eventManager.destroy(this.eventSubscriber);
    }

    registerChangeInModelingSubmissions() {
        this.eventSubscriber = this.eventManager.subscribe(
            'modelingSubmissionListModification',
            (response) => this.load(this.modelingSubmission.id)
        );
    }
}
