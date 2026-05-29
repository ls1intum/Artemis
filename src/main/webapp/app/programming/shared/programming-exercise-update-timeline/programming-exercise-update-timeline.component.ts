import { Component, OnInit, Signal, computed, effect, inject, input, model, signal } from '@angular/core';
import { MODULE_FEATURE_ATHENA, PROFILE_LOCALCI } from 'app/app.constants';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercise/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { Dayjs } from 'dayjs/esm';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { Subject } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { map } from 'rxjs/operators';
import { ProgrammingExerciseInputField } from 'app/programming/manage/update/programming-exercise-update.helper';
import { AutomaticAfterDueDatePreviewRequest, ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { NgStyle } from '@angular/common';
import { ExerciseTimelineComponent, ExerciseTimelineStatus, TimelineItem } from '../../../shared/exercise-timeline/exercise-timeline.component';
import { toSignal } from '@angular/core/rxjs-interop';
import { BuildPhasesTemplateService } from 'app/programming/shared/services/build-phases-template.service';
import { parseBuildPlanPhases } from 'app/programming/shared/entities/build-plan-phases.model';
import { findParamInRouteHierarchy } from 'app/shared/util/navigation.utils';
import { convertDateFromClient } from 'app/shared/util/date.utils';
import { isEqual } from 'lodash-es';

@Component({
    selector: 'jhi-programming-exercise-update-timeline',
    templateUrl: './programming-exercise-update-timeline.component.html',
    styleUrls: ['./programming-exercise-update-timeline.component.scss'],
    imports: [FormsModule, TranslateDirective, HelpIconComponent, NgStyle, ExerciseFeedbackSuggestionOptionsComponent, ExerciseTimelineComponent],
})
export class ProgrammingExerciseUpdateTimelineComponent implements OnInit {
    private profileService = inject(ProfileService);
    private activatedRoute = inject(ActivatedRoute);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private buildPhasesTemplateService = inject(BuildPhasesTemplateService);

    protected readonly AssessmentType = AssessmentType;

    isImport = this.getIsImportSignal();

    isExamMode = input.required<boolean>();
    complaintsInCourseEnabled = input(false);
    exampleSolutionPublicationDateSet = input(true);
    isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord = input<Record<ProgrammingExerciseInputField, boolean>>();
    customizeBuildPlan = input<boolean | undefined>(undefined);
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
    isEnablingToRunTestsAfterDueDateToggleEnabled = computed(() => this.isExamMode() || !!this.dueDate());
    isDatePickerForRunningTestsAfterDueDateVisible = signal(false);
    isSemiAutomaticAssessmentToggleVisible = computed(() => this.computeIsSemiAutomaticAssessmentToggleVisible());
    isSemiAutomaticAssessmentToggleEnabled = computed(() => this.isExamMode() || this.isImport() || !!this.dueDate());
    isDatePickerForSemiAutomaticAssessmentDueDateVisible = computed<boolean>(() => this.computeIfDatePickableForSemiAutomaticAssessmentDueDateVisible());
    isFeedbackRequestsToggleEnabled = computed(() => this.computeIsFeedbackRequestsToggleEnabled());
    isExampleSolutionPublicationDateToggleVisible = computed(() => this.computeIsExampleSolutionPublicationDateToggleVisible());
    isDatePickerForExampleSolutionPublicationDateVisible = signal(false);

    timelineItems = computed<TimelineItem[]>(() => this.computeTimelineItems());

    formValid = true;
    formEmpty = false;
    formValidChanges = new Subject<boolean>();
    isAthenaEnabled = this.profileService.isModuleFeatureActive(MODULE_FEATURE_ATHENA);
    isLocalCIEnabled = this.profileService.isProfileActive(PROFILE_LOCALCI);
    private previousAutomaticAfterDueDatePreviewRequest: AutomaticAfterDueDatePreviewRequest | undefined = undefined;

    constructor() {
        effect(() => {
            if (this.isLocalCIEnabled) {
                return;
            }
            if (!this.isEnablingToRunTestsAfterDueDateToggleVisible()) {
                this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
            }
            if (!this.isEnablingToRunTestsAfterDueDateToggleEnabled()) {
                this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
            }
            if (!this.isDatePickerForRunningTestsAfterDueDateVisible()) {
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
            }
            if (!this.isDatePickerForExampleSolutionPublicationDateVisible()) {
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
                this.allowFeedbackRequests.set(false);
                this.feedbackSuggestionModule.set(undefined);
            }
        });
        effect(() => {
            if (this.allowFeedbackRequests()) {
                this.assessmentDueDate.set(undefined);
                if (!this.isLocalCIEnabled) {
                    this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
                }
            }
        });
        effect(() => {
            if (this.isLocalCIEnabled && this.buildAndTestStudentSubmissionsAfterDueDate() && this.allowFeedbackRequests()) {
                this.allowFeedbackRequests.set(false);
            }
        });
        effect(() => {
            this.buildPhasesTemplateService.buildPlan();
            this.customizeBuildPlan();
            this.updateAutomaticAfterDueDatePreview();
        });
    }

    ngOnInit(): void {
        this.isDatePickerForRunningTestsAfterDueDateVisible.set(this.buildAndTestStudentSubmissionsAfterDueDate() !== undefined);
        this.isDatePickerForExampleSolutionPublicationDateVisible.set(this.exampleSolutionPublicationDate() !== undefined);

        if (!this.isImport() && this.assessmentType() === undefined) {
            this.assessmentType.set(AssessmentType.AUTOMATIC);
        }

        this.updateAutomaticAfterDueDatePreview();
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
                clearable: !this.isLocalCIEnabled,
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
        return (!isInputDisplayedAccordingToCurrentModeRecord || isInputDisplayedAccordingToCurrentModeRecord.runTestsAfterDueDate) && !this.isLocalCIEnabled;
    }

    private computeIsSemiAutomaticAssessmentToggleVisible(): boolean {
        const isInputDisplayedAccordingToCurrentModeRecord = this.isInputDisplayedAccordingToCurrentOfSimpleOrAdvancedModeRecord();
        return !isInputDisplayedAccordingToCurrentModeRecord || isInputDisplayedAccordingToCurrentModeRecord.assessmentDueDate;
    }

    private computeIfDatePickableForSemiAutomaticAssessmentDueDateVisible(): boolean {
        const isSemiAutomaticAssessmentToggleVisible = this.isSemiAutomaticAssessmentToggleVisible();
        const isSemiAutomaticAssessmentToggleEnabled = this.isSemiAutomaticAssessmentToggleEnabled();
        const assessmentTypeIsSemiAutomatic = this.assessmentType() === AssessmentType.SEMI_AUTOMATIC;
        return (
            isSemiAutomaticAssessmentToggleVisible && isSemiAutomaticAssessmentToggleEnabled && assessmentTypeIsSemiAutomatic && !this.isExamMode() && !this.allowFeedbackRequests()
        );
    }

    private computeIsFeedbackRequestsToggleEnabled(): boolean {
        return this.assessmentType() === AssessmentType.SEMI_AUTOMATIC && !(this.isLocalCIEnabled && this.buildAndTestStudentSubmissionsAfterDueDate());
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

    private updateAutomaticAfterDueDatePreview() {
        if (!this.isLocalCIEnabled || !this.exercise()) {
            return;
        }

        const dueDate = this.dueDate();
        if (!dueDate && !this.isExamMode()) {
            this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
            this.previousAutomaticAfterDueDatePreviewRequest = undefined;
            return;
        }

        const exercise = this.exercise();
        const routeExamId = findParamInRouteHierarchy(this.activatedRoute, 'examId');

        let hasAfterDueDateBuildPhase: boolean | undefined = undefined;
        if (this.customizeBuildPlan()) {
            hasAfterDueDateBuildPhase = !!this.buildPhasesTemplateService.buildPlan()?.phases?.some((phase) => phase.condition === 'AFTER_DUE_DATE');
        } else if (this.isImport()) {
            hasAfterDueDateBuildPhase = this.getImportedHasAfterDueDateBuildPhase();
        }

        const requestData: AutomaticAfterDueDatePreviewRequest = {
            programmingExerciseId: exercise.id,
            examId: this.isExamMode() ? (routeExamId ? Number(routeExamId) : undefined) : undefined,
            dueDate: this.isExamMode() ? undefined : convertDateFromClient(dueDate),
            hasAfterDueDateBuildPhase: hasAfterDueDateBuildPhase,
            programmingLanguage: exercise.programmingLanguage!,
            projectType: exercise.projectType,
            staticCodeAnalysisEnabled: !!exercise.staticCodeAnalysisEnabled,
            sequentialTestRuns: !!exercise.buildConfig?.sequentialTestRuns,
        };

        if (isEqual(requestData, this.previousAutomaticAfterDueDatePreviewRequest)) {
            return;
        }
        this.previousAutomaticAfterDueDatePreviewRequest = requestData;

        if (requestData.hasAfterDueDateBuildPhase === false) {
            this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
            this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
            return;
        }

        this.programmingExerciseService.previewAutomaticAfterDueDateDate(requestData).subscribe({
            next: (previewDate) => {
                this.buildAndTestStudentSubmissionsAfterDueDate.set(previewDate);
                this.isDatePickerForRunningTestsAfterDueDateVisible.set(previewDate !== undefined);
            },
            error: () => {
                this.buildAndTestStudentSubmissionsAfterDueDate.set(undefined);
                this.isDatePickerForRunningTestsAfterDueDateVisible.set(false);
                this.previousAutomaticAfterDueDatePreviewRequest = undefined;
            },
        });
    }

    private getImportedHasAfterDueDateBuildPhase(): boolean | undefined {
        const parsedBuildPlan = parseBuildPlanPhases(this.exercise().buildConfig?.buildPlanConfiguration);
        if (!parsedBuildPlan) {
            return undefined;
        }

        return parsedBuildPlan.phases.some((phase) => phase.condition === 'AFTER_DUE_DATE');
    }
}
