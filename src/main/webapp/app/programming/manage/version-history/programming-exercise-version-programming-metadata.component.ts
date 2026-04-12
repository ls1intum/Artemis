import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';
import { faEye, faPenToSquare, faRotateLeft } from '@fortawesome/free-solid-svg-icons';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import {
    AuxiliaryRepositorySnapshotDTO,
    ProgrammingExerciseSnapshotDTO,
    StaticCodeAnalysisCategorySnapshotDTO,
} from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { VersionHistoryViewMode, booleanLabel, valuesDiffer } from 'app/exercise/version-history/shared/version-history.utils';
import { isRevertable } from 'app/exercise/version-history/shared/revert-field.registry';
import { MetadataFieldRowComponent } from 'app/exercise/version-history/shared/metadata-field-row.component';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { ProgrammingExerciseVersionRepositoryCommitDiffComponent } from 'app/programming/manage/version-history/programming-exercise-version-repository-commit-diff.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { findParamInRouteHierarchy } from 'app/shared/util/navigation.utils';
import dayjs from 'dayjs/esm';
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
    kind?: 'text' | 'commit' | 'repository';
    shortCommitHash?: string;
    fullCommitHash?: string;
    commitLink?: (string | number)[];
    repositoryUri?: string;
    repositoryViewLink?: (string | number)[];
    repositoryEditorLink?: (string | number)[];
    repositoryType?: RepositoryType;
    participationId?: number;
    previousCommitHash?: string;
}

@Component({
    selector: 'jhi-programming-exercise-version-programming-metadata',
    templateUrl: './programming-exercise-version-programming-metadata.component.html',
    styleUrls: ['./programming-exercise-version-programming-metadata.component.scss'],
    host: {
        '[style.display]': 'hostDisplay()',
    },
    imports: [
        PanelModule,
        DividerModule,
        ButtonModule,
        TooltipModule,
        TagModule,
        RouterLink,
        CdkCopyToClipboard,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ProgrammingExerciseVersionRepositoryCommitDiffComponent,
        MetadataFieldRowComponent,
    ],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseVersionProgrammingMetadataComponent {
    private readonly markdownService = inject(ArtemisMarkdownService);
    private readonly translateService = inject(TranslateService);
    private readonly route = inject(ActivatedRoute);

    readonly exerciseId = input<number | undefined>();
    readonly programmingData = input<ProgrammingExerciseSnapshotDTO | undefined>();
    readonly previousProgrammingData = input<ProgrammingExerciseSnapshotDTO | undefined>();
    readonly viewMode = input<VersionHistoryViewMode>('full');

    protected readonly faCopy = faCopy;
    protected readonly faEye = faEye;
    protected readonly faPenToSquare = faPenToSquare;
    protected readonly faRotateLeft = faRotateLeft;

    readonly revertField = output<{ fieldId: string; fieldLabel: string; previousRaw: unknown }>();

    readonly isDiffView = computed(() => this.viewMode() === 'changes' && !!this.previousProgrammingData());

    private readonly courseId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'courseId')));
    private readonly routeExerciseId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'exerciseId')));
    private readonly examId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'examId')));
    private readonly exerciseGroupId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'exerciseGroupId')));

    readonly languageFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};
        const previousData = this.previousProgrammingData() ?? {};
        return this.filterFieldsForMode([
            this.toTextField('programmingLanguage', 'artemisApp.programmingExercise.programmingLanguage', data.programmingLanguage, previousData.programmingLanguage),
            this.toTextField('projectType', 'artemisApp.programmingExercise.projectType', data.projectType, previousData.projectType),
            this.toTextField('projectKey', 'artemisApp.programmingExercise.projectKey', data.projectKey, previousData.projectKey),
            this.toTextField('packageName', 'artemisApp.programmingExercise.packageName', data.packageName, previousData.packageName),
            this.toTextField(
                'allowOfflineIde',
                'artemisApp.programmingExercise.allowOfflineIde.title',
                booleanLabel(this.translateService, data.allowOfflineIde),
                booleanLabel(this.translateService, previousData.allowOfflineIde),
            ),
            this.toTextField(
                'allowOnlineEditor',
                'artemisApp.programmingExercise.allowOnlineEditor.title',
                booleanLabel(this.translateService, data.allowOnlineEditor),
                booleanLabel(this.translateService, previousData.allowOnlineEditor),
            ),
            this.toTextField(
                'allowOnlineIde',
                'artemisApp.programmingExercise.allowOnlineIde.title',
                booleanLabel(this.translateService, data.allowOnlineIde),
                booleanLabel(this.translateService, previousData.allowOnlineIde),
            ),
            this.toTextField(
                'staticCodeAnalysisEnabled',
                'artemisApp.programmingExercise.enableStaticCodeAnalysis.title',
                booleanLabel(this.translateService, data.staticCodeAnalysisEnabled),
                booleanLabel(this.translateService, previousData.staticCodeAnalysisEnabled),
            ),
            this.toTextField(
                'maxStaticCodeAnalysisPenalty',
                'artemisApp.programmingExercise.maxStaticCodeAnalysisPenalty.title',
                data.maxStaticCodeAnalysisPenalty,
                previousData.maxStaticCodeAnalysisPenalty,
            ),
            this.toTextField(
                'showTestNamesToStudents',
                'artemisApp.programmingExercise.versionHistory.snapshot.showTestNamesToStudents',
                booleanLabel(this.translateService, data.showTestNamesToStudents),
                booleanLabel(this.translateService, previousData.showTestNamesToStudents),
            ),
            this.toTextField(
                'releaseTestsWithExampleSolution',
                'artemisApp.programmingExercise.versionHistory.snapshot.releaseTestsWithExampleSolution',
                booleanLabel(this.translateService, data.releaseTestsWithExampleSolution),
                booleanLabel(this.translateService, previousData.releaseTestsWithExampleSolution),
            ),
        ]);
    });

    readonly repositoryFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};
        const previousData = this.previousProgrammingData() ?? {};

        return this.filterFieldsForMode([
            this.toRepositoryField(
                'templateRepositoryUri',
                'artemisApp.programmingExercise.templateRepositoryUri',
                data.templateParticipation?.repositoryUri,
                previousData.templateParticipation?.repositoryUri,
                RepositoryType.TEMPLATE,
                data.templateParticipation?.id,
            ),
            this.toRepositoryField(
                'solutionRepositoryUri',
                'artemisApp.programmingExercise.solutionRepositoryUri',
                data.solutionParticipation?.repositoryUri,
                previousData.solutionParticipation?.repositoryUri,
                RepositoryType.SOLUTION,
                data.solutionParticipation?.id,
            ),
            this.toRepositoryField(
                'testRepositoryUri',
                'artemisApp.programmingExercise.testRepositoryUri',
                data.testRepositoryUri,
                previousData.testRepositoryUri,
                RepositoryType.TESTS,
            ),
            this.toTextField(
                'templateBuildPlanId',
                'artemisApp.programmingExercise.versionHistory.snapshot.templateBuildPlanId',
                data.templateParticipation?.buildPlanId,
                previousData.templateParticipation?.buildPlanId,
            ),
            this.toTextField(
                'solutionBuildPlanId',
                'artemisApp.programmingExercise.versionHistory.snapshot.solutionBuildPlanId',
                data.solutionParticipation?.buildPlanId,
                previousData.solutionParticipation?.buildPlanId,
            ),
            this.toCommitField(
                'templateCommitId',
                'artemisApp.programmingExercise.templateCommitId',
                data.templateParticipation?.commitId,
                previousData.templateParticipation?.commitId,
                RepositoryType.TEMPLATE,
                data.templateParticipation?.id ?? previousData.templateParticipation?.id,
            ),
            this.toCommitField(
                'solutionCommitId',
                'artemisApp.programmingExercise.solutionCommitId',
                data.solutionParticipation?.commitId,
                previousData.solutionParticipation?.commitId,
                RepositoryType.SOLUTION,
                data.solutionParticipation?.id ?? previousData.solutionParticipation?.id,
            ),
            this.toCommitField('testsCommitId', 'artemisApp.programmingExercise.testsCommitId', data.testsCommitId, previousData.testsCommitId, RepositoryType.TESTS),
        ]);
    });

    readonly gradingFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};
        const previousData = this.previousProgrammingData() ?? {};
        const submissionPolicyType = data.submissionPolicy?.type;
        const previousSubmissionPolicyType = previousData.submissionPolicy?.type;
        const translatedSubmissionPolicyType = submissionPolicyType
            ? this.translateService.instant(`artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${submissionPolicyType}.title`)
            : undefined;
        const translatedPreviousSubmissionPolicyType = previousSubmissionPolicyType
            ? this.translateService.instant(`artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${previousSubmissionPolicyType}.title`)
            : undefined;

        return this.filterFieldsForMode([
            this.toTextField(
                'submissionPolicyType',
                'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.title',
                translatedSubmissionPolicyType === `artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${submissionPolicyType}.title`
                    ? submissionPolicyType
                    : translatedSubmissionPolicyType,
                translatedPreviousSubmissionPolicyType === `artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${previousSubmissionPolicyType}.title`
                    ? previousSubmissionPolicyType
                    : translatedPreviousSubmissionPolicyType,
            ),
            this.toTextField(
                'submissionPolicyActive',
                'artemisApp.programmingExercise.versionHistory.snapshot.submissionPolicyActive',
                booleanLabel(this.translateService, data.submissionPolicy?.active),
                booleanLabel(this.translateService, previousData.submissionPolicy?.active),
            ),
            this.toTextField(
                'submissionLimit',
                'artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle',
                data.submissionPolicy?.submissionLimit,
                previousData.submissionPolicy?.submissionLimit,
            ),
            this.toTextField(
                'submissionPenalty',
                'artemisApp.programmingExercise.submissionPolicy.submissionPenalty.detailLabel',
                data.submissionPolicy?.exceedingPenalty,
                previousData.submissionPolicy?.exceedingPenalty,
            ),
            this.toTextField(
                'postDueDateHandling',
                'artemisApp.programmingExercise.versionHistory.snapshot.postDueDateHandling',
                data.buildAndTestStudentSubmissionsAfterDueDate ? dayjs(data.buildAndTestStudentSubmissionsAfterDueDate).format('MMM D, YYYY HH:mm') : undefined,
                previousData.buildAndTestStudentSubmissionsAfterDueDate ? dayjs(previousData.buildAndTestStudentSubmissionsAfterDueDate).format('MMM D, YYYY HH:mm') : undefined,
            ),
        ]);
    });

    readonly buildConfigurationFields = computed<MetadataField[]>(() => {
        const buildConfig = this.programmingData()?.buildConfig ?? {};
        const previousBuildConfig = this.previousProgrammingData()?.buildConfig ?? {};

        return this.filterFieldsForMode([
            this.toTextField(
                'sequentialTestRuns',
                'artemisApp.programmingExercise.sequentialTestRuns.title',
                booleanLabel(this.translateService, buildConfig.sequentialTestRuns),
                booleanLabel(this.translateService, previousBuildConfig.sequentialTestRuns),
            ),
            this.toTextField('branch', 'artemisApp.programmingExercise.versionHistory.snapshot.branch', buildConfig.branch, previousBuildConfig.branch),
            this.toTextField('timeoutSeconds', 'artemisApp.programmingExercise.timeout.title', buildConfig.timeoutSeconds, previousBuildConfig.timeoutSeconds),
            this.toTextField(
                'checkoutSolutionRepository',
                'artemisApp.programmingExercise.checkoutSolutionRepository.title',
                booleanLabel(this.translateService, buildConfig.checkoutSolutionRepository),
                booleanLabel(this.translateService, previousBuildConfig.checkoutSolutionRepository),
            ),
            this.toTextField(
                'allowBranching',
                'artemisApp.programmingExercise.allowBranching.title',
                booleanLabel(this.translateService, buildConfig.allowBranching),
                booleanLabel(this.translateService, previousBuildConfig.allowBranching),
            ),
            this.toTextField('branchRegex', 'artemisApp.programmingExercise.branchRegex.title', buildConfig.branchRegex, previousBuildConfig.branchRegex),
            this.toTextField('dockerFlags', 'artemisApp.programmingExercise.versionHistory.snapshot.dockerFlags', buildConfig.dockerFlags, previousBuildConfig.dockerFlags),
            this.toTextField('theiaImage', 'artemisApp.programmingExercise.versionHistory.snapshot.theiaImage', buildConfig.theiaImage, previousBuildConfig.theiaImage),
            this.toTextField(
                'assignmentCheckoutPath',
                'artemisApp.programmingExercise.versionHistory.snapshot.assignmentCheckoutPath',
                buildConfig.assignmentCheckoutPath,
                previousBuildConfig.assignmentCheckoutPath,
            ),
            this.toTextField(
                'solutionCheckoutPath',
                'artemisApp.programmingExercise.versionHistory.snapshot.solutionCheckoutPath',
                buildConfig.solutionCheckoutPath,
                previousBuildConfig.solutionCheckoutPath,
            ),
            this.toTextField(
                'testCheckoutPath',
                'artemisApp.programmingExercise.versionHistory.snapshot.testsCheckoutPath',
                buildConfig.testCheckoutPath,
                previousBuildConfig.testCheckoutPath,
            ),
        ]);
    });

    readonly auxiliaryRepositoriesChanged = computed(() => valuesDiffer(this.programmingData()?.auxiliaryRepositories, this.previousProgrammingData()?.auxiliaryRepositories));
    readonly currentAuxiliaryRepositoryLines = computed(() => this.formatAuxiliaryRepositories(this.programmingData()?.auxiliaryRepositories));
    readonly previousAuxiliaryRepositoryLines = computed(() => this.formatAuxiliaryRepositories(this.previousProgrammingData()?.auxiliaryRepositories));

    readonly staticCodeAnalysisCategoriesChanged = computed(() =>
        valuesDiffer(this.programmingData()?.staticCodeAnalysisCategories, this.previousProgrammingData()?.staticCodeAnalysisCategories),
    );
    readonly currentStaticCodeAnalysisCategoryLines = computed(() => this.formatStaticCodeAnalysisCategories(this.programmingData()?.staticCodeAnalysisCategories));
    readonly previousStaticCodeAnalysisCategoryLines = computed(() => this.formatStaticCodeAnalysisCategories(this.previousProgrammingData()?.staticCodeAnalysisCategories));

    readonly buildPlanConfigurationLabel = computed(() => this.translateLabel('artemisApp.programmingExercise.versionHistory.snapshot.buildPlanConfiguration'));
    readonly formattedBuildPlanConfiguration = computed(() => this.formatJson(this.programmingData()?.buildConfig?.buildPlanConfiguration));
    readonly previousFormattedBuildPlanConfiguration = computed(() => this.formatJson(this.previousProgrammingData()?.buildConfig?.buildPlanConfiguration));
    readonly buildPlanConfigurationChanged = computed(() =>
        valuesDiffer(this.programmingData()?.buildConfig?.buildPlanConfiguration, this.previousProgrammingData()?.buildConfig?.buildPlanConfiguration),
    );

    readonly buildScript = computed<SafeHtml | undefined>(() => {
        const script = this.programmingData()?.buildConfig?.buildScript;
        return this.markdownService.safeHtmlForMarkdown(script ? `\`\`\`bash\n${script}\n\`\`\`` : undefined) ?? undefined;
    });
    readonly currentBuildScript = computed(() => this.programmingData()?.buildConfig?.buildScript);
    readonly previousBuildScript = computed(() => this.previousProgrammingData()?.buildConfig?.buildScript);
    readonly buildScriptChanged = computed(() => valuesDiffer(this.currentBuildScript(), this.previousBuildScript()));

    readonly hasData = computed(() => !!this.programmingData());
    readonly hasVisibleContent = computed(() => {
        if (!this.hasData() || !this.isDiffView()) {
            return true;
        }
        return (
            this.languageFields().length > 0 ||
            this.repositoryFields().length > 0 ||
            this.gradingFields().length > 0 ||
            this.buildConfigurationFields().length > 0 ||
            this.buildPlanConfigurationChanged() ||
            this.buildScriptChanged() ||
            this.auxiliaryRepositoriesChanged() ||
            this.staticCodeAnalysisCategoriesChanged()
        );
    });
    readonly hostDisplay = computed(() => (this.hasVisibleContent() ? 'block' : 'none'));

    private readonly fallbackLabels: Record<string, string> = {
        'artemisApp.programmingExercise.versionHistory.snapshot.branch': 'Branch',
        'artemisApp.programmingExercise.versionHistory.snapshot.postDueDateHandling': 'Post Due Date Handling',
        'artemisApp.programmingExercise.versionHistory.snapshot.dockerFlags': 'Docker Flags',
        'artemisApp.programmingExercise.versionHistory.snapshot.theiaImage': 'Online IDE Image',
        'artemisApp.programmingExercise.versionHistory.snapshot.assignmentCheckoutPath': 'Assignment Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.solutionCheckoutPath': 'Solution Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.testsCheckoutPath': 'Tests Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.buildPlanConfiguration': 'Build Plan Configuration (JSON)',
        'artemisApp.programmingExercise.versionHistory.snapshot.showTestNamesToStudents': 'Show Test Names To Students',
        'artemisApp.programmingExercise.versionHistory.snapshot.releaseTestsWithExampleSolution': 'Release Tests With Example Solution',
        'artemisApp.programmingExercise.versionHistory.snapshot.templateBuildPlanId': 'Template Build Plan ID',
        'artemisApp.programmingExercise.versionHistory.snapshot.solutionBuildPlanId': 'Solution Build Plan ID',
        'artemisApp.programmingExercise.versionHistory.snapshot.submissionPolicyActive': 'Submission Policy Active',
        'artemisApp.programmingExercise.versionHistory.snapshot.auxiliaryRepositories': 'Auxiliary Repositories',
        'artemisApp.programmingExercise.versionHistory.snapshot.staticCodeAnalysisCategories': 'Static Code Analysis Categories',
    };

    private filterFieldsForMode(fields: MetadataField[]): MetadataField[] {
        return this.isDiffView() ? fields.filter((field) => field.changed) : fields;
    }

    private toTextField(labelId: string, labelKey: string, currentRaw?: string | number | boolean, previousRaw?: string | number | boolean): MetadataField {
        return {
            id: labelId,
            label: this.translateLabel(labelKey),
            currentDisplay: typeof currentRaw === 'boolean' ? String(currentRaw) : (currentRaw ?? '-'),
            previousDisplay: typeof previousRaw === 'boolean' ? String(previousRaw) : (previousRaw ?? '-'),
            currentRaw,
            previousRaw,
            changed: valuesDiffer(currentRaw, previousRaw),
            currentEmpty: currentRaw === undefined || currentRaw === '',
            previousEmpty: previousRaw === undefined || previousRaw === '',
            revertable: isRevertable(labelId),
            kind: 'text',
        };
    }

    private toCommitField(
        labelId: string,
        labelKey: string,
        currentValue?: string,
        previousValue?: string,
        repositoryType?: RepositoryType,
        participationId?: number,
    ): MetadataField {
        const fullHash = typeof currentValue === 'string' ? currentValue : undefined;
        return {
            id: labelId,
            label: this.translateLabel(labelKey),
            currentDisplay: currentValue ?? '-',
            previousDisplay: previousValue ?? '-',
            currentRaw: currentValue,
            previousRaw: previousValue,
            changed: valuesDiffer(currentValue, previousValue),
            currentEmpty: currentValue === undefined || currentValue === '',
            previousEmpty: previousValue === undefined || previousValue === '',
            revertable: false,
            kind: 'commit',
            fullCommitHash: fullHash,
            shortCommitHash: fullHash ? (fullHash.length > 8 ? fullHash.slice(0, 8) : fullHash) : '-',
            commitLink: this.buildCommitLink(fullHash, repositoryType),
            repositoryType,
            participationId,
            previousCommitHash: previousValue,
        };
    }

    private toRepositoryField(
        labelId: string,
        labelKey: string,
        currentValue?: string,
        previousValue?: string,
        repositoryType?: RepositoryType,
        repositoryId?: number,
    ): MetadataField {
        const repositoryUri = typeof currentValue === 'string' ? currentValue : undefined;
        return {
            id: labelId,
            label: this.translateLabel(labelKey),
            currentDisplay: currentValue ?? '-',
            previousDisplay: previousValue ?? '-',
            currentRaw: currentValue,
            previousRaw: previousValue,
            changed: valuesDiffer(currentValue, previousValue),
            currentEmpty: currentValue === undefined || currentValue === '',
            previousEmpty: previousValue === undefined || previousValue === '',
            revertable: false,
            kind: 'repository',
            repositoryUri,
            repositoryViewLink: this.buildRepositoryViewLink(repositoryType),
            repositoryEditorLink: this.buildRepositoryEditorLink(repositoryType, repositoryId),
        };
    }

    private translateLabel(key: string): string {
        const translated = this.translateService.instant(key);
        if (translated === key || translated.startsWith('translation-not-found[')) {
            return this.fallbackLabels[key] ?? key;
        }
        return translated;
    }

    private buildCommitLink(commitHash?: string, repositoryType?: RepositoryType): (string | number)[] | undefined {
        if (!commitHash || !repositoryType) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId() ?? this.routeExerciseId();
        if (!courseId || !exerciseId) {
            return undefined;
        }

        const examId = this.examId();
        const exerciseGroupId = this.exerciseGroupId();
        const baseRoute =
            examId && exerciseGroupId
                ? ['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, 'programming-exercises', exerciseId, 'repository', repositoryType]
                : ['/course-management', courseId, 'programming-exercises', exerciseId, 'repository', repositoryType];

        return [...baseRoute, 'commit-history', commitHash];
    }

    private buildRepositoryViewLink(repositoryType?: RepositoryType): (string | number)[] | undefined {
        if (!repositoryType) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId() ?? this.routeExerciseId();
        if (!courseId || !exerciseId) {
            return undefined;
        }

        const examId = this.examId();
        const exerciseGroupId = this.exerciseGroupId();
        return examId && exerciseGroupId
            ? ['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, 'programming-exercises', exerciseId, 'repository', repositoryType]
            : ['/course-management', courseId, 'programming-exercises', exerciseId, 'repository', repositoryType];
    }

    private buildRepositoryEditorLink(repositoryType?: RepositoryType, repositoryId?: number): (string | number)[] | undefined {
        if (!repositoryType || repositoryId === undefined) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId() ?? this.routeExerciseId();
        if (!courseId || !exerciseId) {
            return undefined;
        }

        const examId = this.examId();
        const exerciseGroupId = this.exerciseGroupId();
        return examId && exerciseGroupId
            ? [
                  '/course-management',
                  courseId,
                  'exams',
                  examId,
                  'exercise-groups',
                  exerciseGroupId,
                  'programming-exercises',
                  exerciseId,
                  'code-editor',
                  repositoryType,
                  repositoryId,
              ]
            : ['/course-management', courseId, 'programming-exercises', exerciseId, 'code-editor', repositoryType, repositoryId];
    }

    private toNumber(value?: string): number | undefined {
        if (!value?.trim()) {
            return undefined;
        }
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }

    private formatJson(raw?: string): string | undefined {
        if (!raw?.trim()) {
            return undefined;
        }
        try {
            return JSON.stringify(JSON.parse(raw), undefined, 2);
        } catch {
            return raw;
        }
    }

    private formatAuxiliaryRepositories(repositories?: AuxiliaryRepositorySnapshotDTO[]): string[] {
        return (repositories ?? [])
            .map((repository) => {
                const parts = [repository.name, repository.checkoutDirectory, repository.commitId].filter(Boolean);
                return parts.length > 0 ? parts.join(' | ') : (repository.repositoryUri ?? '-');
            })
            .sort((left, right) => left.localeCompare(right));
    }

    private formatStaticCodeAnalysisCategories(categories?: StaticCodeAnalysisCategorySnapshotDTO[]): string[] {
        return (categories ?? [])
            .map((category) => `${category.name ?? '-'}: penalty=${category.penalty ?? '-'}, max=${category.maxPenalty ?? '-'}, state=${category.state ?? '-'}`)
            .sort((left, right) => left.localeCompare(right));
    }
}
