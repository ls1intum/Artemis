import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable } from 'rxjs';
import { Subscription } from 'rxjs/Subscription';
import { catchError, flatMap, map, tap } from 'rxjs/operators';
import * as moment from 'moment';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/alert/alert.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { codeEditorTour } from 'app/guided-tour/tours/code-editor-tour';
import { CodeEditorBuildOutputComponent } from 'app/exercises/programming/shared/code-editor/build-output/code-editor-build-output.component';
import { ButtonSize } from 'app/shared/components/button.component';
import { CodeEditorSessionService } from 'app/exercises/programming/shared/code-editor/service/code-editor-session.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { CodeEditorFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-file.service';
import { CodeEditorActionsComponent } from 'app/exercises/programming/shared/code-editor/actions/code-editor-actions.component';
import { CodeEditorAceComponent } from 'app/exercises/programming/shared/code-editor/ace/code-editor-ace.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/code-editor-mode-container.component';
import { Feedback } from 'app/entities/feedback.model';
import { CodeEditorInstructionsComponent } from 'app/exercises/programming/shared/code-editor/instructions/code-editor-instructions.component';
import { CodeEditorFileBrowserComponent } from 'app/exercises/programming/shared/code-editor/file-browser/code-editor-file-browser.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { CommitState, DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { cloneDeep as _cloneDeep, orderBy as _orderBy } from 'lodash';
import { Complaint } from 'app/entities/complaint.model';
import { ComplaintResponse } from 'app/entities/complaint-response.model';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ProgrammingAssessmentManualResultService } from 'app/exercises/programming/assess/manual-result/programming-assessment-manual-result.service';
import { JhiEventManager } from 'ng-jhipster';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { Location } from '@angular/common';
import { AccountService } from 'app/core/auth/account.service';
import { ProgrammingSubmissionService } from 'app/exercises/programming/participate/programming-submission.service';
import { ComplaintService } from 'app/complaints/complaint.service';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-tutor-assessment-container.component.html',
})
export class CodeEditorTutorAssessmentContainerComponent extends CodeEditorContainerComponent implements OnInit, OnDestroy {
    @ViewChild(CodeEditorFileBrowserComponent, { static: false }) fileBrowser: CodeEditorFileBrowserComponent;
    @ViewChild(CodeEditorActionsComponent, { static: false }) actions: CodeEditorActionsComponent;
    @ViewChild(CodeEditorBuildOutputComponent, { static: false }) buildOutput: CodeEditorBuildOutputComponent;
    @ViewChild(CodeEditorInstructionsComponent, { static: false }) instructions: CodeEditorInstructionsComponent;
    @ViewChild(CodeEditorAceComponent, { static: false }) aceEditor: CodeEditorAceComponent;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    participationForAssessment: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    submission: ProgrammingSubmission;
    result: Result;
    userId: number;
    // for assessment-layout:
    isLoading = false;
    saveBusy = false;
    submitBusy = false;
    cancelBusy = false;
    nextSubmissionBusy = false;
    isAssessor = true;
    isAtLeastInstructor = false;
    complaint: Complaint;
    private cancelConfirmationText: string;
    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;

    constructor(
        private manualResultService: ProgrammingAssessmentManualResultService,
        private eventManager: JhiEventManager,
        private router: Router,
        private location: Location,
        private accountService: AccountService,
        private programmingSubmissionService: ProgrammingSubmissionService,
        private resultService: ResultService,
        private domainService: DomainService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private guidedTourService: GuidedTourService,
        private complaintService: ComplaintService,
        participationService: ParticipationService,
        translateService: TranslateService,
        route: ActivatedRoute,
        jhiAlertService: AlertService,
        sessionService: CodeEditorSessionService,
        fileService: CodeEditorFileService,
    ) {
        super(participationService, translateService, route, jhiAlertService, sessionService, fileService);
        translateService.get('artemisApp.assessment.messages.confirmCancel').subscribe((text) => (this.cancelConfirmationText = text));
    }

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        // Used to check if the assessor is the current user
        this.accountService.identity().then((user) => {
            this.userId = user!.id!;
        });
        this.isAtLeastInstructor = this.accountService.hasAnyAuthorityDirect(['ROLE_ADMIN', 'ROLE_INSTRUCTOR']);
        this.paramSub = this.route!.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;
            const participationId = Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
                    tap((participationWithResults) => {
                        // Set domain and commitState to make file editor work properly
                        this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults!]);
                        this.commitState = CommitState.UNDEFINED;
                        this.participation = <ProgrammingExerciseStudentParticipation>participationWithResults!;
                        this.submission = this.participation.results[0].submission as ProgrammingSubmission;
                        this.participationForAssessment = _cloneDeep(this.participation);
                        this.participationForAssessment.results = this.findManualResults(this.participationForAssessment.results);
                        this.result = this.participationForAssessment.results[0];
                        this.exercise = this.participation.exercise as ProgrammingExercise;
                    }),
                )
                .subscribe(
                    () => {
                        this.loadingParticipation = false;
                        this.guidedTourService.enableTourForExercise(this.exercise, codeEditorTour, true);
                    },
                    () => {
                        this.participationCouldNotBeFetched = true;
                        this.loadingParticipation = false;
                    },
                );
        });
    }

    findManualResults(results: Result[]): Result[] {
        const manualResult = results.filter((result) => result.assessmentType === AssessmentType.MANUAL);
        return manualResult ? _orderBy(manualResult, 'completionDate', 'desc') : [];
    }

    checkIfManualResultExist(): Result | null {
        return this.participationForAssessment.results ? this.participationForAssessment.results[0] : null;
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Load the participation from server with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<StudentParticipation | null> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            flatMap((participation: StudentParticipation) =>
                participation.results && participation.results.length
                    ? this.loadResultDetails(participation.results[0]).pipe(
                          map((feedback) => {
                              if (feedback) {
                                  participation.results[0].feedbacks = feedback;
                              }
                              return participation;
                          }),
                          catchError(() => Observable.of(participation)),
                      )
                    : Observable.of(participation),
            ),
        );
    }

    /**
     * Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadResultDetails(result: Result): Observable<Feedback[] | null> {
        return this.resultService.getFeedbackDetailsForResult(result.id).pipe(map((res) => res && res.body));
    }

    /**
     * Save the assessment
     */
    save(): void {
        this.saveBusy = true;
        this.manualResultService.save(this.participation.id, this.result).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.saveSuccessful'),
            (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
        );
    }

    /**
     * Submit the assessment
     */
    submit(): void {
        this.submitBusy = true;
        this.manualResultService.save(this.participation.id, this.result, true).subscribe(
            (response) => this.handleSaveOrSubmitSuccessWithAlert(response, 'artemisApp.textAssessment.submitSuccessful'),
            (error: HttpErrorResponse) => this.onError(`artemisApp.${error.error.entityName}.${error.error.message}`),
        );
    }

    /**
     * Cancel the assessment
     */
    cancel(): void {
        const confirmCancel = window.confirm(this.cancelConfirmationText);
        this.cancelBusy = true;
        if (confirmCancel && this.exercise && this.submission) {
            this.manualResultService.cancelAssessment(this.submission.id).subscribe(() => this.navigateBack());
        }
    }

    /**
     * Go to next submission
     */
    nextSubmission() {
        this.programmingSubmissionService.getProgrammingSubmissionForExerciseWithoutAssessment(this.exercise.id).subscribe(
            (response: ProgrammingSubmission) => {
                const unassessedSubmission = response;
                this.router.onSameUrlNavigation = 'reload';
                // navigate to the new assessment page to trigger re-initialization of the components
                this.router.navigateByUrl(
                    `/course-management/${this.exercise.course!.id}/programming-exercises/${this.exercise.id}/code-editor/${unassessedSubmission.participation.id}/assessment`,
                    {},
                );
            },
            (error: HttpErrorResponse) => {
                if (error.status === 404) {
                    // there are no unassessed submission, nothing we have to worry about
                    this.jhiAlertService.error('artemisApp.tutorExerciseDashboard.noSubmissions');
                } else {
                    this.onError(error.message);
                }
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
        this.manualResultService.updateAfterComplaint(this.result.feedbacks, complaintResponse, this.result, this.result!.submission!.id).subscribe(
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

    navigateBack() {
        if (this.exercise && this.exercise.teamMode && this.exercise.course && this.submission) {
            const teamId = (this.submission.participation as StudentParticipation).team.id;
            this.router.navigateByUrl(`/courses/${this.exercise.course.id}/exercises/${this.exercise.id}/teams/${teamId}`);
        } else if (this.exercise && !this.exercise.teamMode && this.exercise.course) {
            this.router.navigateByUrl(`/course-management/${this.exercise.course.id}/exercises/${this.exercise.id}/tutor-dashboard`);
        } else {
            this.location.back();
        }
    }

    /**
     * Boolean which determines whether the user can override a result.
     * If no exercise is loaded, for example during loading between exercises, we return false.
     * Instructors can always override a result.
     * Tutors can override their own results within the assessment due date, if there is no complaint about their assessment.
     * They cannot override a result anymore, if there is a complaint. Another tutor must handle the complaint.
     */
    get canOverride(): boolean {
        if (this.exercise) {
            if (this.isAtLeastInstructor) {
                // Instructors can override any assessment at any time.
                return true;
            }
            if (this.complaint && this.isAssessor) {
                // If there is a complaint, the original assessor cannot override the result anymore.
                return false;
            }
            let isBeforeAssessmentDueDate = true;
            // Add check as the assessmentDueDate must not be set for exercises
            if (this.exercise.assessmentDueDate) {
                isBeforeAssessmentDueDate = moment().isBefore(this.exercise.assessmentDueDate!);
            }
            // tutors are allowed to override one of their assessments before the assessment due date.
            return this.isAssessor && isBeforeAssessmentDueDate;
        }
        return false;
    }

    private handleSaveOrSubmitSuccessWithAlert(response: HttpResponse<Result>, translationKey: string): void {
        this.participation!.results[0] = this.result = response.body!;
        this.jhiAlertService.clear();
        this.jhiAlertService.success(translationKey);
        this.saveBusy = this.submitBusy = false;
    }

    onResultModified(result: Result) {
        this.result = result;
    }

    getComplaint(): void {
        this.complaintService.findByResultId(this.result.id).subscribe(
            (res) => {
                if (!res.body) {
                    return;
                }
                this.complaint = res.body;
            },
            (err: HttpErrorResponse) => {
                this.onError(err.message);
            },
        );
    }
    get assessmentsAreValid() {
        if (this.result && this.result.resultString) {
            return this.result.resultString.length > 0;
        } else {
            return false;
        }
    }
}
