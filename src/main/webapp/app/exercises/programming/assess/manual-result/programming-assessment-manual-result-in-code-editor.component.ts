import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Result } from 'app/entities/result.model';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { Feedback, FeedbackType } from 'app/entities/feedback.model';
import { JhiEventManager } from 'ng-jhipster';
import { HttpResponse } from '@angular/common/http';
import * as moment from 'moment';
import { of } from 'rxjs';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { catchError, tap } from 'rxjs/operators';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { SCORE_PATTERN } from 'app/app.constants';
import { Complaint, ComplaintType } from 'app/entities/complaint.model';
import { ComplaintService } from 'app/complaints/complaint.service';
import { AccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AlertService } from 'app/core/alert/alert.service';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamInformationDTO } from 'app/entities/exam-information.model';

@Component({
    selector: 'jhi-programming-assessment-manual-result-in-code-editor',
    templateUrl: './programming-assessment-manual-result-in-code-editor.component.html',
})
export class ProgrammingAssessmentManualResultInCodeEditorComponent implements OnInit {
    readonly SCORE_PATTERN = SCORE_PATTERN;
    readonly ComplaintType = ComplaintType;

    @Input() participationId: number;
    @Input() result: Result;
    @Input() exercise: ProgrammingExercise;
    @Input() canOverride: boolean;
    @Input() complaint: Complaint;
    @Input() isTestRun = false;
    @Output() onResultModified = new EventEmitter<Result>();

    participation: ProgrammingExerciseStudentParticipation;
    feedbacks: Feedback[] = [];
    isLoading = false;
    isSaving = false;
    isOpenForSubmission = false;
    user: User;

    resultModified: boolean;

    constructor(
        private participationService: ParticipationService,
        private manualResultService: ProgrammingAssessmentManualResultService,
        private datePipe: DatePipe,
        private eventManager: JhiEventManager,
        private alertService: AlertService,
        private resultService: ResultService,
        private complaintService: ComplaintService,
        private accountService: AccountService,
        private jhiAlertService: AlertService,
        private examManagementService: ExamManagementService,
    ) {}

    /**
     * Creates or updates a manual result and checks permissions on component initialization
     */
    ngOnInit() {
        // If there already is a manual result, update it instead of creating a new one.
        this.accountService.identity().then((user) => {
            // Used to check if the assessor is the current user
            this.user = user!;
            if (this.result) {
                this.initializeForResultUpdate();
            } else {
                this.initializeForResultCreation();
            }
        });
    }
    /**
     * If the result has feedbacks, override the feedbacks of this instance with them.
     * Else get them from the result service and override the feedbacks of the instance.
     * Get the complaint of this result, if there is one.
     * Override the participation of this instance with its result's.
     */
    initializeForResultUpdate() {
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
        this.getParticipation();
    }

    /**
     * Generates a manual result and sets the result of this instance to it.
     * Sets its assessor to the current user.
     */
    initializeForResultCreation() {
        this.isLoading = true;
        this.result = this.manualResultService.generateInitialManualResult();
        this.result.assessor = this.user;
        this.getParticipation();
    }

    /**
     * Gets the participation of this instance from the participation service
     */
    getParticipation(): void {
        this.participationService
            .find(this.participationId)
            .pipe(
                tap(({ body: participation }) => {
                    this.participation = participation! as ProgrammingExerciseStudentParticipation;
                    this.result.participation = this.participation;
                    if (!!this.exercise.exerciseGroup) {
                        const exam = this.participation.exercise.exerciseGroup!.exam!;
                        this.examManagementService
                            .getLatestIndividualEndDateOfExam(exam.course!.id, exam.id)
                            .subscribe((res: HttpResponse<ExamInformationDTO>) => (this.isOpenForSubmission = res.body!.latestIndividualEndDate.isAfter(moment())));
                    } else {
                        this.isOpenForSubmission = this.participation.exercise.dueDate === null || this.participation.exercise.dueDate.isAfter(moment());
                    }
                }),
                catchError((err: any) => {
                    this.alertService.error(err);
                    return of(null);
                }),
            )
            .subscribe(() => {
                this.isLoading = false;
            });
    }

    /**
     * Pushes a new feedback to the feedbacks
     */
    pushFeedback() {
        this.feedbacks.push(new Feedback());
        this.updateResultFeedbacks();
    }

    /**
     * Pops a feedback out of feedbacks, if it is not empty
     */
    popFeedback() {
        if (this.feedbacks.length > 0) {
            this.feedbacks.pop();
        }
        this.updateResultFeedbacks();
    }

    /**
     * the dialog is readonly, if it is not writable
     */
    readOnly() {
        return !this.writable();
    }

    /**
     * the dialog is writable if the user can override the result
     * or if there is a complaint that was not yet accepted or rejected
     */
    writable() {
        // TODO: this is still not ideal and we should either distinguish between tutors and instructors here or allow to override accepted / rejected complaints
        // at the moment instructors can still edit already accepted / rejected complaints because the first condition is true, however we do not yet allow to override complaints
        return this.canOverride || (this.complaint !== undefined && this.complaint.accepted === undefined && this.result.assessor.id !== this.user.id);
    }
    /**
     * Updates if the result is successful (score of 100%) or not
     * and emits the updated result to the parent component
     */
    updateResultSuccess() {
        this.result.successful = this.result.score >= 100;
        this.onResultModified.emit(this.result);
    }

    /**
     * Updates the attribute feedbacks of the result, sets the feedback type to manual
     * and emits the updated result to the parent component
     */
    updateResultFeedbacks() {
        this.result.feedbacks = this.feedbacks;
        for (let i = 0; i < this.result.feedbacks.length; i++) {
            this.result.feedbacks[i].type = FeedbackType.MANUAL;
        }
        this.onResultModified.emit(this.result);
    }
}
