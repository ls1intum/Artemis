import { Component, Input, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result';
import { ResultService } from 'app/entities/result/result.service';
import { Feedback, FeedbackType } from '../../entities/feedback';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Observable, of } from 'rxjs';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ParticipationService } from 'app/entities/participation';
import { catchError, tap, filter } from 'rxjs/operators';
import { ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result/programming-assessment-manual-result.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { Complaint, ComplaintService, ComplaintType } from 'app/entities/complaint';
import { AccountService } from 'app/core';
import { ComplaintResponse } from 'app/entities/complaint-response';

@Component({
    selector: 'jhi-exercise-scores-result-dialog',
    templateUrl: './programming-assessment-manual-result-dialog.component.html',
})
export class ProgrammingAssessmentManualResultDialogComponent implements OnInit {
    SCORE_PATTERN = SCORE_PATTERN;
    @Input() participationId: number;
    @Input() result: Result;
    participation: StudentParticipation;
    feedbacks: Feedback[] = [];
    isLoading = false;
    isSaving = false;
    isOpenForSubmission = false;
    userId: number;
    isAssessor: boolean;
    complaint: Complaint;
    readonly ComplaintType = ComplaintType;

    constructor(
        private participationService: ParticipationService,
        private manualResultService: ProgrammingAssessmentManualResultService,
        public activeModal: NgbActiveModal,
        private datePipe: DatePipe,
        private eventManager: JhiEventManager,
        private alertService: JhiAlertService,
        private resultService: ResultService,
        private complaintService: ComplaintService,
        private accountService: AccountService,
        private jhiAlertService: JhiAlertService,
    ) {}

    ngOnInit() {
        // If there already is a manual result, update it instead of creating a new one.
        if (this.result) {
            this.initializeForResultUpdate();
            return;
        }
        this.initializeForResultCreation();
    }

    initializeForResultUpdate() {
        // Used to check if the assessor is the current user
        this.accountService.identity().then(user => {
            this.userId = user!.id!;
            this.isAssessor = this.result.assessor && this.result.assessor.id === this.userId;
        });
        if (this.result.feedbacks) {
            this.feedbacks = this.result.feedbacks;
        } else {
            this.isLoading = true;
            this.resultService
                .getFeedbackDetailsForResult(this.result.id)
                .pipe(
                    tap(({ body: feedbacks }) => {
                        this.feedbacks = feedbacks!;
                    }),
                )
                .subscribe(() => (this.isLoading = false));
        }
        if (this.result.hasComplaint) {
            this.getComplaint(this.result.id);
        }
        this.participation = this.result.participation! as StudentParticipation;
    }

    initializeForResultCreation() {
        this.isLoading = true;
        this.result = this.manualResultService.generateInitialManualResult();
        this.getParticipation();
    }

    getParticipation() {
        this.participationService
            .find(this.participationId)
            .pipe(
                tap(({ body: participation }) => {
                    this.participation = participation!;
                    this.result.participation = this.participation;
                    this.isOpenForSubmission = this.participation.exercise.dueDate === null || this.participation.exercise.dueDate.isAfter(moment());
                }),
                catchError((err: any) => {
                    this.alertService.error(err);
                    this.clear();
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
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
            this.subscribeToSaveResponse(this.manualResultService.update(this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.manualResultService.create(this.result));
        }
    }

    private subscribeToSaveResponse(result: Observable<HttpResponse<Result>>) {
        result.subscribe(res => this.onSaveSuccess(res), err => this.onSaveError());
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

    private getComplaint(id: number): void {
        if (this.result) {
            this.complaintService
                .findByResultId(id)
                .pipe(filter(res => !!res.body))
                .subscribe(
                    res => {
                        this.complaint = res.body!;
                    },
                    (err: HttpErrorResponse) => {
                        this.alertService.error(err.message);
                    },
                );
        }
    }

    /**
     * Sends the current (updated) assessment to the server to update the original assessment after a complaint was accepted.
     * The corresponding complaint response is sent along with the updated assessment to prevent additional requests.
     *
     * @param complaintResponse the response to the complaint that is sent to the server along with the assessment update
     */
    onUpdateAssessmentAfterComplaint(complaintResponse: ComplaintResponse): void {
        this.manualResultService.updateAfterComplaint(this.feedbacks, complaintResponse, this.result, this.result!.submission!.id).subscribe(
            (result: Result) => {
                this.result = result;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
            },
            () => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('artemisApp.assessment.messages.updateAfterComplaintFailed');
            },
        );
    }
}
