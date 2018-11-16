import { Component, OnDestroy, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseDashboardPopupService } from './exercise-dashboard-popup.service';
import { Result, ResultService } from '../entities/result';
import { Participation } from '../entities/participation';
import { Feedback, FeedbackType } from '../entities/feedback';
import { JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Observable } from 'rxjs';

import { Subscription } from 'rxjs/Subscription';

@Component({
    selector: 'jhi-instructor-dashboard-result-dialog',
    templateUrl: './exercise-dashboard-result-dialog.component.html'
})
export class ExerciseDashboardResultDialogComponent implements OnInit {
    participation: Participation;
    result: Result;
    feedbacks: Feedback[] = [];
    isSaving = false;
    isOpenForSubmission = false;

    constructor(
        private resultService: ResultService,
        public activeModal: NgbActiveModal,
        private datePipe: DatePipe,
        private eventManager: JhiEventManager
    ) {}

    ngOnInit() {
        if (this.participation) {
            this.result.participation = this.participation;
            this.isOpenForSubmission =
                this.result.participation.exercise.dueDate === null || this.result.participation.exercise.dueDate.isAfter(moment());
        } else {
            this.clear();
        }
    }

    clear() {
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.result.feedbacks = this.feedbacks;
        this.isSaving = true;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        if (this.result.id != null) {
            this.subscribeToSaveResponse(this.resultService.update(this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.resultService.create(this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(res => this.onSaveSuccess(res), err => this.onSaveError());
    }

    onSaveSuccess(result: HttpResponse<Result>) {
        this.activeModal.close(result);
        this.isSaving = false;
        this.eventManager.broadcast({ name: 'resultListModification', content: 'Added a manual result' });
    }

    onSaveError() {
        this.isSaving = false;
    }

    pushFeedback() {
        this.feedbacks.push(new Feedback());
    }

    popFeedback() {
        if (this.feedbacks.length > 0) {
            this.feedbacks.pop();
        }
    }
}

@Component({
    selector: 'jhi-instructor-dashboard-result-popup',
    template: ''
})
export class InstructorDashboardResultPopupComponent implements OnInit, OnDestroy {
    routeSub: Subscription;

    constructor(private route: ActivatedRoute, private instructorDashboardPopupService: ExerciseDashboardPopupService) {}

    ngOnInit() {
        this.routeSub = this.route.params.subscribe(params => {
            this.instructorDashboardPopupService.open(
                ExerciseDashboardResultDialogComponent as Component,
                params['participationId'],
                false
            );
        });
    }

    ngOnDestroy() {
        this.routeSub.unsubscribe();
    }
}
