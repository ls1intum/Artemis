import { Component, OnDestroy, OnInit, TemplateRef, inject, input, output } from '@angular/core';
import { Subscription, filter, skip } from 'rxjs';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPenSquare } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_ATHENA } from 'app/app.constants';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateService } from '@ngx-translate/core';

import { isExamExercise } from 'app/shared/util/utils';
import { ExerciseDetailsType, ExerciseService } from 'app/exercise/services/exercise.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { AccountService } from 'app/core/auth/account.service';
import { UserService } from 'app/core/user/shared/user.service';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ParticipationWebsocketService } from 'app/core/course/shared/services/participation-websocket.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';

@Component({
    selector: 'jhi-request-feedback-button',
    imports: [NgbTooltipModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './request-feedback-button.component.html',
})
export class RequestFeedbackButtonComponent implements OnInit, OnDestroy {
    private readonly profileService = inject(ProfileService);
    private readonly alertService = inject(AlertService);
    private readonly courseExerciseService = inject(CourseExerciseService);
    private readonly translateService = inject(TranslateService);
    private readonly exerciseService = inject(ExerciseService);
    private readonly participationService = inject(ParticipationService);
    private readonly accountService = inject(AccountService);
    private readonly userService = inject(UserService);
    private readonly modalService = inject(NgbModal);
    private readonly participationWebsocketService = inject(ParticipationWebsocketService);

    protected readonly faPenSquare = faPenSquare;

    protected readonly ExerciseType = ExerciseType;

    athenaEnabled = false;
    requestFeedbackEnabled = false;
    isExamExercise: boolean;
    participation?: StudentParticipation;
    hasUserAcceptedExternalLLMUsage: boolean;
    currentFeedbackRequestCount = 0;
    feedbackRequestLimit = 10; // remark: this will be defined by the instructor and fetched

    isSubmitted = input<boolean>();
    pendingChanges = input<boolean>(false);
    hasAthenaResultForLatestSubmission = input<boolean>(false);
    isGeneratingFeedback = input<boolean>();
    smallButtons = input<boolean>(false);
    exercise = input.required<Exercise>();
    generatingFeedback = output<void>();

    private athenaResultUpdateListener?: Subscription;
    private acceptSubscription?: Subscription;

    ngOnInit() {
        this.athenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
        this.isExamExercise = isExamExercise(this.exercise());
        if (this.isExamExercise || !this.exercise().id) {
            return;
        }
        this.requestFeedbackEnabled = this.exercise().allowFeedbackRequests ?? false;
        this.updateParticipation();
        this.setUserAcceptedExternalLLMUsage();
    }
    ngOnDestroy(): void {
        this.athenaResultUpdateListener?.unsubscribe();
        this.acceptSubscription?.unsubscribe();
    }

    private updateParticipation() {
        if (this.exercise().id) {
            this.exerciseService.getExerciseDetails(this.exercise().id!).subscribe({
                next: (exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.participation = this.participationService.getSpecificStudentParticipation(exerciseResponse.body!.exercise.studentParticipations ?? [], false);
                    if (this.participation) {
                        this.currentFeedbackRequestCount =
                            getAllResultsOfAllSubmissions(this.participation.submissions)?.filter(
                                (result) => result.assessmentType == AssessmentType.AUTOMATIC_ATHENA && result.successful == true,
                            ).length ?? 0;
                        this.subscribeToResultUpdates();
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
        }
    }

    setUserAcceptedExternalLLMUsage(): void {
        this.hasUserAcceptedExternalLLMUsage = !!this.accountService.userIdentity()?.externalLLMUsageAccepted;
    }

    acceptExternalLLMUsage(modal: any) {
        this.acceptSubscription?.unsubscribe();
        this.acceptSubscription = this.userService.updateExternalLLMUsageConsent(true).subscribe(() => {
            this.hasUserAcceptedExternalLLMUsage = true;
            this.accountService.setUserAcceptedExternalLLMUsage();
            modal.close();
        });

        // Proceed with feedback request after accepting
        if (this.assureConditionsSatisfied()) {
            this.processFeedbackRequest();
        }
    }

    requestAIFeedback(content: TemplateRef<any>) {
        if (!this.hasUserAcceptedExternalLLMUsage) {
            this.modalService.open(content, { ariaLabelledBy: 'modal-title' });
            return;
        }
        this.requestFeedback();
    }

    private subscribeToResultUpdates() {
        if (!this.participation?.id) {
            return;
        }

        // Subscribe to result updates for this participation
        this.athenaResultUpdateListener = this.participationWebsocketService
            .subscribeForLatestResultOfParticipation(this.participation.id, true)
            .pipe(
                skip(1), // Skip initial value
                filter((result): result is Result => !!result),
                filter((result) => result.assessmentType === AssessmentType.AUTOMATIC_ATHENA),
            )
            .subscribe(this.handleAthenaAssessment.bind(this));
    }

    private handleAthenaAssessment(result: Result) {
        if (result.completionDate && result.successful) {
            this.currentFeedbackRequestCount += 1;
        }
    }

    requestFeedback() {
        if (!this.assureConditionsSatisfied()) {
            return;
        }
        this.processFeedbackRequest();
    }

    private processFeedbackRequest() {
        this.courseExerciseService.requestFeedback(this.exercise().id!).subscribe({
            next: (participation: StudentParticipation) => {
                if (participation) {
                    this.generatingFeedback.emit();
                    this.alertService.success('artemisApp.exercise.feedbackRequestSent');
                }
            },
            error: (error: HttpErrorResponse) => {
                this.alertService.error(`artemisApp.exercise.${error.error.errorKey}`);
            },
        });
    }

    /**
     * Checks if the conditions for requesting automatic non-graded feedback are satisfied.
     * The student can request automatic non-graded feedback under the following conditions:
     * 1. They have a graded submission.
     * 2. The deadline for the exercise has not been exceeded.
     * 3. There is no already pending feedback request.
     * @returns {boolean} `true` if all conditions are satisfied, otherwise `false`.
     */
    assureConditionsSatisfied(): boolean {
        return this.exercise().type === ExerciseType.PROGRAMMING || this.assureTextModelingConditions();
    }

    /**
     * Special conditions for text exercises.
     * Not more than 1 request per submission.
     * No request with pending changes (these would be overwritten after participation update)
     */
    assureTextModelingConditions(): boolean {
        if (this.hasAthenaResultForLatestSubmission()) {
            const submitFirstWarning = this.translateService.instant('artemisApp.exercise.submissionAlreadyHasAthenaResult');
            this.alertService.warning(submitFirstWarning);
            return false;
        }
        if (this.pendingChanges()) {
            const pendingChangesMessage = this.translateService.instant('artemisApp.exercise.feedbackRequestPendingChanges');
            this.alertService.warning(pendingChangesMessage);
            return false;
        }
        return true;
    }
}
