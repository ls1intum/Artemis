import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
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
    faPlus,
    faQuestion,
    faSpinner,
    faSync,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { ChecklistActionRequest } from 'app/openapi/model/checklistActionRequest';
import { QualityIssue } from 'app/openapi/model/qualityIssue';
import { InferredCompetency } from 'app/openapi/model/inferredCompetency';
import { AlertService } from 'app/shared/service/alert.service';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { Competency, CompetencyExerciseLink, CompetencyTaxonomy, CourseCompetency, MEDIUM_COMPETENCY_LINK_WEIGHT } from 'app/atlas/shared/entities/competency.model';
import { Observable, lastValueFrom, of } from 'rxjs';
import { map } from 'rxjs/operators';
import { taskRegex } from 'app/programming/shared/instructions-render/extensions/programming-exercise-task.extension';
import { titleSimilarity } from './title-similarity.utils';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-checklist-panel',
    templateUrl: './checklist-panel.component.html',
    styleUrls: ['./checklist-panel.component.scss'],
    standalone: true,
    imports: [CommonModule, TranslateModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective, Tag, ButtonDirective, Badge],
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
    isLinkingCompetencies = signal<boolean>(false);
    isCreatingCompetencies = signal<boolean>(false);

    // Expanded state for competency evidence
    expandedCompetencies = signal<Set<number>>(new Set());

    // Stale tracking: sections that may be outdated after an AI action modified the problem statement
    staleSections = signal<Set<string>>(new Set());
    sectionLoading = signal<string | undefined>(undefined);

    // Track the latest problem statement (may be updated by AI actions before the input signal refreshes)
    private latestProblemStatement = signal<string | undefined>(undefined);

    /**
     * Locally computed task and test counts from the problem statement.
     * Parses [task][TaskName](test1,test2) markers without relying on AI, saving tokens.
     */
    localTaskTestCounts = computed(() => {
        const ps = this.latestProblemStatement() ?? this.problemStatement();
        return this.countTasksAndTests(ps);
    });

    sectionCompetencies = signal(true);
    sectionDifficulty = signal(true);
    sectionQuality = signal(true);

    toggleSection(section: 'competencies' | 'difficulty' | 'quality') {
        switch (section) {
            case 'competencies':
                this.sectionCompetencies.update((v) => !v);
                break;
            case 'difficulty':
                this.sectionDifficulty.update((v) => !v);
                break;
            case 'quality':
                this.sectionQuality.update((v) => !v);
                break;
        }
    }

    readonly difficultyLevels = ['EASY', 'MEDIUM', 'HARD'] as const;

    // Icons
    faCheckCircle = faCheckCircle;
    faInfoCircle = faInfoCircle;
    faSpinner = faSpinner;
    faMagic = faMagic;
    faChevronDown = faChevronDown;
    faChevronRight = faChevronRight;
    faArrowUp = faArrowUp;
    faArrowDown = faArrowDown;
    faEquals = faEquals;
    faQuestion = faQuestion;
    faWrench = faWrench;
    faBolt = faBolt;
    faSync = faSync;
    faLink = faLink;
    faPlus = faPlus;
    faExclamationTriangle = faExclamationTriangle;

    analyze() {
        const cId = this.courseId();
        if (!cId) {
            return;
        }

        const ex = this.exercise();
        this.isLoading.set(true);
        const request = {
            problemStatementMarkdown: this.problemStatement(),
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

    toggleCompetencyExpand(rank: number) {
        const current = this.expandedCompetencies();
        const newSet = new Set(current);
        if (newSet.has(rank)) {
            newSet.delete(rank);
        } else {
            newSet.add(rank);
        }
        this.expandedCompetencies.set(newSet);
    }

    isCompetencyExpanded(rank: number): boolean {
        return this.expandedCompetencies().has(rank);
    }

    getConfidenceClass(confidence: number | undefined): string {
        if (confidence === undefined) return 'bg-secondary';
        if (confidence >= 0.8) return 'bg-success';
        if (confidence >= 0.5) return 'bg-warning';
        return 'bg-danger';
    }

    getSeverityClass(severity: string | undefined): string {
        switch (severity?.toUpperCase()) {
            case 'HIGH':
                return 'text-danger';
            case 'MEDIUM':
                return 'text-warning';
            case 'LOW':
            default:
                return 'text-info';
        }
    }

    getSeverityTagSeverity(severity: string | undefined): 'danger' | 'warn' | 'info' {
        switch (severity?.toUpperCase()) {
            case 'HIGH':
                return 'danger';
            case 'MEDIUM':
                return 'warn';
            case 'LOW':
            default:
                return 'info';
        }
    }

    getCategoryColorClass(category: string | undefined): string {
        switch (category?.toUpperCase()) {
            case 'CLARITY':
                return 'category-clarity';
            case 'COHERENCE':
                return 'category-coherence';
            case 'COMPLETENESS':
                return 'category-completeness';
            default:
                return 'category-default';
        }
    }

    getDifficultySeverity(level: string | undefined): 'success' | 'warn' | 'danger' {
        switch (level?.toUpperCase()) {
            case 'EASY':
                return 'success';
            case 'MEDIUM':
                return 'warn';
            case 'HARD':
                return 'danger';
            default:
                return 'warn';
        }
    }

    getDeltaIcon(delta: string | undefined) {
        switch (delta) {
            case 'HIGHER':
                return this.faArrowUp;
            case 'LOWER':
                return this.faArrowDown;
            case 'MATCH':
                return this.faEquals;
            default:
                return this.faQuestion;
        }
    }

    getDeltaLabel(delta: string | undefined): string {
        switch (delta) {
            case 'HIGHER':
                return this.translateService.instant('artemisApp.programmingExercise.instructorChecklist.difficulty.harderThanDeclared');
            case 'LOWER':
                return this.translateService.instant('artemisApp.programmingExercise.instructorChecklist.difficulty.easierThanDeclared');
            case 'MATCH':
                return this.translateService.instant('artemisApp.programmingExercise.instructorChecklist.difficulty.matchesDeclared');
            default:
                return this.translateService.instant('artemisApp.programmingExercise.instructorChecklist.difficulty.unknown');
        }
    }

    getDeltaClass(delta: string | undefined): string {
        switch (delta) {
            case 'HIGHER':
                return 'text-danger';
            case 'LOWER':
                return 'text-warning';
            case 'MATCH':
                return 'text-success';
            default:
                return 'text-secondary';
        }
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
                const penalty = issue.severity?.toUpperCase() === 'HIGH' ? 0.3 : issue.severity?.toUpperCase() === 'MEDIUM' ? 0.2 : 0.1;
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

    qualityPolygonPoints = computed(() => {
        return this.qualityRadarPoints()
            .map((p) => `${p.dataX},${p.dataY}`)
            .join(' ');
    });

    // ===== AI Action Methods =====

    private applyAction(request: ChecklistActionRequest, loadingKey: string, staleMark: string[], onApplied?: () => void) {
        const cId = this.courseId();
        if (!cId || this.isApplyingAction()) return;

        this.isApplyingAction.set(true);
        this.actionLoadingKey.set(loadingKey);

        this.hyperionApiService
            .applyChecklistAction(cId, request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (res) => {
                    if (res.applied) {
                        const newProblemStatement = res.updatedProblemStatement ?? '';
                        this.latestProblemStatement.set(newProblemStatement);
                        this.problemStatementChange.emit(newProblemStatement);
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

    private markSectionsStale(sections: string[]) {
        const current = new Set(this.staleSections());
        for (const s of sections) {
            current.add(s);
        }
        this.staleSections.set(current);
    }

    isSectionStale(section: string): boolean {
        return this.staleSections().has(section);
    }

    isSectionLoading(section: string): boolean {
        return this.sectionLoading() === section;
    }

    /**
     * Re-analyzes a single section by calling the section-specific endpoint,
     * which only runs the requested analysis (saving 2/3 of LLM calls).
     */
    reanalyzeSection(section: string) {
        const cId = this.courseId();
        if (!cId || this.sectionLoading()) return;

        this.sectionLoading.set(section);
        const ex = this.exercise();
        const request = {
            problemStatementMarkdown: this.latestProblemStatement() ?? this.problemStatement(),
            declaredDifficulty: ex.difficulty,
            language: ex.programmingLanguage,
            exerciseId: ex.id,
        };

        const sectionParam = section.toUpperCase() as 'COMPETENCIES' | 'DIFFICULTY' | 'QUALITY';

        this.hyperionApiService
            .analyzeChecklistSection(cId, sectionParam, request)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (res: ChecklistAnalysisResponse) => {
                    const current = this.analysisResult();
                    if (current) {
                        const updated = { ...current };
                        if (section === 'quality') {
                            updated.qualityIssues = res.qualityIssues;
                        } else if (section === 'competencies') {
                            updated.inferredCompetencies = res.inferredCompetencies;
                        } else if (section === 'difficulty') {
                            updated.difficultyAssessment = res.difficultyAssessment;
                        }
                        this.analysisResult.set(updated);
                    } else {
                        this.analysisResult.set(res);
                    }
                    // Remove stale mark for this section
                    const stale = new Set(this.staleSections());
                    stale.delete(section);
                    this.staleSections.set(stale);
                    this.sectionLoading.set(undefined);
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.actions.error');
                    this.sectionLoading.set(undefined);
                },
            });
    }

    private updateAnalysisOptimistically(updater: (current: ChecklistAnalysisResponse) => ChecklistAnalysisResponse) {
        const current = this.analysisResult();
        if (current) {
            this.analysisResult.set(updater(current));
        }
    }

    fixQualityIssue(issue: QualityIssue, index: number) {
        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixQualityIssue,
                problemStatementMarkdown: this.latestProblemStatement() ?? this.problemStatement(),
                context: {
                    issueDescription: issue.description || '',
                    suggestedFix: issue.suggestedFix || '',
                    category: issue.category || '',
                },
            },
            `fix-issue-${index}`,
            ['competencies', 'difficulty'],
            () => this.updateAnalysisOptimistically((r) => ({ ...r, qualityIssues: (r.qualityIssues ?? []).filter((_, i) => i !== index) })),
        );
    }

    fixAllQualityIssues() {
        const issues = this.analysisResult()?.qualityIssues || [];
        const allIssues = issues.map((i, idx) => `${idx + 1}. [${i.category}/${i.severity}] ${i.description} (Fix: ${i.suggestedFix || 'N/A'})`).join('\n');

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                problemStatementMarkdown: this.latestProblemStatement() ?? this.problemStatement(),
                context: { allIssues },
            },
            'fix-all',
            ['competencies', 'difficulty'],
            () => this.updateAnalysisOptimistically((r) => ({ ...r, qualityIssues: [] })),
        );
    }

    adaptDifficulty(targetDifficulty: string) {
        const current = this.exercise()?.difficulty || 'unknown';
        const assessment = this.analysisResult()?.difficultyAssessment;
        const reasoning = assessment?.reasoning || '';

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.AdaptDifficulty,
                problemStatementMarkdown: this.latestProblemStatement() ?? this.problemStatement(),
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
                this.updateAnalysisOptimistically((r) => ({
                    ...r,
                    difficultyAssessment: {
                        ...r.difficultyAssessment,
                        suggested: targetDifficulty,
                        delta: 'MATCH',
                        reasoning: `Adapted to ${targetDifficulty}. Re-analyze for an updated assessment.`,
                    },
                }));
                this.difficultyChange.emit(targetDifficulty);
            },
        );
    }

    isActionLoading(key: string): boolean {
        return this.actionLoadingKey() === key;
    }

    // ===== Competency Linking Methods =====

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
     * Finds a matching course competency using fuzzy word-overlap matching.
     * Handles cases where the AI-inferred title differs slightly from the course competency
     * title (e.g., "Algorithm Analysis" vs "Algorithm Design and Analysis").
     */
    private findMatchingCompetency(inferred: InferredCompetency): CourseCompetency | undefined {
        const inferredTitle = inferred.competencyTitle?.toLowerCase().trim();
        if (!inferredTitle) return undefined;

        let bestMatch: CourseCompetency | undefined;
        let bestScore = 0;

        for (const cc of this.courseCompetencies()) {
            const courseTitle = cc.title?.toLowerCase().trim();
            if (!courseTitle) continue;

            const score = titleSimilarity(inferredTitle, courseTitle);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = cc;
            }
        }

        // Require at least 50% word overlap to consider it a match
        return bestScore >= 0.5 ? bestMatch : undefined;
    }

    /**
     * Loads course competencies, using the cached value if already populated.
     */
    private loadCourseCompetencies(): Observable<CourseCompetency[]> {
        const cached = this.courseCompetencies();
        if (cached.length > 0) {
            return of(cached);
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
     * Link inferred competencies to matching existing course competencies.
     * Matches by title similarity and adds them to the exercise's competencyLinks.
     */
    linkMatchingCompetencies(): void {
        if (this.isLinkingCompetencies()) return;

        this.isLinkingCompetencies.set(true);
        const inferred = this.analysisResult()?.inferredCompetencies ?? [];
        const exercise = this.exercise();

        // Ensure we have course competencies loaded (uses cache if available)
        this.loadCourseCompetencies()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    const existingLinks = exercise?.competencyLinks ?? [];
                    const existingIds = new Set(existingLinks.map((link) => link.competency?.id).filter(Boolean));
                    const newLinks: CompetencyExerciseLink[] = [...existingLinks];
                    const newlyLinked = new Set<string>();

                    for (const comp of inferred) {
                        const match = this.findMatchingCompetency(comp);
                        if (match?.id && !existingIds.has(match.id)) {
                            newLinks.push(new CompetencyExerciseLink(match, exercise, MEDIUM_COMPETENCY_LINK_WEIGHT));
                            existingIds.add(match.id);
                            newlyLinked.add(comp.competencyTitle ?? '');
                        }
                    }

                    if (newlyLinked.size > 0) {
                        this.competencyLinksChange.emit(newLinks);
                        this.linkedCompetencyTitles.set(new Set([...this.linkedCompetencyTitles(), ...newlyLinked]));
                        this.alertService.success('artemisApp.programmingExercise.instructorChecklist.competencies.linkSuccess');
                    } else {
                        this.alertService.warning('artemisApp.programmingExercise.instructorChecklist.competencies.noMatches');
                    }
                    this.isLinkingCompetencies.set(false);
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.linkError');
                    this.isLinkingCompetencies.set(false);
                },
            });
    }

    /**
     * Creates new competencies from inferred competencies that don't match any existing
     * course competency, then links them to the exercise.
     */
    createAndLinkCompetencies(): void {
        if (this.isCreatingCompetencies()) return;

        this.isCreatingCompetencies.set(true);
        const inferred = this.analysisResult()?.inferredCompetencies ?? [];
        const exercise = this.exercise();
        const courseId = this.courseId();

        // First ensure course competencies are loaded (uses cache if available)
        this.loadCourseCompetencies()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: () => {
                    const existingLinks = exercise?.competencyLinks ?? [];
                    const existingIds = new Set(existingLinks.map((link) => link.competency?.id).filter(Boolean));
                    const existingTitles = new Set(
                        this.courseCompetencies()
                            .map((c) => c.title?.toLowerCase().trim())
                            .filter(Boolean),
                    );

                    // Find inferred competencies that don't match existing ones
                    const toCreate: Competency[] = [];
                    for (const comp of inferred) {
                        const title = comp.competencyTitle?.trim();
                        if (!title) continue;
                        if (existingTitles.has(title.toLowerCase())) continue;
                        if (this.createdCompetencyTitles().has(title)) continue;

                        const newComp = new Competency();
                        newComp.title = title;
                        newComp.description = comp.whyThisMatches ?? `Inferred from problem statement analysis. Evidence: ${(comp.evidence ?? []).join('; ')}`;
                        newComp.taxonomy = this.mapTaxonomy(comp.taxonomyLevel);
                        toCreate.push(newComp);
                    }

                    if (toCreate.length === 0) {
                        this.alertService.warning('artemisApp.programmingExercise.instructorChecklist.competencies.allExist');
                        this.isCreatingCompetencies.set(false);
                        return;
                    }

                    // Create all new competencies in parallel
                    const promises = toCreate.map((comp) => lastValueFrom(this.competencyService.create(comp, courseId)));
                    Promise.all(promises)
                        .then((responses) => {
                            const newLinks: CompetencyExerciseLink[] = [...existingLinks];
                            const newlyCreated = new Set<string>();

                            for (const response of responses) {
                                const created = response.body;
                                if (created?.id && !existingIds.has(created.id)) {
                                    newLinks.push(new CompetencyExerciseLink(created, exercise, MEDIUM_COMPETENCY_LINK_WEIGHT));
                                    existingIds.add(created.id);
                                    newlyCreated.add(created.title ?? '');
                                    // Update local cache
                                    this.courseCompetencies.update((current) => [...current, created]);
                                }
                            }

                            if (newlyCreated.size > 0) {
                                this.competencyLinksChange.emit(newLinks);
                                this.createdCompetencyTitles.set(new Set([...this.createdCompetencyTitles(), ...newlyCreated]));
                                this.alertService.success('artemisApp.programmingExercise.instructorChecklist.competencies.createSuccess');
                            }
                            this.isCreatingCompetencies.set(false);
                        })
                        .catch(() => {
                            this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.createError');
                            this.isCreatingCompetencies.set(false);
                        });
                },
                error: () => {
                    this.alertService.error('artemisApp.programmingExercise.instructorChecklist.competencies.createError');
                    this.isCreatingCompetencies.set(false);
                },
            });
    }

    /**
     * Checks if an inferred competency has been linked to the exercise
     */
    isCompetencyLinked(comp: InferredCompetency): boolean {
        return this.linkedCompetencyTitles().has(comp.competencyTitle ?? '');
    }

    /**
     * Checks if an inferred competency was created as a new course competency
     */
    isCompetencyCreated(comp: InferredCompetency): boolean {
        return this.createdCompetencyTitles().has(comp.competencyTitle ?? '');
    }

    /**
     * Gets the count of inferred competencies that match existing course competencies
     */
    readonly matchingCount = computed(() => {
        const inferred = this.analysisResult()?.inferredCompetencies ?? [];
        return inferred.filter((c) => this.findMatchingCompetency(c) !== undefined).length;
    });

    /**
     * Gets the count of inferred competencies that don't match any existing course competency
     */
    readonly unmatchedCount = computed(() => {
        const inferred = this.analysisResult()?.inferredCompetencies ?? [];
        return inferred.filter((c) => this.findMatchingCompetency(c) === undefined).length;
    });

    /**
     * Counts tasks and unique test cases from the problem statement by parsing [task] markers.
     * This avoids relying on AI for counting, saving tokens.
     */
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
