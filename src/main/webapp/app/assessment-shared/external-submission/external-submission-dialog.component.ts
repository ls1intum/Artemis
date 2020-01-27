import { Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result/result.model';
import { Feedback, FeedbackType } from '../../entities/feedback/feedback.model';
import { JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { Complaint } from 'app/entities/complaint/complaint.model';
import { Exercise } from 'app/entities/exercise';
import { ExternalSubmissionService } from 'app/assessment-shared/external-submission/external-submission.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { User } from 'app/core/user/user.model';

@Component({
    selector: 'jhi-external-submission-dialog',
    templateUrl: './external-submission-dialog.component.html',
})
export class ExternalSubmissionDialogComponent implements OnInit {
    readonly SCORE_PATTERN = SCORE_PATTERN;

    @Input() exercise: Exercise;

    student: User = new User();
    result: Result;
    feedbacks: Feedback[] = [];
    isSaving = false;
    userId: number;
    isAssessor: boolean;
    complaint: Complaint;

    constructor(
        private participationService: ParticipationService,
        private externalSubmissionService: ExternalSubmissionService,
        private activeModal: NgbActiveModal,
        private datePipe: DatePipe,
        private eventManager: JhiEventManager,
    ) {}

    ngOnInit() {
        this.initializeForResultCreation();
    }

    initializeForResultCreation() {
        this.result = this.externalSubmissionService.generateInitialManualResult();
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
        this.subscribeToSaveResponse(this.externalSubmissionService.create(this.exercise, this.student, this.result));
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(
            res => this.onSaveSuccess(res),
            () => this.onSaveError(),
        );
    }

    onSaveSuccess(result: HttpResponse<Result>) {
        this.activeModal.close(result.body);
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
