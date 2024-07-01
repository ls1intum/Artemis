import { Component, Input, OnChanges, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen, faPenSquare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { AlertService } from 'app/core/util/alert.service';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { PROFILE_ATHENA } from 'app/app.constants';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
})
export class OpenCodeEditorButtonComponent implements OnInit, OnChanges {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    participations: ProgrammingExerciseStudentParticipation[];
    @Input()
    courseAndExerciseNavigationUrlSegment: any[];
    @Input()
    exercise: Exercise;

    courseAndExerciseNavigationUrl: string;
    activeParticipation: ProgrammingExerciseStudentParticipation;
    isPracticeMode: boolean = true;
    isExam: boolean;
    athenaEnabled = false;
    private feedbackSent = false;

    // Icons
    faFolderOpen = faFolderOpen;
    faPenSquare = faPenSquare;

    constructor(
        private participationService: ParticipationService,
        private alertService: AlertService,
        private courseExerciseService: CourseExerciseService,
        private translateService: TranslateService,
        private profileService: ProfileService,
    ) {}

    ngOnInit() {
        this.isExam = !!this.exercise.exerciseGroup;
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            this.athenaEnabled = profileInfo.activeProfiles?.includes(PROFILE_ATHENA);
        });
    }

    ngOnChanges() {
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`);
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, shouldPreferPractice) ?? this.participations[0];
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, this.isPracticeMode)!;
    }

    requestFeedback() {
        if (!this.assureConditionsSatisfied()) return;

        const confirmLockRepository = this.translateService.instant('artemisApp.exercise.lockRepositoryWarning');
        if (!window.confirm(confirmLockRepository)) {
            return;
        }

        this.courseExerciseService.requestFeedback(this.exercise.id!).subscribe({
            next: (participation: StudentParticipation) => {
                if (participation) {
                    this.feedbackSent = true;
                    this.alertService.success('artemisApp.exercise.feedbackRequestSent');
                }
            },
            error: (error) => {
                this.alertService.error(`artemisApp.${error.error.entityName}.errors.${error.error.errorKey}`);
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
        const latestResult = this.activeParticipation?.results && this.activeParticipation.results.find(({ assessmentType }) => assessmentType === AssessmentType.AUTOMATIC);
        const someHiddenTestsPassed = latestResult?.score !== undefined;
        const testsNotPassedWarning = this.translateService.instant('artemisApp.exercise.notEnoughPoints');
        if (!someHiddenTestsPassed) {
            window.alert(testsNotPassedWarning);
            return false;
        }

        const afterDueDate = !this.exercise.dueDate || dayjs().isSameOrAfter(this.exercise.dueDate);
        const dueDateWarning = this.translateService.instant('artemisApp.exercise.feedbackRequestAfterDueDate');
        if (afterDueDate) {
            window.alert(dueDateWarning);
            return false;
        }

        const requestAlreadySent = (this.activeParticipation?.individualDueDate && this.activeParticipation.individualDueDate.isBefore(Date.now())) ?? false;
        const requestAlreadySentWarning = this.translateService.instant('artemisApp.exercise.feedbackRequestAlreadySent');
        if (requestAlreadySent) {
            window.alert(requestAlreadySentWarning);
            return false;
        }

        if (this.activeParticipation?.results) {
            const athenaResults = this.activeParticipation.results.filter((result) => result.assessmentType === 'AUTOMATIC_ATHENA');
            const countOfSuccessfulRequests = athenaResults.filter((result) => result.successful === true).length;

            if (countOfSuccessfulRequests >= 20) {
                const rateLimitExceededWarning = this.translateService.instant('artemisApp.exercise.maxAthenaResultsReached');
                window.alert(rateLimitExceededWarning);
                return false;
            }
        }

        return true;
    }
}
