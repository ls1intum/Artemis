import { Component, inject, input, output, signal } from '@angular/core';
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
    faExclamationTriangle,
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
    lastActionSummary = signal<string | undefined>(undefined);

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
    faExclamationTriangle = faExclamationTriangle;
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
            case 'ERROR':
                return 'text-danger';
            case 'WARNING':
                return 'text-warning';
            case 'INFO':
            default:
                return 'text-info';
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

    // Competency radar chart constants
    private readonly COMPETENCY_RADAR_RADIUS = 100;
    private readonly COMPETENCY_LABEL_OFFSET = 30;

    // Extract short label from competency title
    private getShortLabel(title: string | undefined): string {
        if (!title) return '';
        // Remove common prefixes like "Analyze and implement", "Design and develop", etc.
        const cleaned = title
            .replace(/^(Analyze and implement|Design and develop|Implement and test|Create and manage|Apply|Understand|Analyze|Implement|Design|Develop|Manage|Use)\s+/i, '')
            .replace(/\s+(in\s+\w+|using\s+\w+)$/i, ''); // Remove trailing "in Java", "using Python", etc.

        // Truncate if too long (max ~35 chars)
        if (cleaned.length > 35) {
            return cleaned.substring(0, 33) + '...';
        }
        return cleaned;
    }

    // Calculate competency radar point coordinates
    get competencyRadarPoints(): {
        label: string;
        fullLabel: string;
        value: number;
        axisX: number;
        axisY: number;
        dataX: number;
        dataY: number;
        labelX: number;
        labelY: number;
        textAnchor: string;
    }[] {
        const competencies = this.analysisResult()?.inferredCompetencies || [];
        if (competencies.length === 0) return [];

        const angleStep = (2 * Math.PI) / competencies.length;
        const startAngle = -Math.PI / 2; // Start from top

        return competencies.map((comp, i) => {
            const angle = startAngle + i * angleStep;
            const cos = Math.cos(angle);
            const sin = Math.sin(angle);

            // Axis endpoint
            const axisX = cos * this.COMPETENCY_RADAR_RADIUS;
            const axisY = sin * this.COMPETENCY_RADAR_RADIUS;

            // Data point (scaled by confidence)
            const confidence = comp.confidence || 0;
            const dataRadius = Math.max(confidence * this.COMPETENCY_RADAR_RADIUS, 8);
            const dataX = cos * dataRadius;
            const dataY = sin * dataRadius;

            // Label position (outside the chart)
            const labelRadius = this.COMPETENCY_RADAR_RADIUS + this.COMPETENCY_LABEL_OFFSET;
            const labelX = cos * labelRadius;
            const labelY = sin * labelRadius;

            // Text anchor based on position
            let textAnchor = 'middle';
            if (cos < -0.1) textAnchor = 'end';
            else if (cos > 0.1) textAnchor = 'start';

            return {
                label: this.getShortLabel(comp.competencyTitle),
                fullLabel: comp.competencyTitle || '',
                value: confidence,
                axisX,
                axisY,
                dataX,
                dataY,
                labelX,
                labelY,
                textAnchor,
            };
        });
    }

    // Generate polygon points string for competency grid
    getCompetencyGridPoints(scale: number): string {
        const competencies = this.analysisResult()?.inferredCompetencies || [];
        if (competencies.length === 0) return '';

        const angleStep = (2 * Math.PI) / competencies.length;
        const startAngle = -Math.PI / 2;

        const points = competencies.map((_, i) => {
            const angle = startAngle + i * angleStep;
            const x = Math.cos(angle) * this.COMPETENCY_RADAR_RADIUS * scale;
            const y = Math.sin(angle) * this.COMPETENCY_RADAR_RADIUS * scale;
            return `${x},${y}`;
        });

        return points.join(' ');
    }

    // Generate polygon points string for competency data
    get competencyPolygonPoints(): string {
        return this.competencyRadarPoints.map((p) => `${p.dataX},${p.dataY}`).join(' ');
    }

    // ===== AI Action Methods =====

    private applyAction(request: ChecklistActionRequest, loadingKey: string) {
        const exerciseId = this.exercise()?.id;
        if (!exerciseId || this.isApplyingAction()) return;

        this.isApplyingAction.set(true);
        this.actionLoadingKey.set(loadingKey);
        this.lastActionSummary.set(undefined);

        this.hyperionApiService.applyChecklistAction(exerciseId, request).subscribe({
            next: (res) => {
                if (res.applied) {
                    this.problemStatementChange.emit(res.updatedProblemStatement);
                    this.lastActionSummary.set(res.summary);
                    this.alertService.success('artemisApp.programmingExercise.instructorChecklist.actions.success');
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
