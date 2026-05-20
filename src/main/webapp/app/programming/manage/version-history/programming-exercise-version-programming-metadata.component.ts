import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SafeHtml } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';
import { faEye, faPenToSquare } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseSnapshotDTO } from 'app/exercise/synchronization/metadata/exercise-metadata-snapshot.dto';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { findParamInRouteHierarchy } from 'app/shared/util/navigation.utils';
import { booleanLabel } from 'app/exercise/version-history/shared/version-history.utils';
import dayjs from 'dayjs/esm';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ButtonModule } from 'primeng/button';
import { DividerModule } from 'primeng/divider';
import { PanelModule } from 'primeng/panel';
import { TooltipModule } from 'primeng/tooltip';

/**
 * View-model for a single row in the metadata panel.
 *
 * All display values are pre-computed in `computed()` signals so that the
 * template never invokes methods during change detection.
 *
 * The `kind` discriminator controls the template variant used:
 * - `'commit'`     — shows a shortened hash, copy button, and link to commit history
 * - `'repository'` — shows copy-URI, view, and editor action buttons
 * - `'text'` / absent — plain text value
 */
interface MetadataField {
    /** Pre-translated display label. */
    translatedLabel: string;
    /** Raw value (used for copy-to-clipboard, tooltips, etc.). */
    value?: string | number;
    /** Display-ready value: the raw value or a dash placeholder when missing. */
    displayValue: string | number;
    /** Whether the value is absent (undefined or empty string). */
    isEmpty: boolean;
    kind?: 'text' | 'commit' | 'repository';
    /** For commit fields: the first 8 characters of the hash. */
    shortCommitHash?: string;
    /** For commit fields: the full hash string. */
    fullCommitHash?: string;
    /** For commit fields: router-link to the commit history page. */
    commitLink?: (string | number)[];
    /** For repository fields: the URI string. */
    repositoryUri?: string;
    /** For repository fields: router-link to the read-only repository view. */
    repositoryViewLink?: (string | number)[];
    /** For repository fields: router-link to the code editor. */
    repositoryEditorLink?: (string | number)[];
}

/**
 * Renders the programming-exercise-specific portion of a version snapshot:
 * language settings, repository URIs and commit hashes (with action buttons),
 * grading/submission policy, build configuration, and the build script.
 *
 * Commit and repository fields include contextual links that adapt to both
 * the course-management and exam route hierarchies.
 */
@Component({
    selector: 'jhi-programming-exercise-version-programming-metadata',
    templateUrl: './programming-exercise-version-programming-metadata.component.html',
    styleUrls: ['./programming-exercise-version-programming-metadata.component.scss'],
    imports: [PanelModule, DividerModule, ButtonModule, TooltipModule, RouterLink, CdkCopyToClipboard, FaIconComponent, TranslateDirective, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProgrammingExerciseVersionProgrammingMetadataComponent {
    private readonly markdownService = inject(ArtemisMarkdownService);
    private readonly translateService = inject(TranslateService);
    private readonly route = inject(ActivatedRoute);

    /** Programming-specific snapshot data. When `undefined`, a "no data" note is shown. */
    readonly programmingData = input<ProgrammingExerciseSnapshotDTO>();

    protected readonly faCopy = faCopy;
    protected readonly faEye = faEye;
    protected readonly faPenToSquare = faPenToSquare;

    // Route params are static for the lifetime of this component; computed() is used for consistency with the signal-based API
    private readonly courseId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'courseId')));
    private readonly exerciseId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'exerciseId')));
    private readonly examId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'examId')));
    private readonly exerciseGroupId = computed(() => this.toNumber(findParamInRouteHierarchy(this.route, 'exerciseGroupId')));

    /** Language, project type, IDE settings, and static code analysis fields. */
    readonly languageFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};

        return [
            this.toTextField('artemisApp.programmingExercise.programmingLanguage', data.programmingLanguage),
            this.toTextField('artemisApp.programmingExercise.projectType', data.projectType),
            this.toTextField('artemisApp.programmingExercise.projectKey', data.projectKey),
            this.toTextField('artemisApp.programmingExercise.packageName', data.packageName),
            this.toTextField('artemisApp.programmingExercise.allowOfflineIde.title', booleanLabel(this.translateService, data.allowOfflineIde)),
            this.toTextField('artemisApp.programmingExercise.allowOnlineEditor.title', booleanLabel(this.translateService, data.allowOnlineEditor)),
            this.toTextField('artemisApp.programmingExercise.allowOnlineIde.title', booleanLabel(this.translateService, data.allowOnlineIde)),
            this.toTextField('artemisApp.programmingExercise.enableStaticCodeAnalysis.title', booleanLabel(this.translateService, data.staticCodeAnalysisEnabled)),
            this.toTextField('artemisApp.programmingExercise.maxStaticCodeAnalysisPenalty.title', data.maxStaticCodeAnalysisPenalty),
        ];
    });

    /** Repository URIs and commit hashes for template, solution, and test repos. */
    readonly repositoryFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};

        return [
            this.toRepositoryField(
                'artemisApp.programmingExercise.templateRepositoryUri',
                data.templateParticipation?.repositoryUri,
                RepositoryType.TEMPLATE,
                data.templateParticipation?.id,
            ),
            this.toRepositoryField(
                'artemisApp.programmingExercise.solutionRepositoryUri',
                data.solutionParticipation?.repositoryUri,
                RepositoryType.SOLUTION,
                data.solutionParticipation?.id,
            ),
            this.toRepositoryField('artemisApp.programmingExercise.testRepositoryUri', data.testRepositoryUri, RepositoryType.TESTS),
            this.toCommitField('artemisApp.programmingExercise.templateCommitId', data.templateParticipation?.commitId, RepositoryType.TEMPLATE),
            this.toCommitField('artemisApp.programmingExercise.solutionCommitId', data.solutionParticipation?.commitId, RepositoryType.SOLUTION),
            this.toCommitField('artemisApp.programmingExercise.testsCommitId', data.testsCommitId, RepositoryType.TESTS),
        ];
    });

    /** Submission policy and post-due-date handling fields. */
    readonly gradingFields = computed<MetadataField[]>(() => {
        const data = this.programmingData() ?? {};

        const submissionPolicyType = data.submissionPolicy?.type;
        const translatedSubmissionPolicyType = submissionPolicyType
            ? this.translateService.instant(`artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${submissionPolicyType}.title`)
            : undefined;

        return [
            this.toTextField(
                'artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.title',
                translatedSubmissionPolicyType === `artemisApp.programmingExercise.submissionPolicy.submissionPolicyType.${submissionPolicyType}.title`
                    ? submissionPolicyType
                    : translatedSubmissionPolicyType,
            ),
            this.toTextField('artemisApp.programmingExercise.submissionPolicy.submissionLimitTitle', data.submissionPolicy?.submissionLimit),
            this.toTextField('artemisApp.programmingExercise.submissionPolicy.submissionPenalty.detailLabel', data.submissionPolicy?.exceedingPenalty),
            this.toTextField(
                'artemisApp.programmingExercise.versionHistory.snapshot.postDueDateHandling',
                data.buildAndTestStudentSubmissionsAfterDueDate ? dayjs(data.buildAndTestStudentSubmissionsAfterDueDate).format('MMM D, YYYY HH:mm') : undefined,
            ),
        ];
    });

    /** Build configuration fields (sequential runs, branch, timeout, checkout paths, etc.). */
    readonly buildConfigurationFields = computed<MetadataField[]>(() => {
        const buildConfig = this.programmingData()?.buildConfig ?? {};

        return [
            this.toTextField('artemisApp.programmingExercise.sequentialTestRuns.title', booleanLabel(this.translateService, buildConfig.sequentialTestRuns)),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.branch', buildConfig.branch),
            this.toTextField('artemisApp.programmingExercise.timeout.title', buildConfig.timeoutSeconds),
            this.toTextField('artemisApp.programmingExercise.checkoutSolutionRepository.title', booleanLabel(this.translateService, buildConfig.checkoutSolutionRepository)),
            this.toTextField('artemisApp.programmingExercise.allowBranching.title', booleanLabel(this.translateService, buildConfig.allowBranching)),
            this.toTextField('artemisApp.programmingExercise.branchRegex.title', buildConfig.branchRegex),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.dockerFlags', buildConfig.dockerFlags),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.theiaImage', buildConfig.theiaImage),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.assignmentCheckoutPath', buildConfig.assignmentCheckoutPath),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.solutionCheckoutPath', buildConfig.solutionCheckoutPath),
            this.toTextField('artemisApp.programmingExercise.versionHistory.snapshot.testsCheckoutPath', buildConfig.testCheckoutPath),
        ];
    });

    /** Pre-translated label for the build plan configuration summary. */
    readonly buildPlanConfigurationLabel = computed(() => this.translateLabel('artemisApp.programmingExercise.versionHistory.snapshot.buildPlanConfiguration'));

    /** Pretty-printed JSON of the build plan configuration, or `undefined` if absent. */
    readonly formattedBuildPlanConfiguration = computed(() => this.formatJson(this.programmingData()?.buildConfig?.buildPlanConfiguration));

    /** Build script rendered as syntax-highlighted HTML inside a bash code fence. */
    readonly buildScript = computed<SafeHtml | undefined>(() => {
        const script = this.programmingData()?.buildConfig?.buildScript;
        return this.markdownService.safeHtmlForMarkdown(script ? `\`\`\`bash\n${script}\n\`\`\`` : undefined) ?? undefined;
    });

    /** Whether any programming-specific data is available. */
    readonly hasData = computed(() => !!this.programmingData());

    private readonly fallbackLabels: Record<string, string> = {
        'artemisApp.programmingExercise.versionHistory.snapshot.branch': 'Branch',
        'artemisApp.programmingExercise.versionHistory.snapshot.postDueDateHandling': 'Post Due Date Handling',
        'artemisApp.programmingExercise.versionHistory.snapshot.dockerFlags': 'Docker Flags',
        'artemisApp.programmingExercise.versionHistory.snapshot.theiaImage': 'Online IDE Image',
        'artemisApp.programmingExercise.versionHistory.snapshot.assignmentCheckoutPath': 'Assignment Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.solutionCheckoutPath': 'Solution Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.testsCheckoutPath': 'Tests Checkout Path',
        'artemisApp.programmingExercise.versionHistory.snapshot.buildPlanConfiguration': 'Build Plan Configuration (JSON)',
    };

    // ── View-model factory methods (called only inside computed() signals) ──

    /** Creates a plain text metadata field with pre-computed display values. */
    private toTextField(label: string, value?: string | number): MetadataField {
        return {
            translatedLabel: this.translateLabel(label),
            value,
            displayValue: value ?? '-',
            isEmpty: value === undefined || value === '',
            kind: 'text',
        };
    }

    /** Creates a commit metadata field with pre-computed hash, short hash, and link. */
    private toCommitField(label: string, value?: string, repositoryType?: RepositoryType): MetadataField {
        const fullHash = typeof value === 'string' ? value : undefined;
        const shortHash = fullHash ? (fullHash.length > 8 ? fullHash.slice(0, 8) : fullHash) : '-';

        return {
            translatedLabel: this.translateLabel(label),
            value,
            displayValue: value ?? '-',
            isEmpty: value === undefined || value === '',
            kind: 'commit',
            fullCommitHash: fullHash,
            shortCommitHash: shortHash,
            commitLink: this.buildCommitLink(fullHash, repositoryType),
        };
    }

    /** Creates a repository metadata field with pre-computed URI, view link, and editor link. */
    private toRepositoryField(label: string, value?: string, repositoryType?: RepositoryType, repositoryId?: number): MetadataField {
        const uri = typeof value === 'string' ? value : undefined;

        return {
            translatedLabel: this.translateLabel(label),
            value,
            displayValue: value ?? '-',
            isEmpty: value === undefined || value === '',
            kind: 'repository',
            repositoryUri: uri,
            repositoryViewLink: this.buildRepositoryViewLink(repositoryType),
            repositoryEditorLink: this.buildRepositoryEditorLink(repositoryType, repositoryId),
        };
    }

    /**
     * Translates an i18n key, falling back to a hardcoded label or the raw key
     * when no translation is available (e.g. for snapshot-specific labels that
     * may not exist in all locales yet).
     */
    private translateLabel(key: string): string {
        const translated = this.translateService.instant(key);
        if (translated === key || translated.startsWith('translation-not-found[')) {
            return this.fallbackLabels[key] ?? key;
        }
        return translated;
    }

    /** Builds a router-link path to the commit history page, adapting to course or exam routes. */
    private buildCommitLink(commitHash?: string, repositoryType?: RepositoryType): (string | number)[] | undefined {
        if (!commitHash || !repositoryType) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
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

    /** Builds a router-link path to the read-only repository view, adapting to course or exam routes. */
    private buildRepositoryViewLink(repositoryType?: RepositoryType): (string | number)[] | undefined {
        if (!repositoryType) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
        if (!courseId || !exerciseId) {
            return undefined;
        }

        const examId = this.examId();
        const exerciseGroupId = this.exerciseGroupId();
        return examId && exerciseGroupId
            ? ['/course-management', courseId, 'exams', examId, 'exercise-groups', exerciseGroupId, 'programming-exercises', exerciseId, 'repository', repositoryType]
            : ['/course-management', courseId, 'programming-exercises', exerciseId, 'repository', repositoryType];
    }

    /** Builds a router-link path to the code editor for a repository, adapting to course or exam routes. */
    private buildRepositoryEditorLink(repositoryType?: RepositoryType, repositoryId?: number): (string | number)[] | undefined {
        if (!repositoryType || repositoryId === undefined) {
            return undefined;
        }

        const courseId = this.courseId();
        const exerciseId = this.exerciseId();
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

    /** Parses a string to a finite number, or returns `undefined` for missing/invalid input. */
    private toNumber(value?: string): number | undefined {
        if (!value) {
            return undefined;
        }
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : undefined;
    }

    /** Pretty-prints a JSON string, returning the raw value on parse failure. */
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
}
