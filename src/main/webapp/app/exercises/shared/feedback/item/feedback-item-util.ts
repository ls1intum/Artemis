import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { FeedbackItemNode } from 'app/exercises/shared/feedback/item/feedback-item-node';
import { Exercise } from 'app/entities/exercise.model';
import { round } from 'app/shared/util/utils';

export interface ChartData {
    xScaleMax: number;
    scheme: Color;
    results: NgxChartsMultiSeriesDataEntry[];
}

const roundToDecimals = (i: number, n: number) => {
    const f = 10 ** n;
    return round(i, f);
};

const capCredits = (credits: number, maxCredits?: number): number => {
    // no maxCredits or credits and maxCredits do not have the same sign;
    if (!maxCredits || credits * maxCredits < 0) {
        return credits;
    }

    const absCredits = Math.abs(credits);
    const absMaxCredits = Math.abs(credits);
    return Math.sign(credits) * Math.max(absCredits, absMaxCredits);
};

export const calculateAppliedCredits = (node: FeedbackItemNode) => {
    return roundToDecimals(capCredits(node.credits ?? 0, node.maxCredits), 2);
};

export const nodesToChartData = (feedbackNodes: FeedbackItemNode[], exercise: Exercise): ChartData => {
    const maxPoints = (exercise.maxPoints ?? 0) + (exercise.bonusPoints ?? 0);
    const score = feedbackNodes.reduce((acc, node) => acc + (node.credits ?? 0), 0);
    const xScaleMax = Math.max(100, score);
    const results: NgxChartsMultiSeriesDataEntry[] = [
        {
            name: 'scores',
            series: feedbackNodes.map((node: FeedbackItemNode) => ({
                name: node.name,
                value: roundToDecimals(capCredits((node.credits ?? 0) * maxPoints, node.maxCredits), 2),
            })),
        },
    ];
    const scheme: Color = {
        name: 'Feedback Detail',
        selectable: true,
        group: ScaleType.Ordinal,
        domain: feedbackNodes.map((node) => node.color ?? 'var(--white)'),
    };

    return {
        xScaleMax,
        results,
        scheme,
    };
};
