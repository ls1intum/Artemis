import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { Subscription, filter, skip } from 'rxjs';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPenSquare } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_ATHENA } from 'app/app.constants';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { TranslateService } from '@ngx-translate/core';

import { isExamExercise } from 'app/shared/util/utils';
import { ExerciseDetailsType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { ParticipationWebsocketService } from 'app/overview/participation-websocket.service';
import { Result } from 'app/entities/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-request-feedback-button',
    imports: [NgbTooltipModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective],
    templateUrl: './request-feedback-button.component.html',
})
export class RequestFeedbackButtonComponent implements OnInit, OnDestroy {
    faPenSquare = faPenSquare;
    athenaEnabled = false;
    requestFeedbackEnabled = false;
    isExamExercise: boolean;
    participation?: StudentParticipation;
    currentFeedbackRequestCount = 0;
    feedbackRequestLimit = 10; // remark: this will be defined by the instructor and fetched

    isSubmitted = input<boolean>();
    pendingChanges = input<boolean>(false);
    hasAthenaResultForLatestSubmission = input<boolean>(false);
    isGeneratingFeedback = input<boolean>();
    smallButtons = input<boolean>(false);
    exercise = input.required<Exercise>();
    generatingFeedback = output<void>();

    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private courseExerciseService = inject(CourseExerciseService);
    private translateService = inject(TranslateService);
    private exerciseService = inject(ExerciseService);
    private participationService = inject(ParticipationService);
    private participationWebsocketService = inject(ParticipationWebsocketService);

    private athenaResultUpdateListener?: Subscription;

    protected readonly ExerciseType = ExerciseType;

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
        });
        this.isExamExercise = isExamExercise(this.exercise());
        if (this.isExamExercise || !this.exercise().id) {
            return;
        }
        this.requestFeedbackEnabled = this.exercise().allowFeedbackRequests ?? false;
        this.updateParticipation();
    }
    ngOnDestroy(): void {
        this.athenaResultUpdateListener?.unsubscribe();
    }

    private updateParticipation() {
        if (this.exercise().id) {
            this.exerciseService.getExerciseDetails(this.exercise().id!).subscribe({
                next: (exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.participation = this.participationService.getSpecificStudentParticipation(exerciseResponse.body!.exercise.studentParticipations ?? [], false);
                    if (this.participation) {
                        this.currentFeedbackRequestCount =
                            this.participation.results?.filter((result) => result.assessmentType == AssessmentType.AUTOMATIC_ATHENA && result.successful == true).length ?? 0;
                        this.subscribeToResultUpdates();
                    }
                },
                error: (error: HttpErrorResponse) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
        }
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
        if (this.exercise().type === ExerciseType.PROGRAMMING || this.assureTextModelingConditions()) {
            return true;
        }
        return false;
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
