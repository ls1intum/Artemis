import { Component, OnDestroy, OnInit, computed, effect, inject, input, model, signal } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { Dayjs } from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { faUserCheck } from '@fortawesome/free-solid-svg-icons';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject, Subscription } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { tap } from 'rxjs/operators';
import { ImportOptions } from 'app/programming/manage/programming-exercises';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgStyle } from '@angular/common';
import { ExerciseTimeline, ExerciseTimelineStatus, TimelineItem } from 'app/shared/exercise-timeline/exercise-timeline';

// TODO: look at all usages and adapt styling of parent if necessary
@Component({
    selector: 'jhi-programming-exercise-timeline',
    templateUrl: './programming-exercise-timeline.component.html',
    styleUrls: ['./programming-exercise-timeline.component.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, NgStyle, ExerciseFeedbackSuggestionOptionsComponent, ExerciseTimeline],
})
export class ProgrammingExerciseTimelineComponent implements OnDestroy, OnInit {
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);
    private urlSubscription: Subscription;

    protected readonly AssessmentType = AssessmentType;
    protected readonly IncludedInOverallScore = IncludedInOverallScore;
    protected readonly faUserCheck = faUserCheck;
    protected readonly timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    buildAndTestStudentSubmissionsAfterDueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    exampleSolutionPublicationDate = model<Dayjs | undefined>();
    assessmentType = model<AssessmentType>();
    allowFeedbackRequests = model<boolean>();
    isExamMode = input.required<boolean>();
    exercise = input.required<ProgrammingExercise>();
    readOnly = input(false);
    importOptions = input<ImportOptions>();
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();

    isDatePickableForRunningTestsAfterDueDate = signal(false);
    isEnablingToRunTestsAfterDueDateToggleEnabled = computed(() => this.computeIsEnablingToRunTestsAfterDueDateToggleEnabled());
    isDatePickableForSemiAutomaticAssessmentDueDate = signal(false);
    isSemiAutomaticAssessmentToggleEnabled = computed(() => this.computeIsSemiAutomaticAssessmentToggleEnabled());
    isDatePickableForExampleSolutionPublicationDate = signal(this.exampleSolutionPublicationDate() !== undefined);
    isExampleSolutionPublicationDateToggleEnabled = computed(() => this.computeIsExampleSolutionPublicationDateToggleEnabled());

    isImport = signal(false);
    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();
    isAthenaEnabled: boolean;

    constructor() {
        effect(() => {
            if (!this.isEnablingToRunTestsAfterDueDateToggleEnabled()) {
                this.isDatePickableForRunningTestsAfterDueDate.set(false);
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isDatePickableForRunningTestsAfterDueDate()) {
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isSemiAutomaticAssessmentToggleEnabled()) {
                this.isDatePickableForSemiAutomaticAssessmentDueDate.set(false);
                this.assessmentDueDate.set(undefined);
            }
        });
        effect(() => {
            if (this.isDatePickableForSemiAutomaticAssessmentDueDate()) {
                this.assessmentType.set(AssessmentType.SEMI_AUTOMATIC);
            } else {
                this.assessmentType.set(AssessmentType.AUTOMATIC);
                this.assessmentDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isExampleSolutionPublicationDateToggleEnabled()) {
                this.isDatePickableForExampleSolutionPublicationDate.set(false);
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isDatePickableForExampleSolutionPublicationDate()) {
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
    }

    ngOnInit(): void {
        this.isDatePickableForRunningTestsAfterDueDate.set(this.buildAndTestStudentSubmissionsAfterDueDate() !== undefined);
        this.isDatePickableForSemiAutomaticAssessmentDueDate.set(this.assessmentDueDate() !== undefined);
        this.isDatePickableForExampleSolutionPublicationDate.set(this.exampleSolutionPublicationDate() !== undefined);
        this.updateIsImportBasedOnUrl();

        const exercise = this.exercise();
        if (!exercise.id && !this.isImport()) {
            exercise.assessmentType = AssessmentType.AUTOMATIC;
        }
        this.isAthenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
    }

    ngOnDestroy() {
        this.urlSubscription?.unsubscribe();
    }

    handleTimelineStatusChange(timelineStatus: ExerciseTimelineStatus) {
        this.formValid = timelineStatus.valid;
        this.formEmpty = timelineStatus.empty;
        this.formValidChanges.next(this.formValid);
    }

    toggleSetTestCaseVisibilityToAfterDueDate() {
        const importOptions = this.importOptions();
        if (importOptions) {
            importOptions.setTestCaseVisibilityToAfterDueDate = !importOptions.setTestCaseVisibilityToAfterDueDate;
        }
    }

    toggleFeedbackRequests() {
        const exercise = this.exercise();
        exercise.allowFeedbackRequests = !exercise.allowFeedbackRequests;
        if (exercise.allowFeedbackRequests) {
            exercise.assessmentDueDate = undefined;
            exercise.buildAndTestStudentSubmissionsAfterDueDate = undefined;
        }
    }

    toggleAssessmentType() {
        const exercise = this.exercise();
        if (exercise.assessmentType === AssessmentType.SEMI_AUTOMATIC) {
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.assessmentDueDate = undefined;
            exercise.allowComplaintsForAutomaticAssessments = false;
            exercise.feedbackSuggestionModule = undefined;
        } else {
            exercise.assessmentType = AssessmentType.SEMI_AUTOMATIC;
            exercise.allowComplaintsForAutomaticAssessments = false;
            exercise.allowFeedbackRequests = false;
        }
    }

    toggleComplaintsType() {
        const exercise = this.exercise();
        exercise.allowComplaintsForAutomaticAssessments = !exercise.allowComplaintsForAutomaticAssessments;
    }

    toggleReleaseTests() {
        const exercise = this.exercise();
        exercise.releaseTestsWithExampleSolution = !exercise.releaseTestsWithExampleSolution;
    }

    private computeTimelineItems(): TimelineItem[] {
        const timelineItems: TimelineItem[] = [
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.releaseDate',
                date: this.releaseDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
            },
            {
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dueDate',
                date: this.dueDate,
            },
        ];

        if (this.isDatePickableForRunningTestsAfterDueDate()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dateForRunningTestsAfterDueDate',
                date: this.buildAndTestStudentSubmissionsAfterDueDate,
            });
        }
        if (this.isDatePickableForSemiAutomaticAssessmentDueDate()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
            });
        }
        if (this.isDatePickableForExampleSolutionPublicationDate()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
            });
        }

        return timelineItems;
    }

    private computeIsEnablingToRunTestsAfterDueDateToggleEnabled(): boolean {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.runTestsAfterDueDate) && (this.isExamMode() || !!this.dueDate());
    }

    private computeIsSemiAutomaticAssessmentToggleEnabled(): boolean {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.assessmentDueDate) && (this.isImport() || !!this.dueDate() || this.isExamMode());
    }

    private computeIsExampleSolutionPublicationDateToggleEnabled(): boolean {
        const isFieldDisplayed = this.isEditFieldDisplayedRecord();
        return (!isFieldDisplayed || isFieldDisplayed.exampleSolutionPublicationDate) && !this.isExamMode();
    }

    private updateIsImportBasedOnUrl() {
        let isImportFromExistingExercise = false;
        let isImportFromFile = false;
        this.urlSubscription = this.activatedRoute.url
            .pipe(
                tap((segments) => {
                    isImportFromExistingExercise = segments.some((segment) => segment.path === 'import');
                    isImportFromFile = segments.some((segment) => segment.path === 'import-from-file');
                }),
            )
            .subscribe(() => {
                this.isImport.set(isImportFromExistingExercise || isImportFromFile);
            });
    }
}
