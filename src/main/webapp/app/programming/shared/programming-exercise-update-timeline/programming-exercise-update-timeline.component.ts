import { Component, OnInit, Signal, computed, effect, inject, input, model, signal } from '@angular/core';
import { PROFILE_ATHENA } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { Dayjs } from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Subject } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs/operators';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgStyle } from '@angular/common';
import { ExerciseTimeline, ExerciseTimelineStatus, TimelineItem } from 'app/shared/exercise-timeline/exercise-timeline';
import { toSignal } from '@angular/core/rxjs-interop';

// TODO: decide whether to make exampleSolutionPublicationDate unavailable during import
@Component({
    selector: 'jhi-programming-exercise-update-timeline',
    templateUrl: './programming-exercise-update-timeline.component.html',
    styleUrls: ['./programming-exercise-update-timeline.component.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, NgStyle, ExerciseFeedbackSuggestionOptionsComponent, ExerciseTimeline],
})
export class ProgrammingExerciseUpdateTimelineComponent implements OnInit {
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);

    protected readonly AssessmentType = AssessmentType;

    isImport = this.getIsImportSignal();

    isExamMode = input.required<boolean>();
    complaintsInCourseDisabled = input(false);
    exampleSolutionPublicationDateSet = input(true);
    isEditFieldDisplayedRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    exercise = input.required<ProgrammingExercise>();

    releaseDate = model<Dayjs | undefined>();
    startDate = model<Dayjs | undefined>();
    dueDate = model<Dayjs | undefined>();
    buildAndTestStudentSubmissionsAfterDueDate = model<Dayjs | undefined>();
    assessmentDueDate = model<Dayjs | undefined>();
    exampleSolutionPublicationDate = model<Dayjs | undefined>();
    assessmentType = model<AssessmentType>();
    allowFeedbackRequests = model<boolean>();
    setTestCaseVisibilityToAfterDueDate = model<boolean>();
    allowComplaintsForAutomaticAssessments = model<boolean>();
    releaseTestsWithExampleSolution = model<boolean>();
    feedbackSuggestionModule = model<string>();
    showTestNamesToStudents = model<boolean>();

    isDatePickableForRunningTestsAfterDueDate = signal(false);
    isDatePickableForSemiAutomaticAssessmentDueDate = signal(false);
    isDatePickableForExampleSolutionPublicationDate = signal(this.exampleSolutionPublicationDate() !== undefined);

    isEnablingToRunTestsAfterDueDateToggleEnabled = computed(() => this.computeIsEnablingToRunTestsAfterDueDateToggleEnabled());
    isSemiAutomaticAssessmentToggleEnabled = computed(() => this.computeIsSemiAutomaticAssessmentToggleEnabled());
    isExampleSolutionPublicationDateToggleEnabled = computed(() => this.computeIsExampleSolutionPublicationDateToggleEnabled());
    timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

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
                this.allowComplaintsForAutomaticAssessments.set(false);
                this.allowFeedbackRequests.set(false);
            } else {
                this.assessmentType.set(AssessmentType.AUTOMATIC);
                this.assessmentDueDate.set(undefined);
                this.allowComplaintsForAutomaticAssessments.set(false);
                this.feedbackSuggestionModule.set(undefined);
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
        effect(() => {
            if (this.allowFeedbackRequests()) {
                this.assessmentDueDate.set(undefined);
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
    }

    ngOnInit(): void {
        this.isDatePickableForRunningTestsAfterDueDate.set(this.buildAndTestStudentSubmissionsAfterDueDate() !== undefined);
        this.isDatePickableForSemiAutomaticAssessmentDueDate.set(this.assessmentDueDate() !== undefined);
        this.isDatePickableForExampleSolutionPublicationDate.set(this.exampleSolutionPublicationDate() !== undefined);

        if (!this.isImport() && this.assessmentType() === undefined) {
            this.assessmentType.set(AssessmentType.AUTOMATIC);
        }

        this.isAthenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
    }

    handleTimelineStatusChange(timelineStatus: ExerciseTimelineStatus) {
        this.formValid = timelineStatus.valid;
        this.formEmpty = timelineStatus.empty;
        this.formValidChanges.next(this.formValid);
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

    private getIsImportSignal(): Signal<boolean> {
        return toSignal(this.activatedRoute.url.pipe(map((segments) => segments.some((segment) => ['import', 'import-from-file'].includes(segment.path)))), {
            initialValue: false,
        });
    }
}
