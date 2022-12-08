import { Color, ScaleType } from '@swimlane/ngx-charts';
import { NgxChartsMultiSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { FeedbackNode } from 'app/exercises/shared/feedback/node/feedback-node';
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
    const absMaxCredits = Math.abs(maxCredits);
    return Math.sign(credits) * Math.min(absCredits, absMaxCredits);
};

const calculatePercentage = (node: FeedbackNode, maxPoints: number) => {
    const appliedCredits = capCredits(node.credits ?? 0, node.maxCredits);
    return roundToDecimals((appliedCredits / maxPoints) * 100, 2);
};

export const nodesToChartData = (feedbackNodes: FeedbackNode[], exercise: Exercise): ChartData => {
    const maxPoints = exercise.maxPoints! + (exercise.bonusPoints ?? 0);
    const xScaleMax = Math.max(100, maxPoints);
    const results: NgxChartsMultiSeriesDataEntry[] = [
        {
            name: 'scores',
            series: feedbackNodes.map((node: FeedbackNode) => ({
                name: node.name,
                value: calculatePercentage(node, exercise.maxPoints!),
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
