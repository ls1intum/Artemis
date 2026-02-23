import { Component, DestroyRef, WritableSignal, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe, NgClass } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { Tag } from 'primeng/tag';
import { ButtonDirective } from 'primeng/button';
import { Badge } from 'primeng/badge';
import {
    faBolt,
    faCheckCircle,
    faChevronDown,
    faChevronRight,
    faExclamationTriangle,
    faInfoCircle,
    faMagic,
    faSpinner,
    faSync,
    faTrashCan,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { ChecklistActionRequest } from 'app/openapi/model/checklistActionRequest';
import { QualityIssue } from 'app/openapi/model/qualityIssue';
import { AlertService } from 'app/shared/service/alert.service';
import { Checkbox } from 'primeng/checkbox';
import { FormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Type-safe section identifier used for stale tracking and section-level re-analysis.
 */
export type ChecklistSectionType = 'quality';

/**
 * Maps client-side section names to their corresponding field in ChecklistAnalysisResponse.
 */
const SECTION_TO_FIELD: Record<ChecklistSectionType, keyof ChecklistAnalysisResponse> = {
    quality: 'qualityIssues',
};

/** Default quality score before penalties are applied. */
const DEFAULT_QUALITY_SCORE = 1.0;
/** Penalty subtracted per HIGH-severity quality issue. */
const PENALTY_HIGH = 0.3;
/** Penalty subtracted per MEDIUM-severity quality issue. */
const PENALTY_MEDIUM = 0.2;
/** Penalty subtracted per LOW-severity quality issue. */
const PENALTY_LOW = 0.1;

@Component({
    selector: 'jhi-checklist-panel',
    templateUrl: './checklist-panel.component.html',
    styleUrls: ['./checklist-panel.component.scss'],
    standalone: true,
    imports: [NgClass, DecimalPipe, TranslateModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective, Tag, ButtonDirective, Badge, Checkbox, FormsModule],
})
export class ChecklistPanelComponent {
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private alertService = inject(AlertService);
    private destroyRef = inject(DestroyRef);

    exercise = input.required<ProgrammingExercise>();
    courseId = input.required<number>();
    problemStatement = input.required<string>();

    problemStatementChange = output<string>();
    problemStatementDiffRequest = output<string>();

    analysisResult = signal<ChecklistAnalysisResponse | undefined>(undefined);
    isLoading = signal<boolean>(false);
    isApplyingAction = signal<boolean>(false);
    actionLoadingKey = signal<string | undefined>(undefined);

    // Quality issue multi-select state
    selectedIssueIndices = signal<Set<number>>(new Set());

    // Stale tracking: sections that may be outdated after an AI action modified the problem statement
    staleSections = signal<Set<ChecklistSectionType>>(new Set());
    sectionLoading = signal<Set<ChecklistSectionType>>(new Set());

    // Track the latest problem statement (may be updated by AI actions before the input signal refreshes)
    private latestProblemStatement = signal<string | undefined>(undefined);

    /**
     * Effective problem statement: the latest AI-modified version, or the input signal.
     */
    private readonly effectiveProblemStatement = computed(() => this.latestProblemStatement() ?? this.problemStatement());

    constructor() {
        /**
         * Clear latestProblemStatement once the input signal catches up to the
         * AI-emitted value, ensuring subsequent manual edits are respected.
         */
        effect(() => {
            const inputPS = this.problemStatement();
            const latest = this.latestProblemStatement();
            if (latest !== undefined && inputPS === latest) {
                untracked(() => this.latestProblemStatement.set(undefined));
            }
        });

        /**
         * When the problem statement input changes (external edit), mark all
         * checklist sections as stale so the user knows results may be outdated.
         */
        let previousPS: string | undefined;
        effect(() => {
            const currentPS = this.problemStatement();
            if (previousPS !== undefined && currentPS !== previousPS) {
                untracked(() => {
                    if (this.analysisResult()) {
                        this.staleSections.set(new Set<ChecklistSectionType>(['quality']));
                    }
                });
            }
            previousPS = currentPS;
        });
    }

    sectionExpanded: Record<ChecklistSectionType, ReturnType<typeof signal<boolean>>> = {
        quality: signal(true),
    };

    toggleSection(section: ChecklistSectionType) {
        this.sectionExpanded[section].update((v) => !v);
    }

    // Icons
    readonly faCheckCircle = faCheckCircle;
    readonly faInfoCircle = faInfoCircle;
    readonly faSpinner = faSpinner;
    readonly faMagic = faMagic;
    readonly faChevronDown = faChevronDown;
    readonly faChevronRight = faChevronRight;
    readonly faWrench = faWrench;
    readonly faBolt = faBolt;
    readonly faSync = faSync;
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faTrashCan = faTrashCan;

    analyze() {
        const cId = this.courseId();
        if (cId == null || this.isApplyingAction() || this.sectionLoading().size > 0) {
            return;
        }

        const ex = this.exercise();
        this.isLoading.set(true);
        const request = {
            problemStatementMarkdown: this.effectiveProblemStatement(),
            declaredDifficulty: ex.difficulty,
            language: ex.programmingLanguage,
            exerciseId: ex.id,
        };

        this.hyperionApiService
            .analyzeChecklist(cId, request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (res: ChecklistAnalysisResponse) => {
                    this.analysisResult.set(res);
                    this.isLoading.set(false);
                    this.staleSections.set(new Set());
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.isLoading.set(false);
                },
            });
    }

    private static readonly SEVERITY_TAG_MAP: Record<string, 'danger' | 'warn' | 'info'> = { HIGH: 'danger', MEDIUM: 'warn', LOW: 'info' };
    private static readonly CATEGORY_COLOR_MAP: Record<string, string> = { CLARITY: 'category-clarity', COHERENCE: 'category-coherence', COMPLETENESS: 'category-completeness' };

    getSeverityTagSeverity(severity: string | undefined): 'danger' | 'warn' | 'info' {
        return ChecklistPanelComponent.SEVERITY_TAG_MAP[severity?.toUpperCase() ?? ''] ?? 'info';
    }

    getCategoryColorClass(category: string | undefined): string {
        return ChecklistPanelComponent.CATEGORY_COLOR_MAP[category?.toUpperCase() ?? ''] ?? 'category-default';
    }

    // Quality radar chart for Clarity, Coherence, Completeness
    private readonly QUALITY_RADAR_RADIUS = 80;
    private readonly QUALITY_CATEGORIES = ['CLARITY', 'COHERENCE', 'COMPLETENESS'] as const;
    private readonly QUALITY_COLORS: Record<string, string> = {
        CLARITY: 'var(--primary)',
        COHERENCE: 'var(--info)',
        COMPLETENESS: 'var(--success)',
    };

    qualityScores = computed(() => {
        const issues = this.analysisResult()?.qualityIssues || [];
        const scores: Record<string, number> = { CLARITY: DEFAULT_QUALITY_SCORE, COHERENCE: DEFAULT_QUALITY_SCORE, COMPLETENESS: DEFAULT_QUALITY_SCORE };

        for (const issue of issues) {
            const cat = issue.category?.toUpperCase() || '';
            if (cat in scores) {
                const sev = issue.severity?.toUpperCase();
                const penalty = sev === 'HIGH' ? PENALTY_HIGH : sev === 'MEDIUM' ? PENALTY_MEDIUM : PENALTY_LOW;
                scores[cat] = Math.max(0, scores[cat] - penalty);
            }
        }
        return scores;
    });

    qualityRadarPoints = computed(() => {
        const scores = this.qualityScores();
        const count = this.QUALITY_CATEGORIES.length;
        const angleStep = (2 * Math.PI) / count;
        const startAngle = -Math.PI / 2;

        return this.QUALITY_CATEGORIES.map((cat, i) => {
            const angle = startAngle + i * angleStep;
            const cos = Math.cos(angle);
            const sin = Math.sin(angle);
            const value = scores[cat];
            const dataRadius = value * this.QUALITY_RADAR_RADIUS;

            return {
                label: cat.charAt(0) + cat.slice(1).toLowerCase(),
                value,
                color: this.QUALITY_COLORS[cat],
                axisX: cos * this.QUALITY_RADAR_RADIUS,
                axisY: sin * this.QUALITY_RADAR_RADIUS,
                dataX: cos * dataRadius,
                dataY: sin * dataRadius,
                labelX: cos * (this.QUALITY_RADAR_RADIUS + 25),
                labelY: sin * (this.QUALITY_RADAR_RADIUS + 25),
            };
        });
    });

    getQualityGridPoints(scale: number): string {
        const count = this.QUALITY_CATEGORIES.length;
        const angleStep = (2 * Math.PI) / count;
        const startAngle = -Math.PI / 2;

        return Array.from({ length: count }, (_, i) => {
            const angle = startAngle + i * angleStep;
            const x = Math.cos(angle) * this.QUALITY_RADAR_RADIUS * scale;
            const y = Math.sin(angle) * this.QUALITY_RADAR_RADIUS * scale;
            return `${x},${y}`;
        }).join(' ');
    }

    /**
     * Pre-computed grid polygon strings for the 4 scale levels.
     */
    readonly qualityGridStrings = [0.25, 0.5, 0.75, 1.0].map((level) => this.getQualityGridPoints(level));

    qualityPolygonPoints = computed(() => {
        return this.qualityRadarPoints()
            .map((p) => `${p.dataX},${p.dataY}`)
            .join(' ');
    });

    private applyAction(request: ChecklistActionRequest, loadingKey: string, staleMark: ChecklistSectionType[], onApplied?: () => void) {
        const cId = this.courseId();
        if (cId == null || this.isApplyingAction() || this.sectionLoading().size > 0) return;

        this.isApplyingAction.set(true);
        this.actionLoadingKey.set(loadingKey);

        this.hyperionApiService
            .applyChecklistAction(cId, request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (res) => {
                    if (res.applied) {
                        const newProblemStatement = res.updatedProblemStatement ?? this.latestProblemStatement() ?? this.problemStatement();
                        this.latestProblemStatement.set(newProblemStatement);
                        this.problemStatementDiffRequest.emit(newProblemStatement);
                        this.alertService.success('artemisApp.programmingExercise.instructorChecklist.actions.success');
                        onApplied?.();
                        // Mark other sections as stale instead of re-analyzing
                        this.markSectionsStale(staleMark);
                    } else {
                        this.alertService.warning('artemisApp.programmingExercise.instructorChecklist.actions.noChanges');
                    }
                    this.isApplyingAction.set(false);
                    this.actionLoadingKey.set(undefined);
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.isApplyingAction.set(false);
                    this.actionLoadingKey.set(undefined);
                },
            });
    }

    private markSectionsStale(sections: ChecklistSectionType[]) {
        this.staleSections.update((current) => {
            const n = new Set(current);
            sections.forEach((s) => n.add(s));
            return n;
        });
    }

    isSectionStale(section: ChecklistSectionType): boolean {
        return this.staleSections().has(section);
    }

    isSectionLoading(section: ChecklistSectionType): boolean {
        return this.sectionLoading().has(section);
    }

    /**
     * Re-analyzes a single section via the section-specific endpoint.
     */
    reanalyzeSection(section: ChecklistSectionType) {
        const cId = this.courseId();
        if (cId == null || this.isLoading() || this.sectionLoading().has(section) || this.isApplyingAction()) return;

        this.updateSet(this.sectionLoading, section, 'add');
        const ex = this.exercise();
        const request = {
            problemStatementMarkdown: this.effectiveProblemStatement(),
            declaredDifficulty: ex.difficulty,
            language: ex.programmingLanguage,
            exerciseId: ex.id,
        };

        this.hyperionApiService
            .analyzeChecklistSection(cId, section.toUpperCase() as 'QUALITY', request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (res: ChecklistAnalysisResponse) => {
                    const current = this.analysisResult();
                    if (current) {
                        const updated = Object.assign({}, current, { [SECTION_TO_FIELD[section]]: res[SECTION_TO_FIELD[section]] });
                        this.analysisResult.set(updated);
                    } else {
                        this.analysisResult.set(res);
                    }
                    this.updateSet(this.staleSections, section, 'delete');
                    this.updateSet(this.sectionLoading, section, 'delete');
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.updateSet(this.sectionLoading, section, 'delete');
                },
            });
    }

    private updateAnalysisOptimistically(updater: (current: ChecklistAnalysisResponse) => ChecklistAnalysisResponse) {
        this.analysisResult.update((current) => (current ? updater(current) : current));
    }

    fixQualityIssue(issue: QualityIssue, index: number) {
        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixQualityIssue,
                problemStatementMarkdown: this.effectiveProblemStatement(),
                context: {
                    issueDescription: issue.description || '',
                    suggestedFix: issue.suggestedFix || '',
                    category: issue.category || '',
                },
            },
            `fix-issue-${index}`,
            [],
            () => this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: (r.qualityIssues ?? []).filter((_, i) => i !== index) })),
        );
    }

    fixAllQualityIssues() {
        const issues = this.analysisResult()?.qualityIssues || [];
        const allIssues = issues.map((i, idx) => `${idx + 1}. [${i.category}/${i.severity}] ${i.description} (Fix: ${i.suggestedFix || 'N/A'})`).join('\n');

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                problemStatementMarkdown: this.effectiveProblemStatement(),
                context: { allIssues },
            },
            'fix-all',
            [],
            () => {
                this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: [] }));
                this.selectedIssueIndices.set(new Set());
            },
        );
    }

    /**
     * Discards a single quality issue from the list without AI action.
     * The quality radar graph is NOT updated (scores remain unchanged until re-analysis).
     */
    discardQualityIssue(index: number) {
        this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: (r.qualityIssues ?? []).filter((_, i) => i !== index) }));
        // Reindex selected indices after removal
        this.selectedIssueIndices.update((current) => {
            const updated = new Set<number>();
            for (const idx of current) {
                if (idx < index) updated.add(idx);
                else if (idx > index) updated.add(idx - 1);
                // idx === index is removed (discarded)
            }
            return updated;
        });
        this.alertService.success('artemisApp.programmingExercise.instructorChecklist.quality.discarded');
    }

    /**
     * Toggles selection state of a quality issue at the given index.
     */
    toggleIssueSelection(index: number) {
        this.selectedIssueIndices.update((current) => {
            const updated = new Set(current);
            if (updated.has(index)) {
                updated.delete(index);
            } else {
                updated.add(index);
            }
            return updated;
        });
    }

    /**
     * Returns whether a quality issue at the given index is selected.
     */
    isIssueSelected(index: number): boolean {
        return this.selectedIssueIndices().has(index);
    }

    /**
     * Selects all quality issues.
     */
    selectAllIssues() {
        const count = this.analysisResult()?.qualityIssues?.length ?? 0;
        this.selectedIssueIndices.set(new Set(Array.from({ length: count }, (_, i) => i)));
    }

    /**
     * Deselects all quality issues.
     */
    deselectAllIssues() {
        this.selectedIssueIndices.set(new Set());
    }

    /**
     * Whether all quality issues are currently selected.
     */
    allIssuesSelected(): boolean {
        const count = this.analysisResult()?.qualityIssues?.length ?? 0;
        return count > 0 && this.selectedIssueIndices().size === count;
    }

    /**
     * Fixes all currently selected quality issues via AI in a single batch action.
     */
    fixSelectedIssues() {
        const issues = this.analysisResult()?.qualityIssues ?? [];
        const selected = this.selectedIssueIndices();
        if (selected.size === 0) return;

        const selectedIssues = [...selected]
            .sort((a, b) => a - b)
            .map((i) => issues[i])
            .filter(Boolean);
        const allIssues = selectedIssues.map((i, idx) => `${idx + 1}. [${i.category}/${i.severity}] ${i.description} (Fix: ${i.suggestedFix || 'N/A'})`).join('\n');

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                problemStatementMarkdown: this.effectiveProblemStatement(),
                context: { allIssues },
            },
            'fix-selected',
            [],
            () => {
                this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: (r.qualityIssues ?? []).filter((_, i) => !selected.has(i)) }));
                this.selectedIssueIndices.set(new Set());
            },
        );
    }

    /**
     * Discards all currently selected quality issues from the list without AI action.
     * The quality radar graph is NOT updated (scores remain unchanged until re-analysis).
     */
    discardSelectedIssues() {
        const selected = this.selectedIssueIndices();
        if (selected.size === 0) return;

        this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: (r.qualityIssues ?? []).filter((_, i) => !selected.has(i)) }));
        this.selectedIssueIndices.set(new Set());
        this.alertService.success('artemisApp.programmingExercise.instructorChecklist.quality.discardedMultiple');
    }

    isActionLoading(key: string): boolean {
        return this.actionLoadingKey() === key;
    }

    private updateSet<T>(sig: WritableSignal<Set<T>>, item: T, op: 'add' | 'delete') {
        sig.update((s) => {
            const n = new Set(s);
            n[op](item);
            return n;
        });
    }
}
