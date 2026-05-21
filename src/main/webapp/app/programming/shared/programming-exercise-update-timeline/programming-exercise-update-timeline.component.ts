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
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from '../../../shared/exercise-timeline/exercise-timeline.component';
import { toSignal } from '@angular/core/rxjs-interop';

@Component({
    selector: 'jhi-programming-exercise-update-timeline',
    templateUrl: './programming-exercise-update-timeline.component.html',
    styleUrls: ['./programming-exercise-update-timeline.component.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, NgStyle, ExerciseFeedbackSuggestionOptionsComponent, ExerciseTimelineComponent],
})
export class ProgrammingExerciseUpdateTimelineComponent implements OnInit {
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);

    protected readonly AssessmentType = AssessmentType;

    isImport = this.getIsImportSignal();

    isExamMode = input.required<boolean>();
    complaintsInCourseEnabled = input(false);
    exampleSolutionPublicationDateSet = input(true);
    isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
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

    isDatePickerForReleaseDateVisible = computed(() => !this.isExamMode() && (this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord()?.releaseDate ?? true));
    isDatePickerForStartDateVisible = computed(() => !this.isExamMode() && (this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord()?.startDate ?? true));
    isDatePickerForDueDateVisible = computed(() => !this.isExamMode() && (this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord()?.dueDate ?? true));
    isEnablingToRunTestsAfterDueDateToggleVisible = computed(() => this.computeIsEnablingToRunTestsAfterDueDateToggleVisible());
    isDatePickerForRunningTestsAfterDueDateVisible = signal(false);
    isSemiAutomaticAssessmentToggleVisible = computed(() => this.computeIsSemiAutomaticAssessmentToggleVisible());
    isDatePickerForSemiAutomaticAssessmentDueDateVisible = computed<boolean>(() => this.computeIfDatePickableForSemiAutomaticAssessmentDueDateVisible());
    isExampleSolutionPublicationDateToggleVisible = computed(() => this.computeIsExampleSolutionPublicationDateToggleVisible());
    isDatePickerForExampleSolutionPublicationDateVisible = signal(false);

    timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    formValid: boolean;
    formEmpty: boolean;
    formValidChanges = new Subject<boolean>();
    isAthenaEnabled: boolean;

    constructor() {
        effect(() => {
            if (!this.isEnablingToRunTestsAfterDueDateToggleVisible()) {
                this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isDatePickerForSemiAutomaticAssessmentDueDateVisible()) {
                this.assessmentDueDate.set(undefined);
            }
        });
        effect(() => {
            if (!this.isExampleSolutionPublicationDateToggleVisible()) {
                this.isDatePickerForExampleSolutionPublicationDateVisible.set(false);
                this.exampleSolutionPublicationDate.set(undefined);
            }
        });
        effect(() => {
            if (this.assessmentType() === AssessmentType.SEMI_AUTOMATIC) {
                this.allowComplaintsForAutomaticAssessments.set(false);
                this.allowFeedbackRequests.set(false);
            } else if (this.assessmentType() === AssessmentType.AUTOMATIC) {
                this.assessmentDueDate.set(undefined);
                this.allowComplaintsForAutomaticAssessments.set(false);
                this.feedbackSuggestionModule.set(undefined);
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
        this.isDatePickerForRunningTestsAfterDueDateVisible.set(this.buildAndTestStudentSubmissionsAfterDueDate() !== undefined);
        this.isDatePickerForExampleSolutionPublicationDateVisible.set(this.exampleSolutionPublicationDate() !== undefined);

        if (!this.isImport() && this.assessmentType() === undefined) {
            this.assessmentType.set(AssessmentType.AUTOMATIC);
        }

        this.isAthenaEnabled = this.profileService.isProfileActive(PROFILE_ATHENA);
    }

    toggleAssessmentType() {
        this.assessmentType.update((assessmentType) => (assessmentType === AssessmentType.AUTOMATIC ? AssessmentType.SEMI_AUTOMATIC : AssessmentType.AUTOMATIC));
    }

    handleTimelineStatusChange(timelineStatus: ExerciseTimelineStatus) {
        this.formValid = timelineStatus.valid;
        this.formEmpty = timelineStatus.empty;
        this.formValidChanges.next(this.formValid);
    }

    private computeTimelineItems(): TimelineItem[] {
        const timelineItems: TimelineItem[] = [];
        if (this.isDatePickerForReleaseDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.releaseDate',
                date: this.releaseDate,
            });
        }
        if (this.isDatePickerForStartDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.startDate',
                date: this.startDate,
            });
        }
        if (this.isDatePickerForDueDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dueDate',
                date: this.dueDate,
            });
        }
        if (this.isDatePickerForRunningTestsAfterDueDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.dateForRunningTestsAfterDueDate',
                date: this.buildAndTestStudentSubmissionsAfterDueDate,
            });
        }
        if (this.isDatePickerForSemiAutomaticAssessmentDueDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.assessmentDueDate',
                date: this.assessmentDueDate,
            });
        }
        if (this.isDatePickerForExampleSolutionPublicationDateVisible()) {
            timelineItems.push({
                kind: 'optional',
                labelStringKey: 'artemisApp.exercise.exampleSolutionPublicationDate',
                date: this.exampleSolutionPublicationDate,
            });
        }

        return timelineItems;
    }

    private computeIsEnablingToRunTestsAfterDueDateToggleVisible(): boolean {
        const isInputDisplayedAccordingToCurrentModeRecord = this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord();
        const isInputDisplayedAccordingToCurrentMode = !isInputDisplayedAccordingToCurrentModeRecord || isInputDisplayedAccordingToCurrentModeRecord.runTestsAfterDueDate;
        return isInputDisplayedAccordingToCurrentMode && (this.isExamMode() || !!this.dueDate());
    }

    private computeIsSemiAutomaticAssessmentToggleVisible(): boolean {
        const isInputDisplayedAccordingToCurrentModeRecord = this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord();
        const isInputDisplayedAccordingToCurrentMode = !isInputDisplayedAccordingToCurrentModeRecord || isInputDisplayedAccordingToCurrentModeRecord.assessmentDueDate;
        return isInputDisplayedAccordingToCurrentMode && (this.isExamMode() || this.isImport() || !!this.dueDate());
    }

    private computeIfDatePickableForSemiAutomaticAssessmentDueDateVisible(): boolean {
        const isSemiAutomaticAssessmentToggleVisible = this.isSemiAutomaticAssessmentToggleVisible();
        const assessmentTypeIsSemiAutomatic = this.assessmentType() === AssessmentType.SEMI_AUTOMATIC;
        return isSemiAutomaticAssessmentToggleVisible && assessmentTypeIsSemiAutomatic && !this.isExamMode() && !this.allowFeedbackRequests();
    }

    private computeIsExampleSolutionPublicationDateToggleVisible(): boolean {
        const isInputDisplayedAccordingToCurrentModeRecord = this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord();
        const isInputDisplayedAccordingToCurrentMode = !isInputDisplayedAccordingToCurrentModeRecord || isInputDisplayedAccordingToCurrentModeRecord.exampleSolutionPublicationDate;
        return isInputDisplayedAccordingToCurrentMode && !this.isExamMode() && !this.isImport();
    }

    private getIsImportSignal(): Signal<boolean> {
        return toSignal(this.activatedRoute.url.pipe(map((segments) => segments.some((segment) => ['import', 'import-from-file'].includes(segment.path)))), {
            initialValue: false,
        });
    }
}
