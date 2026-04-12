import { ChangeDetectionStrategy, Component, OnDestroy, computed, effect, inject, input, output, signal } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyExerciseLinkSnapshotDTO, ExerciseSnapshotDTO, GradingCriterionSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { normalizeCategoryArray } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot-shared.mapper';
import { serializeGradingCriteriaToMarkdown } from 'app/exercise/version-history/shared/grading-criteria-markdown.util';
import { ExerciseVersionMarkdownDiffComponent } from 'app/exercise/version-history/shared/exercise-version-markdown-diff.component';
import { MetadataFieldRowComponent } from 'app/exercise/version-history/shared/metadata-field-row.component';
import { VersionHistoryViewMode, booleanLabel, valuesDiffer } from 'app/exercise/version-history/shared/version-history.utils';
import { isRevertable } from 'app/exercise/version-history/shared/revert-field.registry';
import { CustomExerciseCategoryBadgeComponent } from 'app/exercise/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { ProgrammingExercisePlantUmlExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-plant-uml.extension';
import { ProgrammingExerciseTaskExtensionWrapper } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import dayjs from 'dayjs/esm';
import { Subscription } from 'rxjs';
import { faRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { PanelModule } from 'primeng/panel';
import { TagModule } from 'primeng/tag';
import { TooltipModule } from 'primeng/tooltip';

interface MetadataField {
    id: string;
    label: string;
    currentDisplay: string | number;
    previousDisplay: string | number;
    currentRaw?: string | number | boolean;
    previousRaw?: string | number | boolean;
    changed: boolean;
    currentEmpty: boolean;
    previousEmpty: boolean;
    revertable: boolean;
}

interface MetadataDateField {
    id: string;
    label: string;
    currentValue?: dayjs.Dayjs;
    previousValue?: dayjs.Dayjs;
    currentRaw?: string;
    previousRaw?: string;
    changed: boolean;
    revertable: boolean;
}

interface CompetencyEntry {
    key: string;
    label: string;
    weight: string;
}

@Component({
    selector: 'jhi-exercise-version-shared-snapshot-metadata',
    templateUrl: './exercise-version-shared-snapshot-metadata.component.html',
    styleUrls: ['./exercise-version-shared-snapshot-metadata.component.scss'],
    host: {
        '[style.display]': 'hostDisplay()',
    },
    imports: [
        PanelModule,
        DividerModule,
        TagModule,
        ButtonModule,
        TooltipModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        CustomExerciseCategoryBadgeComponent,
        ExerciseVersionMarkdownDiffComponent,
        MetadataFieldRowComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseVersionSharedSnapshotMetadataComponent implements OnDestroy {
    private readonly markdownService = inject(ArtemisMarkdownService);
    private readonly translateService = inject(TranslateService);
    private readonly plantUmlWrapper = inject(ProgrammingExercisePlantUmlExtensionWrapper);
    private readonly taskWrapper = inject(ProgrammingExerciseTaskExtensionWrapper);

    private readonly injectableCallbacks: Array<() => void> = [];
    private readonly injectableContentFoundSubscription: Subscription;
    private plantUmlTimeoutId?: ReturnType<typeof setTimeout>;

    readonly snapshot = input.required<ExerciseSnapshotDTO>();
    readonly previousSnapshot = input<ExerciseSnapshotDTO | undefined>();
    readonly viewMode = input<VersionHistoryViewMode>('full');

    readonly problemStatement = signal<SafeHtml | undefined>(undefined);
    readonly gradingInstructions = signal<SafeHtml | undefined>(undefined);

    readonly revertField = output<{ fieldId: string; fieldLabel: string; previousRaw: unknown }>();

    protected readonly faRotateLeft = faRotateLeft;

    readonly problemStatementDiffActions = [new FormulaAction(), new TaskAction(), new TestCaseAction()];

    readonly isDiffView = computed(() => this.viewMode() === 'changes' && !!this.previousSnapshot());

    constructor() {
        this.injectableContentFoundSubscription = this.plantUmlWrapper.subscribeForInjectableElementsFound().subscribe((injectableCallback) => {
            this.injectableCallbacks.push(injectableCallback);
        });

        effect(() => {
            this.renderMarkdownSections(this.snapshot());
        });
    }

    ngOnDestroy(): void {
        this.injectableContentFoundSubscription.unsubscribe();
        if (this.plantUmlTimeoutId !== undefined) {
            clearTimeout(this.plantUmlTimeoutId);
        }
    }

    readonly generalFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        const previousSnapshot = this.previousSnapshot();
        return this.filterFieldsForMode([
            this.toField('title', 'artemisApp.exercise.title', snapshot.title, previousSnapshot?.title),
            this.toField('shortName', 'artemisApp.exercise.shortName', snapshot.shortName, previousSnapshot?.shortName),
            this.toField('channelName', 'artemisApp.lecture.channelName', snapshot.channelName, previousSnapshot?.channelName),
            this.toField(
                'assessmentType',
                'artemisApp.assessmentMode',
                this.translateEnum('artemisApp.AssessmentType', snapshot.assessmentType),
                this.translateEnum('artemisApp.AssessmentType', previousSnapshot?.assessmentType),
            ),
            this.toField(
                'difficulty',
                'artemisApp.exercise.difficulty',
                this.translateEnum('artemisApp.DifficultyLevel', snapshot.difficulty),
                this.translateEnum('artemisApp.DifficultyLevel', previousSnapshot?.difficulty),
            ),
            this.toField('mode', 'artemisApp.exercise.mode', this.humanizeEnum(snapshot.mode), this.humanizeEnum(previousSnapshot?.mode)),
            this.toField('maxPoints', 'artemisApp.exercise.points', snapshot.maxPoints, previousSnapshot?.maxPoints),
            this.toField('bonusPoints', 'artemisApp.exercise.bonusPoints', snapshot.bonusPoints, previousSnapshot?.bonusPoints),
            this.toField(
                'includedInOverallScore',
                'artemisApp.exercise.includedInOverallScore',
                this.getIncludedInScoreLabel(snapshot.includedInOverallScore),
                this.getIncludedInScoreLabel(previousSnapshot?.includedInOverallScore),
            ),
            this.toField(
                'presentationScoreEnabled',
                'artemisApp.exercise.versionHistory.snapshot.presentationScoreEnabled',
                booleanLabel(this.translateService, snapshot.presentationScoreEnabled),
                booleanLabel(this.translateService, previousSnapshot?.presentationScoreEnabled),
            ),
            this.toField(
                'secondCorrectionEnabled',
                'artemisApp.exercise.versionHistory.snapshot.secondCorrectionEnabled',
                booleanLabel(this.translateService, snapshot.secondCorrectionEnabled),
                booleanLabel(this.translateService, previousSnapshot?.secondCorrectionEnabled),
            ),
            this.toField(
                'feedbackSuggestionModule',
                'artemisApp.exercise.versionHistory.snapshot.feedbackSuggestionModule',
                snapshot.feedbackSuggestionModule,
                previousSnapshot?.feedbackSuggestionModule,
            ),
        ]);
    });

    readonly dateFields = computed<MetadataDateField[]>(() => {
        const snapshot = this.snapshot();
        const previousSnapshot = this.previousSnapshot();
        return this.filterDateFieldsForMode([
            this.toDateField('releaseDate', 'artemisApp.exercise.releaseDate', snapshot.releaseDate, previousSnapshot?.releaseDate),
            this.toDateField('startDate', 'artemisApp.exercise.startDate', snapshot.startDate, previousSnapshot?.startDate),
            this.toDateField('dueDate', 'artemisApp.exercise.dueDate', snapshot.dueDate, previousSnapshot?.dueDate),
            this.toDateField('assessmentDueDate', 'artemisApp.exercise.assessmentDueDate', snapshot.assessmentDueDate, previousSnapshot?.assessmentDueDate),
            this.toDateField(
                'exampleSolutionPublicationDate',
                'artemisApp.exercise.exampleSolutionPublicationDate',
                snapshot.exampleSolutionPublicationDate,
                previousSnapshot?.exampleSolutionPublicationDate,
            ),
        ]);
    });

    readonly feedbackFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        const previousSnapshot = this.previousSnapshot();
        return this.filterFieldsForMode([
            this.toField(
                'allowFeedbackRequests',
                'artemisApp.programmingExercise.timeline.manualFeedbackRequests',
                booleanLabel(this.translateService, snapshot.allowFeedbackRequests),
                booleanLabel(this.translateService, previousSnapshot?.allowFeedbackRequests),
            ),
            this.toField(
                'allowComplaintsForAutomaticAssessments',
                'artemisApp.programmingExercise.timeline.complaintOnAutomaticAssessment',
                booleanLabel(this.translateService, snapshot.allowComplaintsForAutomaticAssessments),
                booleanLabel(this.translateService, previousSnapshot?.allowComplaintsForAutomaticAssessments),
            ),
        ]);
    });

    readonly teamAssignmentFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        const previousSnapshot = this.previousSnapshot();
        return this.filterFieldsForMode([
            this.toField(
                'teamAssignment.minTeamSize',
                'artemisApp.exercise.versionHistory.snapshot.teamAssignmentMinSize',
                snapshot.teamAssignmentConfig?.minTeamSize,
                previousSnapshot?.teamAssignmentConfig?.minTeamSize,
            ),
            this.toField(
                'teamAssignment.maxTeamSize',
                'artemisApp.exercise.versionHistory.snapshot.teamAssignmentMaxSize',
                snapshot.teamAssignmentConfig?.maxTeamSize,
                previousSnapshot?.teamAssignmentConfig?.maxTeamSize,
            ),
        ]);
    });

    readonly plagiarismFields = computed<MetadataField[]>(() => {
        const snapshot = this.snapshot();
        const previousSnapshot = this.previousSnapshot();
        return this.filterFieldsForMode([
            this.toField(
                'plagiarism.continuousPlagiarismControlEnabled',
                'artemisApp.exercise.versionHistory.snapshot.continuousPlagiarismControlEnabled',
                booleanLabel(this.translateService, snapshot.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled),
                booleanLabel(this.translateService, previousSnapshot?.plagiarismDetectionConfig?.continuousPlagiarismControlEnabled),
            ),
            this.toField(
                'plagiarism.continuousPlagiarismControlPostDueDateChecksEnabled',
                'artemisApp.exercise.versionHistory.snapshot.continuousPlagiarismControlPostDueDateChecksEnabled',
                booleanLabel(this.translateService, snapshot.plagiarismDetectionConfig?.continuousPlagiarismControlPostDueDateChecksEnabled),
                booleanLabel(this.translateService, previousSnapshot?.plagiarismDetectionConfig?.continuousPlagiarismControlPostDueDateChecksEnabled),
            ),
            this.toField(
                'plagiarism.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod',
                'artemisApp.exercise.versionHistory.snapshot.plagiarismCaseStudentResponsePeriod',
                snapshot.plagiarismDetectionConfig?.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
                previousSnapshot?.plagiarismDetectionConfig?.continuousPlagiarismControlPlagiarismCaseStudentResponsePeriod,
            ),
            this.toField(
                'plagiarism.similarityThreshold',
                'artemisApp.exercise.versionHistory.snapshot.similarityThreshold',
                snapshot.plagiarismDetectionConfig?.similarityThreshold,
                previousSnapshot?.plagiarismDetectionConfig?.similarityThreshold,
            ),
            this.toField(
                'plagiarism.minimumScore',
                'artemisApp.exercise.versionHistory.snapshot.minimumScore',
                snapshot.plagiarismDetectionConfig?.minimumScore,
                previousSnapshot?.plagiarismDetectionConfig?.minimumScore,
            ),
            this.toField(
                'plagiarism.minimumSize',
                'artemisApp.exercise.versionHistory.snapshot.minimumSize',
                snapshot.plagiarismDetectionConfig?.minimumSize,
                previousSnapshot?.plagiarismDetectionConfig?.minimumSize,
            ),
        ]);
    });

    readonly categories = computed(() => normalizeCategoryArray(this.snapshot().categories ?? []));
    readonly previousCategories = computed(() => normalizeCategoryArray(this.previousSnapshot()?.categories ?? []));
    readonly categoriesChanged = computed(() =>
        valuesDiffer(
            this.categories().map((category) => category.category),
            this.previousCategories().map((category) => category.category),
        ),
    );

    readonly competencyEntries = computed(() => this.toCompetencyEntries(this.snapshot().competencyLinks));
    readonly previousCompetencyEntries = computed(() => this.toCompetencyEntries(this.previousSnapshot()?.competencyLinks));
    readonly competenciesChanged = computed(() => valuesDiffer(this.snapshot().competencyLinks, this.previousSnapshot()?.competencyLinks));

    readonly problemStatementChanged = computed(() => valuesDiffer(this.snapshot().problemStatement, this.previousSnapshot()?.problemStatement));
    readonly problemStatementLabel = computed(() => this.translateLabel('artemisApp.exercise.versionHistory.snapshot.problemStatement'));

    readonly gradingCriteria = computed(() => this.sortGradingCriteria(this.snapshot().gradingCriteria));
    readonly gradingConfigurationChanged = computed(() =>
        valuesDiffer(
            { gradingInstructions: this.snapshot().gradingInstructions, gradingCriteria: this.snapshot().gradingCriteria },
            { gradingInstructions: this.previousSnapshot()?.gradingInstructions, gradingCriteria: this.previousSnapshot()?.gradingCriteria },
        ),
    );
    readonly previousGradingMarkdown = computed(() => serializeGradingCriteriaToMarkdown(this.previousSnapshot()?.gradingInstructions, this.previousSnapshot()?.gradingCriteria));
    readonly currentGradingMarkdown = computed(() => serializeGradingCriteriaToMarkdown(this.snapshot().gradingInstructions, this.snapshot().gradingCriteria));
    readonly hasVisibleContent = computed(() => {
        if (!this.isDiffView()) {
            return true;
        }
        return (
            this.generalFields().length > 0 ||
            this.dateFields().length > 0 ||
            this.categoriesChanged() ||
            this.competenciesChanged() ||
            this.feedbackFields().length > 0 ||
            this.teamAssignmentFields().length > 0 ||
            this.plagiarismFields().length > 0 ||
            this.problemStatementChanged() ||
            this.gradingConfigurationChanged()
        );
    });
    readonly hostDisplay = computed(() => (this.hasVisibleContent() ? 'block' : 'none'));

    private readonly fallbackLabels: Record<string, string> = {
        'artemisApp.exercise.versionHistory.snapshot.presentationScoreEnabled': 'Presentation Score Enabled',
        'artemisApp.exercise.versionHistory.snapshot.secondCorrectionEnabled': 'Second Correction Enabled',
        'artemisApp.exercise.versionHistory.snapshot.feedbackSuggestionModule': 'Feedback Suggestion Module',
        'artemisApp.exercise.versionHistory.snapshot.teamAssignment': 'Team Assignment',
        'artemisApp.exercise.versionHistory.snapshot.teamAssignmentMinSize': 'Minimum Team Size',
        'artemisApp.exercise.versionHistory.snapshot.teamAssignmentMaxSize': 'Maximum Team Size',
        'artemisApp.exercise.versionHistory.snapshot.competencies': 'Competencies',
        'artemisApp.exercise.versionHistory.snapshot.problemStatement': 'Problem Statement',
        'artemisApp.exercise.versionHistory.snapshot.gradingConfiguration': 'Grading Configuration',
        'artemisApp.exercise.versionHistory.snapshot.gradingCriteria': 'Grading Criteria',
        'artemisApp.exercise.versionHistory.snapshot.plagiarismDetection': 'Plagiarism Detection',
        'artemisApp.exercise.versionHistory.snapshot.continuousPlagiarismControlEnabled': 'Continuous Plagiarism Control',
        'artemisApp.exercise.versionHistory.snapshot.continuousPlagiarismControlPostDueDateChecksEnabled': 'Post Due Date Checks',
        'artemisApp.exercise.versionHistory.snapshot.plagiarismCaseStudentResponsePeriod': 'Student Response Period',
        'artemisApp.exercise.versionHistory.snapshot.similarityThreshold': 'Similarity Threshold',
        'artemisApp.exercise.versionHistory.snapshot.minimumScore': 'Minimum Score',
        'artemisApp.exercise.versionHistory.snapshot.minimumSize': 'Minimum Size',
    };

    private toField(id: string, labelKey: string, currentRaw?: string | number | boolean, previousRaw?: string | number | boolean): MetadataField {
        return {
            id,
            label: this.translateLabel(labelKey),
            currentDisplay: typeof currentRaw === 'boolean' ? String(currentRaw) : (currentRaw ?? '-'),
            previousDisplay: typeof previousRaw === 'boolean' ? String(previousRaw) : (previousRaw ?? '-'),
            currentRaw,
            previousRaw,
            changed: valuesDiffer(currentRaw, previousRaw),
            currentEmpty: currentRaw === undefined || currentRaw === '',
            previousEmpty: previousRaw === undefined || previousRaw === '',
            revertable: isRevertable(id),
        };
    }

    private toDateField(id: string, labelKey: string, currentValue?: string, previousValue?: string): MetadataDateField {
        const currentDate = this.toDate(currentValue);
        const previousDate = this.toDate(previousValue);
        return {
            id,
            label: this.translateLabel(labelKey),
            currentValue: currentDate,
            previousValue: previousDate,
            currentRaw: currentValue,
            previousRaw: previousValue,
            changed: valuesDiffer(currentValue, previousValue),
            revertable: isRevertable(id),
        };
    }

    private filterFieldsForMode(fields: MetadataField[]): MetadataField[] {
        return this.isDiffView() ? fields.filter((field) => field.changed) : fields;
    }

    private filterDateFieldsForMode(fields: MetadataDateField[]): MetadataDateField[] {
        return this.isDiffView() ? fields.filter((field) => field.changed) : fields;
    }

    private renderMarkdownSections(snapshot: ExerciseSnapshotDTO): void {
        if (this.plantUmlTimeoutId !== undefined) {
            clearTimeout(this.plantUmlTimeoutId);
        }
        this.injectableCallbacks.length = 0;
        this.plantUmlWrapper.setExerciseId(snapshot.id);

        this.problemStatement.set(this.renderMarkdown(snapshot.problemStatement));
        this.gradingInstructions.set(this.renderMarkdown(snapshot.gradingInstructions));

        this.plantUmlTimeoutId = setTimeout(() => {
            this.injectableCallbacks.forEach((callback) => callback());
        }, 0);
    }

    private renderMarkdown(markdown?: string): SafeHtml | undefined {
        if (!markdown?.trim()) {
            return undefined;
        }

        return this.markdownService.safeHtmlForMarkdown(markdown, [this.taskWrapper.getExtension(), this.plantUmlWrapper.getExtension()]);
    }

    private toDate(value?: string): dayjs.Dayjs | undefined {
        return value ? dayjs(value) : undefined;
    }

    private humanizeEnum(value?: string): string | undefined {
        return value ? value.replaceAll('_', ' ') : undefined;
    }

    private translateEnum(prefix: string, value?: string): string | undefined {
        if (!value) {
            return undefined;
        }
        const key = `${prefix}.${value}`;
        const translated = this.translateService.instant(key);
        return translated === key ? this.humanizeEnum(value) : translated;
    }

    private getIncludedInScoreLabel(value?: IncludedInOverallScore): string | undefined {
        if (!value) {
            return undefined;
        }

        switch (value) {
            case IncludedInOverallScore.INCLUDED_COMPLETELY:
                return this.translateService.instant('artemisApp.exercise.yes');
            case IncludedInOverallScore.NOT_INCLUDED:
                return this.translateService.instant('artemisApp.exercise.no');
            case IncludedInOverallScore.INCLUDED_AS_BONUS:
                return this.translateService.instant('artemisApp.exercise.bonus');
            default:
                return this.humanizeEnum(value);
        }
    }

    private translateLabel(key: string): string {
        const translated = this.translateService.instant(key);
        if (translated === key || translated.startsWith('translation-not-found[')) {
            return this.fallbackLabels[key] ?? key;
        }
        return translated;
    }

    private toCompetencyEntries(links?: CompetencyExerciseLinkSnapshotDTO[]): CompetencyEntry[] {
        return (links ?? [])
            .filter((link) => link.competencyId?.competencyId !== undefined)
            .map((link) => ({
                key: `${link.competencyId?.competencyId}-${link.weight ?? '-'}`,
                label: this.translateService.instant('artemisApp.exercise.versionHistory.snapshot.competencyLabel', { id: link.competencyId?.competencyId }),
                weight: String(link.weight ?? '-'),
            }))
            .sort((left, right) => left.label.localeCompare(right.label));
    }

    private sortGradingCriteria(criteria?: GradingCriterionSnapshotDTO[]): GradingCriterionSnapshotDTO[] {
        return [...(criteria ?? [])].sort((left, right) => {
            if (left.id !== undefined && right.id !== undefined) {
                return left.id - right.id;
            }
            return (left.title ?? '').localeCompare(right.title ?? '');
        });
    }
}
