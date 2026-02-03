import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { TranslateModule } from '@ngx-translate/core';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
    faArrowDown,
    faArrowUp,
    faCheckCircle,
    faChevronDown,
    faChevronRight,
    faEquals,
    faExclamationTriangle,
    faInfoCircle,
    faMagic,
    faQuestion,
    faSpinner,
} from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { HyperionProblemStatementApiService } from 'app/openapi/api/hyperionProblemStatementApi.service';
import { ChecklistAnalysisResponse } from 'app/openapi/model/checklistAnalysisResponse';
import { BloomRadar } from 'app/openapi/model/bloomRadar';
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

    @Input() exercise: ProgrammingExercise;
    @Input() problemStatement: string;

    analysisResult = signal<ChecklistAnalysisResponse | undefined>(undefined);
    isLoading = signal<boolean>(false);

    // Expanded state for competency evidence
    expandedCompetencies = signal<Set<number>>(new Set());

    sections = {
        competencies: true,
        difficulty: true,
        quality: true,
    };

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

    analyze() {
        if (!this.exercise || !this.exercise.id) {
            return;
        }

        this.isLoading.set(true);
        const request = {
            problemStatementMarkdown: this.problemStatement,
            declaredDifficulty: this.exercise.difficulty,
            language: this.exercise.programmingLanguage,
        };

        this.hyperionApiService.analyzeChecklist(this.exercise.id!, request).subscribe({
            next: (res: ChecklistAnalysisResponse) => {
                this.analysisResult.set(res);
                this.isLoading.set(false);
            },
            error: (err: unknown) => {
                this.alertService.error('artemisApp.programmingExercise.checklist.analysisError');
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

    // Bloom radar data for chart
    get bloomRadarData(): { label: string; value: number }[] {
        const radar: BloomRadar | undefined = this.analysisResult()?.bloomRadar;
        if (!radar) return [];
        return [
            { label: 'Remember', value: radar.REMEMBER || 0 },
            { label: 'Understand', value: radar.UNDERSTAND || 0 },
            { label: 'Apply', value: radar.APPLY || 0 },
            { label: 'Analyze', value: radar.ANALYZE || 0 },
            { label: 'Evaluate', value: radar.EVALUATE || 0 },
            { label: 'Create', value: radar.CREATE || 0 },
        ];
    }

    // Radar chart constants
    private readonly RADAR_RADIUS = 100;
    private readonly LABEL_OFFSET = 65;

    // Calculate radar point coordinates
    get radarPoints(): {
        label: string;
        value: number;
        axisX: number;
        axisY: number;
        dataX: number;
        dataY: number;
        labelX: number;
        labelY: number;
        textAnchor: string;
    }[] {
        const data = this.bloomRadarData;
        if (data.length === 0) return [];

        const angleStep = (2 * Math.PI) / data.length;
        const startAngle = -Math.PI / 2; // Start from top

        return data.map((item, i) => {
            const angle = startAngle + i * angleStep;
            const cos = Math.cos(angle);
            const sin = Math.sin(angle);

            // Axis endpoint
            const axisX = cos * this.RADAR_RADIUS;
            const axisY = sin * this.RADAR_RADIUS;

            // Data point (scaled by value)
            const dataRadius = Math.max(item.value * this.RADAR_RADIUS, 5);
            const dataX = cos * dataRadius;
            const dataY = sin * dataRadius;

            // Label position (outside the chart)
            const labelRadius = this.RADAR_RADIUS + this.LABEL_OFFSET;
            const labelX = cos * labelRadius;
            const labelY = sin * labelRadius;

            // Text anchor based on position
            let textAnchor = 'middle';
            if (cos < -0.1) textAnchor = 'end';
            else if (cos > 0.1) textAnchor = 'start';

            return {
                label: item.label,
                value: item.value,
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

    // Generate polygon points string for grid
    getRadarGridPoints(scale: number): string {
        const data = this.bloomRadarData;
        if (data.length === 0) return '';

        const angleStep = (2 * Math.PI) / data.length;
        const startAngle = -Math.PI / 2;

        const points = data.map((_, i) => {
            const angle = startAngle + i * angleStep;
            const x = Math.cos(angle) * this.RADAR_RADIUS * scale;
            const y = Math.sin(angle) * this.RADAR_RADIUS * scale;
            return `${x},${y}`;
        });

        return points.join(' ');
    }

    // Generate polygon points string for data
    get radarPolygonPoints(): string {
        return this.radarPoints.map((p) => `${p.dataX},${p.dataY}`).join(' ');
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
}
