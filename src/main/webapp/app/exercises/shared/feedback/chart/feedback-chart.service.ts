import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
import { Exercise } from 'app/entities/exercise.model';
import { round } from 'app/shared/util/utils';
import { Injectable } from '@angular/core';
import { ChartData } from 'app/exercises/shared/feedback/chart/feedback-chart-data';

@Injectable({ providedIn: 'root' })
export class FeedbackChartService {
    create = (feedbackNodes: FeedbackNode[], exercise: Exercise): ChartData => {
        const maxPoints = exercise.maxPoints! + (exercise.bonusPoints ?? 0);
        const xScaleMax = Math.max(100, maxPoints);
        const results: NgxChartsMultiSeriesDataEntry[] = [
            {
                name: 'scores',
                series: feedbackNodes.map((node: FeedbackNode) => ({
                    name: node.name,
                    value: this.calculatePercentage(node, exercise.maxPoints!),
                })),
            },
        ];
        const scheme: Color = {
            name: 'Feedback Detail',
            selectable: true,
            group: ScaleType.Ordinal,
            domain: feedbackNodes.map((node) => `var(--bs-${node.color})` ?? 'var(--white)'),
        };

        return {
            xScaleMax,
            results,
            scheme,
        };
    };

    private roundToDecimals = (i: number, n: number) => {
        const f = 10 ** n;
        return round(i, f);
    };

    private capCredits = (credits: number, maxCredits?: number): number => {
        // no maxCredits or credits and maxCredits do not have the same sign;
        if (!maxCredits || credits * maxCredits < 0) {
            return credits;
        }

        const absCredits = Math.abs(credits);
        const absMaxCredits = Math.abs(maxCredits);
        return Math.sign(credits) * Math.min(absCredits, absMaxCredits);
    };

    private calculatePercentage = (node: FeedbackNode, maxPoints: number) => {
        const appliedCredits = this.capCredits(node.credits ?? 0, node.maxCredits);
        return this.roundToDecimals((appliedCredits / maxPoints) * 100, 2);
    };
}
