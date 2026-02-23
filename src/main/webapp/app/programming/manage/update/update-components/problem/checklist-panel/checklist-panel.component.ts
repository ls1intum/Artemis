import { Component, DestroyRef, WritableSignal, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe, NgClass } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { IconDefinition } from '@fortawesome/fontawesome-common-types';
import { Tag } from 'primeng/tag';
import { ButtonDirective } from 'primeng/button';
import { Badge } from 'primeng/badge';
import {
    faArrowDown,
    faArrowUp,
    faBolt,
    faCheckCircle,
    faChevronDown,
    faChevronRight,
    faEquals,
    faExclamationTriangle,
    faInfoCircle,
    faLink,
    faMagic,
    faQuestion,
    faSpinner,
    faSync,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { ChecklistActionRequest } from 'app/openapi/model/checklistActionRequest';
import { DifficultyAssessment } from 'app/openapi/model/difficultyAssessment';
import { QualityIssue } from 'app/openapi/model/qualityIssue';
import { InferredCompetency } from 'app/openapi/model/inferredCompetency';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import {
    Competency,
    CompetencyExerciseLink,
    CompetencyTaxonomy,
    CourseCompetency,
    HIGH_COMPETENCY_LINK_WEIGHT,
    LOW_COMPETENCY_LINK_WEIGHT,
    MEDIUM_COMPETENCY_LINK_WEIGHT,
} from 'app/atlas/shared/entities/competency.model';
import { EMPTY, Observable, forkJoin, of } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

/**
 * Type-safe section identifier used for stale tracking and section-level re-analysis.
 */
export type ChecklistSectionType = 'competencies' | 'difficulty' | 'quality';

/**
 * Maps client-side section names to their corresponding field in ChecklistAnalysisResponse.
 */
const SECTION_TO_FIELD: Record<ChecklistSectionType, keyof ChecklistAnalysisResponse> = {
    quality: 'qualityIssues',
    competencies: 'inferredCompetencies',
    difficulty: 'difficultyAssessment',
};

@Component({
    selector: 'jhi-checklist-panel',
    templateUrl: './checklist-panel.component.html',
    styleUrls: ['./checklist-panel.component.scss'],
    standalone: true,
    imports: [NgClass, DecimalPipe, TranslateModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective, Tag, ButtonDirective, Badge],
})
export class ChecklistPanelComponent {
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private alertService = inject(AlertService);
    private competencyService = inject(CompetencyService);
    private destroyRef = inject(DestroyRef);
    private translateService = inject(TranslateService);

    exercise = input.required<ProgrammingExercise>();
    courseId = input.required<number>();
    problemStatement = input.required<string>();

    problemStatementChange = output<string>();
    problemStatementDiffRequest = output<string>();
    competencyLinksChange = output<CompetencyExerciseLink[]>();
    difficultyChange = output<string>();

    analysisResult = signal<ChecklistAnalysisResponse | undefined>(undefined);
    isLoading = signal<boolean>(false);
    isApplyingAction = signal<boolean>(false);
    actionLoadingKey = signal<string | undefined>(undefined);

    // Competency linking state
    courseCompetencies = signal<CourseCompetency[]>([]);
    linkedCompetencyTitles = signal<Set<string>>(new Set());
    createdCompetencyTitles = signal<Set<string>>(new Set());
    isSyncingCompetencies = signal<boolean>(false);

    // Expanded state for competency evidence
    expandedCompetencies = signal<Set<number>>(new Set());

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
    }

    /**
     * Locally computed task and test counts by parsing [task] markers (no AI needed).
     */
    localTaskTestCounts = computed(() => {
        return this.countTasksAndTests(this.effectiveProblemStatement());
    });

    sectionExpanded: Record<ChecklistSectionType, ReturnType<typeof signal<boolean>>> = {
        competencies: signal(true),
        difficulty: signal(true),
        quality: signal(true),
    };

    toggleSection(section: ChecklistSectionType) {
        this.sectionExpanded[section].update((v) => !v);
    }

    readonly difficultyLevels = ['EASY', 'MEDIUM', 'HARD'] as const;

    // Icons
    readonly faCheckCircle = faCheckCircle;
    readonly faInfoCircle = faInfoCircle;
    readonly faSpinner = faSpinner;
    readonly faMagic = faMagic;
    readonly faChevronDown = faChevronDown;
    readonly faChevronRight = faChevronRight;
    readonly faArrowUp = faArrowUp;
    readonly faArrowDown = faArrowDown;
    readonly faEquals = faEquals;
    readonly faQuestion = faQuestion;
    readonly faWrench = faWrench;
    readonly faBolt = faBolt;
    readonly faSync = faSync;
    readonly faLink = faLink;
    readonly faExclamationTriangle = faExclamationTriangle;

    analyze() {
        const cId = this.courseId();
        if (!cId || this.isApplyingAction() || this.sectionLoading().size > 0) {
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
                    // New analysis results invalidate previous linking state
                    this.linkedCompetencyTitles.set(new Set());
                    this.createdCompetencyTitles.set(new Set());
                    this.competencyLinksChange.emit([]);
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.isLoading.set(false);
                },
            });
    }

    toggleCompetencyExpand(rank: number) {
        this.expandedCompetencies.update((s) => {
            const n = new Set(s);
            if (n.has(rank)) {
                n.delete(rank);
            } else {
                n.add(rank);
            }
            return n;
        });
    }

    isCompetencyExpanded(rank: number): boolean {
        return this.expandedCompetencies().has(rank);
    }

    private static readonly SEVERITY_TAG_MAP: Record<string, 'danger' | 'warn' | 'info'> = { HIGH: 'danger', MEDIUM: 'warn', LOW: 'info' };
    private static readonly CATEGORY_COLOR_MAP: Record<string, string> = { CLARITY: 'category-clarity', COHERENCE: 'category-coherence', COMPLETENESS: 'category-completeness' };
    private static readonly DIFFICULTY_SEVERITY_MAP: Record<string, 'success' | 'warn' | 'danger'> = { EASY: 'success', MEDIUM: 'warn', HARD: 'danger' };
    private static readonly DELTA_CLASS_MAP: Record<string, string> = { HIGHER: 'text-danger', LOWER: 'text-warning', MATCH: 'text-success' };
    private static readonly DELTA_LABEL_KEYS: Record<string, string> = {
        HIGHER: 'artemisApp.programmingExercise.instructorChecklist.difficulty.harderThanDeclared',
        LOWER: 'artemisApp.programmingExercise.instructorChecklist.difficulty.easierThanDeclared',
        MATCH: 'artemisApp.programmingExercise.instructorChecklist.difficulty.matchesDeclared',
    };

    getSeverityTagSeverity(severity: string | undefined): 'danger' | 'warn' | 'info' {
        return ChecklistPanelComponent.SEVERITY_TAG_MAP[severity?.toUpperCase() ?? ''] ?? 'info';
    }

    getCategoryColorClass(category: string | undefined): string {
        return ChecklistPanelComponent.CATEGORY_COLOR_MAP[category?.toUpperCase() ?? ''] ?? 'category-default';
    }

    getDifficultySeverity(level: string | undefined): 'success' | 'warn' | 'danger' {
        return ChecklistPanelComponent.DIFFICULTY_SEVERITY_MAP[level?.toUpperCase() ?? ''] ?? 'warn';
    }

    getDeltaIcon(delta: string | undefined): IconDefinition {
        return { HIGHER: this.faArrowUp, LOWER: this.faArrowDown, MATCH: this.faEquals }[delta ?? ''] ?? this.faQuestion;
    }

    getDeltaLabel(delta: string | undefined): string {
        const key = ChecklistPanelComponent.DELTA_LABEL_KEYS[delta ?? ''] ?? 'artemisApp.programmingExercise.instructorChecklist.difficulty.unknown';
        return this.translateService.instant(key);
    }

    getDeltaClass(delta: string | undefined): string {
        return ChecklistPanelComponent.DELTA_CLASS_MAP[delta ?? ''] ?? 'text-secondary';
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
        const scores: Record<string, number> = { CLARITY: 1.0, COHERENCE: 1.0, COMPLETENESS: 1.0 };

        for (const issue of issues) {
            const cat = issue.category?.toUpperCase() || '';
            if (cat in scores) {
                const sev = issue.severity?.toUpperCase();
                const penalty = sev === 'HIGH' ? 0.3 : sev === 'MEDIUM' ? 0.2 : 0.1;
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
        if (!cId || this.isApplyingAction() || this.sectionLoading().size > 0) return;

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
        if (!cId || this.isLoading() || this.sectionLoading().has(section) || this.isApplyingAction()) return;

        this.addToSet(this.sectionLoading, section);
        const ex = this.exercise();
        const request = {
            problemStatementMarkdown: this.effectiveProblemStatement(),
            declaredDifficulty: ex.difficulty,
            language: ex.programmingLanguage,
            exerciseId: ex.id,
        };

        this.hyperionApiService
            .analyzeChecklistSection(cId, section.toUpperCase() as 'COMPETENCIES' | 'DIFFICULTY' | 'QUALITY', request)
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
                    if (section === 'competencies') {
                        this.linkedCompetencyTitles.set(new Set());
                        this.createdCompetencyTitles.set(new Set());
                        this.competencyLinksChange.emit([]);
                    }
                    this.deleteFromSet(this.staleSections, section);
                    this.deleteFromSet(this.sectionLoading, section);
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.deleteFromSet(this.sectionLoading, section);
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
            ['competencies', 'difficulty'],
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
            ['competencies', 'difficulty'],
            () => this.updateAnalysisOptimistically((r) => Object.assign({}, r, { qualityIssues: [] })),
        );
    }

    adaptDifficulty(targetDifficulty: DifficultyAssessment.SuggestedEnum) {
        const current = this.exercise()?.difficulty || 'unknown';
        const assessment = this.analysisResult()?.difficultyAssessment;
        const reasoning = assessment?.reasoning || '';

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.AdaptDifficulty,
                problemStatementMarkdown: this.effectiveProblemStatement(),
                context: {
                    targetDifficulty,
                    currentDifficulty: current,
                    reasoning,
                    taskCount: String(assessment?.taskCount ?? 'unknown'),
                    testCount: String(assessment?.testCount ?? 'unknown'),
                },
            },
            `adapt-${targetDifficulty}`,
            ['quality', 'competencies'],
            () => {
                this.updateAnalysisOptimistically((r) =>
                    Object.assign({}, r, {
                        difficultyAssessment: Object.assign({}, r.difficultyAssessment, {
                            suggested: targetDifficulty,
                            delta: DifficultyAssessment.DeltaEnum.Match,
                            reasoning: this.translateService.instant('artemisApp.programmingExercise.instructorChecklist.actions.adaptedReasoning', {
                                difficulty: targetDifficulty,
                            }),
                        }),
                    }),
                );
                this.difficultyChange.emit(targetDifficulty);
            },
        );
    }

    isActionLoading(key: string): boolean {
        return this.actionLoadingKey() === key;
    }

    /**
     * Maps an inferred taxonomy level string to CompetencyTaxonomy enum
     */
    private mapTaxonomy(level: string | undefined): CompetencyTaxonomy | undefined {
        if (!level) return undefined;
        const upper = level.toUpperCase();
        if (Object.values(CompetencyTaxonomy).includes(upper as CompetencyTaxonomy)) {
            return upper as CompetencyTaxonomy;
        }
        return undefined;
    }

    /**
     * Loads course competencies, using the cached value if already populated.
     * @param forceReload if true, bypasses the cache and fetches fresh data from the server
     */
    private loadCourseCompetencies(forceReload = false): Observable<CourseCompetency[]> {
        if (!forceReload) {
            const cached = this.courseCompetencies();
            if (cached.length > 0) {
                return of(cached);
            }
        }
        return this.competencyService.getAllForCourse(this.courseId()).pipe(
            map((res) => {
                const competencies = res.body ?? [];
                this.courseCompetencies.set(competencies);
                return competencies;
            }),
        );
    }

    /**
     * Unified method: links inferred competencies to matching course competencies,
     * then creates new course competencies for any that couldn't be matched and links those too.
     *
     * Matching strategy (per inferred competency):
     *   1. Use AI-provided matchedCourseCompetencyId if available and still exists
     *   2. If unmatched, create a new course competency and link it
     */
    applyCompetencies(): void {
        if (this.isSyncingCompetencies()) return;

        this.isSyncingCompetencies.set(true);
        const inferred = this.analysisResult()?.inferredCompetencies ?? [];
        const exercise = this.exercise();
        const courseId = this.courseId();

        this.loadCourseCompetencies(true)
            .pipe(
                switchMap(() => {
                    const { allLinks, newlyLinked, toCreate, toCreateInferred, linkedIds } = this.reconcileCompetencies(inferred, exercise);
                    if (toCreate.length === 0) {
                        this.finishApply(allLinks, newlyLinked, new Set());
                        return EMPTY;
                    }
                    const create$ = toCreate.map((comp) =>
                        this.competencyService.create(comp, courseId).pipe(
                            map((response) => ({ success: true as const, response })),
                            catchError(() => of({ success: false as const, response: null })),
                        ),
                    );
                    return forkJoin(create$).pipe(
                        tap((results) => {
                            const newlyCreated = new Set<string>();
                            let failureCount = 0;
                            for (let i = 0; i < results.length; i++) {
                                const result = results[i];
                                if (!result.success) {
                                    failureCount++;
                                    continue;
                                }
                                const created = result.response?.body;
                                if (created?.id && !linkedIds.has(created.id)) {
                                    allLinks.push(new CompetencyExerciseLink(created, exercise, this.computeRelevanceWeight(toCreateInferred[i])));
                                    linkedIds.add(created.id);
                                    newlyCreated.add((created.title ?? '').toLowerCase());
                                    this.courseCompetencies.update((current) => [...current, created]);
                                }
                            }
                            this.finishApply(allLinks, newlyLinked, newlyCreated);
                            if (failureCount > 0 && failureCount < results.length) {
                                this.alertService.warning('artemisApp.programmingExercise.instructorChecklist.competencies.syncError');
                            } else if (failureCount === results.length) {
                                this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.syncError');
                            }
                        }),
                        catchError(() => {
                            this.finishApply(allLinks, newlyLinked, new Set());
                            this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.syncError');
                            return EMPTY;
                        }),
                    );
                }),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe({
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.syncError');
                    this.isSyncingCompetencies.set(false);
                },
            });
    }

    /**
     * Helper to finalize applyCompetencies: emits links, updates tracking sets, shows alerts.
     */
    private finishApply(allLinks: CompetencyExerciseLink[], linked: Set<string>, created: Set<string>): void {
        const totalNew = linked.size + created.size;
        if (totalNew > 0) {
            this.competencyLinksChange.emit(allLinks);
            this.linkedCompetencyTitles.set(new Set([...this.linkedCompetencyTitles(), ...linked, ...created]));
            if (created.size > 0) {
                this.createdCompetencyTitles.set(new Set([...this.createdCompetencyTitles(), ...created]));
            }
            this.alertService.success('artemisApp.programmingExercise.instructorChecklist.competencies.syncSuccess');
        } else {
            this.alertService.warning('artemisApp.programmingExercise.instructorChecklist.competencies.allLinked');
        }
        this.isSyncingCompetencies.set(false);
    }

    /**
     * Reconciles inferred competencies against existing course competencies.
     * Links matching competencies and queues unmatched ones for creation.
     */
    private reconcileCompetencies(inferred: InferredCompetency[], exercise: ProgrammingExercise) {
        const courseComps = this.courseCompetencies();
        const competencyById = new Map(courseComps.filter((c) => c.id != null).map((c) => [c.id!, c]));
        const existingCompIds = new Set(courseComps.filter((c) => c.id != null).map((c) => c.id!));

        const existingTitles = new Set(courseComps.map((c) => c.title?.toLowerCase().trim()).filter((t): t is string => !!t));
        const reconciledCreated = new Set([...this.createdCompetencyTitles()].filter((t) => existingTitles.has(t)));
        this.createdCompetencyTitles.set(reconciledCreated);

        const existingLinks = exercise?.competencyLinks ?? [];
        const linkedIds = new Set(existingLinks.map((link) => link.competency?.id).filter((id): id is number => id != null));
        const allLinks: CompetencyExerciseLink[] = [...existingLinks];
        const newlyLinked = new Set<string>();
        const toCreate: Competency[] = [];
        const toCreateInferred: InferredCompetency[] = [];

        for (const comp of inferred) {
            const title = comp.competencyTitle?.trim();
            if (!title) continue;

            let courseComp: CourseCompetency | undefined;
            const matchId = comp.matchedCourseCompetencyId;
            if (matchId != null && matchId > 0 && existingCompIds.has(matchId)) {
                courseComp = competencyById.get(matchId);
            }

            if (courseComp?.id && !linkedIds.has(courseComp.id)) {
                allLinks.push(new CompetencyExerciseLink(courseComp, exercise, this.computeRelevanceWeight(comp)));
                linkedIds.add(courseComp.id);
                newlyLinked.add(title.toLowerCase());
            } else if (!courseComp && !reconciledCreated.has(title.toLowerCase())) {
                const newComp = new Competency();
                newComp.title = title;
                newComp.description = comp.whyThisMatches ?? `Inferred from problem statement analysis. Evidence: ${(comp.evidence ?? []).join('; ')}`;
                newComp.taxonomy = this.mapTaxonomy(comp.taxonomyLevel);
                toCreate.push(newComp);
                toCreateInferred.push(comp);
            }
        }

        return { allLinks, newlyLinked, toCreate, toCreateInferred, linkedIds };
    }

    private competencyTitleIn(comp: InferredCompetency, titleSet: Set<string>): boolean {
        return titleSet.has((comp.competencyTitle ?? '').toLowerCase().trim());
    }

    /**
     * Checks if an inferred competency has been linked to the exercise
     */
    isCompetencyLinked(comp: InferredCompetency): boolean {
        return this.competencyTitleIn(comp, this.linkedCompetencyTitles());
    }

    /**
     * Computes the relevance weight for a competency link based on AI-inferred data.
     * Primary competencies get HIGH weight, high-confidence ones get MEDIUM, others get LOW.
     */
    private computeRelevanceWeight(comp: InferredCompetency): number {
        if (comp.isLikelyPrimary) {
            return HIGH_COMPETENCY_LINK_WEIGHT;
        }
        if (comp.confidence != null && comp.confidence >= 0.7) {
            return MEDIUM_COMPETENCY_LINK_WEIGHT;
        }
        return LOW_COMPETENCY_LINK_WEIGHT;
    }

    private addToSet<T>(sig: WritableSignal<Set<T>>, item: T) {
        sig.update((s) => {
            const n = new Set(s);
            n.add(item);
            return n;
        });
    }

    private deleteFromSet<T>(sig: WritableSignal<Set<T>>, item: T) {
        sig.update((s) => {
            const n = new Set(s);
            n.delete(item);
            return n;
        });
    }

    /** Counts tasks and unique test cases by parsing [task] markers in the problem statement. */
    private countTasksAndTests(problemStatement: string): { tasks: number; tests: number } {
        const matches = [...problemStatement.matchAll(taskRegex)];
        let taskCount = 0;
        const testNames = new Set<string>();

        for (const match of matches) {
            taskCount++;
            const testList = match[2]?.trim();
            if (testList) {
                testList
                    .split(',')
                    .map((t) => t.trim())
                    .filter(Boolean)
                    .forEach((t) => testNames.add(t));
            }
        }

        return { tasks: taskCount, tests: testNames.size };
    }
}
