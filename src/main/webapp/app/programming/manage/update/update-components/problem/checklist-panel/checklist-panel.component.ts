import { Component, computed, inject, input, output, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
    faArrowDown,
    faArrowUp,
    faBolt,
    faCheckCircle,
    faChevronDown,
    faChevronRight,
    faEquals,
    faInfoCircle,
    faMagic,
    faMinus,
    faPlus,
    faQuestion,
    faSpinner,
    faWrench,
} from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { ChecklistActionRequest } from 'app/openapi/model/checklistActionRequest';
import { QualityIssue } from 'app/openapi/model/qualityIssue';
import { AlertService } from 'app/shared/service/alert.service';

import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-checklist-panel',
    templateUrl: './checklist-panel.component.html',
    styleUrls: ['./checklist-panel.component.scss'],
    standalone: true,
    imports: [CommonModule, TranslateModule, FontAwesomeModule, ArtemisTranslatePipe, TranslateDirective],
})
export class ChecklistPanelComponent {
    private hyperionApiService = inject(HyperionProblemStatementApiService);
    private alertService = inject(AlertService);

    exercise = input.required<ProgrammingExercise>();
    problemStatement = input.required<string>();

    problemStatementChange = output<string>();

    analysisResult = signal<ChecklistAnalysisResponse | undefined>(undefined);
    isLoading = signal<boolean>(false);
    isApplyingAction = signal<boolean>(false);
    actionLoadingKey = signal<string | undefined>(undefined);

    // Expanded state for competency evidence
    expandedCompetencies = signal<Set<number>>(new Set());

    sections = {
        competencies: true,
        difficulty: true,
        quality: true,
    };

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
    faPlus = faPlus;
    faMinus = faMinus;

    analyze() {
        const ex = this.exercise();
        if (!ex?.id) {
            return;
        }

        this.isLoading.set(true);
        const request = {
            problemStatementMarkdown: this.problemStatement(),
            declaredDifficulty: ex.difficulty,
            language: ex.programmingLanguage,
        };

        this.hyperionApiService.analyzeChecklist(ex.id, request).subscribe({
            next: (res: ChecklistAnalysisResponse) => {
                this.analysisResult.set(res);
                this.isLoading.set(false);
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

    getSeverityBadgeClass(severity: string | undefined): string {
        switch (severity?.toUpperCase()) {
            case 'HIGH':
                return 'bg-danger';
            case 'MEDIUM':
                return 'bg-warning text-dark';
            case 'LOW':
            default:
                return 'bg-info';
        }
    }

    getRelevanceLabel(confidence: number | undefined): string {
        if (confidence === undefined) return 'Low';
        if (confidence >= 0.7) return 'High';
        if (confidence >= 0.4) return 'Medium';
        return 'Low';
    }

    getRelevanceBadgeClass(confidence: number | undefined): string {
        if (confidence === undefined) return 'relevance-low';
        if (confidence >= 0.7) return 'relevance-high';
        if (confidence >= 0.4) return 'relevance-medium';
        return 'relevance-low';
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
        CLARITY: '#3b82f6',
        COHERENCE: '#8b5cf6',
        COMPLETENESS: '#10b981',
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

    get qualityRadarPoints(): { label: string; value: number; color: string; axisX: number; axisY: number; dataX: number; dataY: number; labelX: number; labelY: number }[] {
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
    }

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

    get qualityPolygonPoints(): string {
        return this.qualityRadarPoints.map((p) => `${p.dataX},${p.dataY}`).join(' ');
    }

    // ===== AI Action Methods =====

    private applyAction(request: ChecklistActionRequest, loadingKey: string) {
        const exerciseId = this.exercise()?.id;
        if (!exerciseId || this.isApplyingAction()) return;

        this.isApplyingAction.set(true);
        this.actionLoadingKey.set(loadingKey);

        this.hyperionApiService.applyChecklistAction(exerciseId, request).subscribe({
            next: (res) => {
                if (res.applied) {
                    this.problemStatementChange.emit(res.updatedProblemStatement ?? '');
                    this.alertService.success('artemisApp.programmingExercise.instructorChecklist.actions.success');
                    // Use the embedded re-analysis from the same response
                    if (res.updatedAnalysis) {
                        this.analysisResult.set(res.updatedAnalysis);
                    }
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

    fixQualityIssue(issue: QualityIssue, index: number) {
        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixQualityIssue,
                problemStatementMarkdown: this.problemStatement(),
                context: {
                    issueDescription: issue.description || '',
                    suggestedFix: issue.suggestedFix || '',
                    category: issue.category || '',
                },
            },
            `fix-issue-${index}`,
        );
    }

    fixAllQualityIssues() {
        const issues = this.analysisResult()?.qualityIssues || [];
        const allIssues = issues.map((i, idx) => `${idx + 1}. [${i.category}/${i.severity}] ${i.description} (Fix: ${i.suggestedFix || 'N/A'})`).join('\n');

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.FixAllQualityIssues,
                problemStatementMarkdown: this.problemStatement(),
                context: { allIssues },
            },
            'fix-all',
        );
    }

    adaptDifficulty(targetDifficulty: string) {
        const current = this.exercise()?.difficulty || 'unknown';
        const reasoning = this.analysisResult()?.difficultyAssessment?.reasoning || '';

        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.AdaptDifficulty,
                problemStatementMarkdown: this.problemStatement(),
                context: {
                    targetDifficulty,
                    currentDifficulty: current,
                    reasoning,
                },
            },
            `adapt-${targetDifficulty}`,
        );
    }

    emphasizeCompetency(competencyTitle: string, taxonomyLevel: string) {
        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.EmphasizeCompetency,
                problemStatementMarkdown: this.problemStatement(),
                context: { competencyTitle, taxonomyLevel },
            },
            `emphasize-${competencyTitle}`,
        );
    }

    deemphasizeCompetency(competencyTitle: string) {
        this.applyAction(
            {
                actionType: ChecklistActionRequest.ActionTypeEnum.DeemphasizeCompetency,
                problemStatementMarkdown: this.problemStatement(),
                context: { competencyTitle },
            },
            `deemphasize-${competencyTitle}`,
        );
    }

    isActionLoading(key: string): boolean {
        return this.actionLoadingKey() === key;
    }
}
