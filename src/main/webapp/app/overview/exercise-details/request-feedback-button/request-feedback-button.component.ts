import { Component, OnInit, inject, input, output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { faPenSquare } from '@fortawesome/free-solid-svg-icons';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { PROFILE_ATHENA } from 'app/app.constants';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { isExamExercise } from 'app/shared/util/utils';
import { ExerciseDetailsType, ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-request-feedback-button',
    standalone: true,
    imports: [CommonModule, ArtemisSharedCommonModule, NgbTooltipModule, FontAwesomeModule],
    templateUrl: './request-feedback-button.component.html',
})
export class RequestFeedbackButtonComponent implements OnInit {
    faPenSquare = faPenSquare;
    athenaEnabled = false;
    isExamExercise: boolean;
    participation?: StudentParticipation;

    isGeneratingFeedback = input<boolean>();
    smallButtons = input<boolean>(false);
    exercise = input.required<Exercise>();
    generatingFeedback = output<void>();

    private feedbackSent = false;
    private profileService = inject(ProfileService);
    private alertService = inject(AlertService);
    private courseExerciseService = inject(CourseExerciseService);
    private translateService = inject(TranslateService);
    private exerciseService = inject(ExerciseService);
    private participationService = inject(ParticipationService);

    protected readonly ExerciseType = ExerciseType;

    ngOnInit() {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
        });
        this.isExamExercise = isExamExercise(this.exercise());
        if (this.isExamExercise || !this.exercise().id) {
            return;
        }
        this.updateParticipation();
    }

    private updateParticipation() {
        if (this.exercise().id) {
            this.exerciseService.getExerciseDetails(this.exercise().id!).subscribe({
                next: (exerciseResponse: HttpResponse<ExerciseDetailsType>) => {
                    this.participation = this.participationService.getSpecificStudentParticipation(exerciseResponse.body!.exercise.studentParticipations ?? [], false);
                },
                error: (error: HttpErrorResponse) => {
                    this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
                },
            });
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
                    this.feedbackSent = true;
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
        if (this.exercise().type !== ExerciseType.PROGRAMMING && this.hasAthenaResultForLatestSubmission()) {
            const submitFirstWarning = this.translateService.instant('artemisApp.exercise.submissionAlreadyHasAthenaResult');
            this.alertService.warning(submitFirstWarning);
            return false;
        }
        return true;
    }

    hasAthenaResultForLatestSubmission(): boolean {
        if (this.participation?.submissions && this.participation?.results) {
            // submissions.results is always undefined so this is neccessary
            return (
                this.participation.submissions?.last()?.id ===
                this.participation.results?.filter((result) => result.assessmentType == AssessmentType.AUTOMATIC_ATHENA).first()?.submission?.id
            );
        }
        return false;
    }
}
