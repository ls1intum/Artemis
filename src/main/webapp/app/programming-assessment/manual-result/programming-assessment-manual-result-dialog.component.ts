import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Result } from 'app/entities/result/result.model';
import { ResultService } from 'app/entities/result/result.service';
import { Feedback, FeedbackType } from '../../entities/feedback/feedback.model';
import { JhiAlertService, JhiEventManager } from 'ng-jhipster';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import * as moment from 'moment';
import { Observable, of } from 'rxjs';
import { ParticipationService } from 'app/entities/participation/participation.service';
import { catchError, tap, filter } from 'rxjs/operators';
import { ProgrammingAssessmentManualResultService } from 'app/programming-assessment/manual-result/programming-assessment-manual-result.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { Complaint, ComplaintType } from 'app/entities/complaint/complaint.model';
import { ComplaintService } from 'app/entities/complaint/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { ComplaintResponse } from 'app/entities/complaint-response/complaint-response.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation';
import { ProgrammingExercise } from 'app/entities/programming-exercise';

@Component({
    selector: 'jhi-exercise-scores-result-dialog',
    templateUrl: './programming-assessment-manual-result-dialog.component.html',
})
export class ProgrammingAssessmentManualResultDialogComponent implements OnInit {
    readonly SCORE_PATTERN = SCORE_PATTERN;
    readonly ComplaintType = ComplaintType;

    @Input() participationId: number;
    @Input() result: Result;
    @Input() exercise: ProgrammingExercise;
    @Output() onResultModified = new EventEmitter<Result>();

    participation: ProgrammingExerciseStudentParticipation;
    feedbacks: Feedback[] = [];
    isLoading = false;
    isSaving = false;
    isOpenForSubmission = false;
    userId: number;
    isAssessor: boolean;
    complaint: Complaint;
    resultModified: boolean;

    constructor(
        private participationService: ParticipationService,
        private manualResultService: ProgrammingAssessmentManualResultService,
        private activeModal: NgbActiveModal,
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
        // TODO: the participation needs additional information
        this.participation = this.result.participation! as ProgrammingExerciseStudentParticipation;
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
                    this.participation = participation! as ProgrammingExerciseStudentParticipation;
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
        if (this.resultModified) {
            this.onResultModified.emit(this.result);
        }
        this.activeModal.dismiss('cancel');
    }

    save() {
        this.result.feedbacks = this.feedbacks;
        this.isSaving = true;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        if (this.result.id != null) {
            this.subscribeToSaveResponse(this.manualResultService.update(this.participation.id, this.result));
        } else {
            // in case id is null or undefined
            this.subscribeToSaveResponse(this.manualResultService.create(this.participation.id, this.result));
        }
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

    private getComplaint(id: number): void {
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
                this.resultModified = true;
                this.jhiAlertService.clear();
                this.jhiAlertService.success('artemisApp.assessment.messages.updateAfterComplaintSuccessful');
            },
            () => {
                this.jhiAlertService.clear();
                this.jhiAlertService.error('artemisApp.assessment.messages.updateAfterComplaintFailed');
            },
        );
    }

    /**
     * the dialog is readonly if there is a complaint that was accepted or rejected
     */
    readOnly() {
        return this.complaint !== undefined && this.complaint.accepted !== undefined;
    }
}
